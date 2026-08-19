package app.concord.user;

import app.concord.support.AbstractIntegrationTest;
import app.concord.support.TestApiClient;
import app.concord.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Ciclo de vida da conta: troca de senha, sessões e exclusão por anonimização. */
class AccountLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("troca de senha mantém a sessão atual e encerra as demais")
    void trocaDeSenhaMantemSessaoAtual() {
        String username = TestData.uniqueUsername();
        registerVerify(username);

        // Duas sessões: dois clientes distintos, como dois dispositivos.
        TestApiClient dispositivoA = api;
        TestApiClient dispositivoB = new TestApiClient(port, objectMapper);
        dispositivoA.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));
        dispositivoB.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));

        assertThat(dispositivoA.get("/users/me/sessions").json()).hasSize(2);

        String novaSenha = "mesa-comprida-19-fresta";
        assertThat(dispositivoA.post("/users/me/password", Map.of(
                "currentPassword", TestData.VALID_PASSWORD,
                "newPassword", novaSenha)).status()).isEqualTo(204);

        // Quem trocou continua logado; o outro dispositivo caiu.
        assertThat(dispositivoA.get("/auth/me").status()).isEqualTo(200);
        assertThat(dispositivoB.get("/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("senha atual incorreta impede a troca")
    void exigeSenhaAtualCorreta() {
        String username = TestData.uniqueUsername();
        registerVerify(username);
        api.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));

        TestApiClient.Response response = api.post("/users/me/password", Map.of(
                "currentPassword", "isso-nao-e-a-senha",
                "newPassword", "outra-senha-bem-longa"));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.errorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("exclusão anonimiza a conta e preserva as invariantes do banco")
    void exclusaoAnonimiza() {
        String username = TestData.uniqueUsername();
        String email = TestData.emailFor(username);
        registerVerify(username);
        TestApiClient.Response login =
                api.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));
        UUID userId = UUID.fromString(login.json().get("id").asText());

        assertThat(api.delete("/users/me", Map.of(
                "currentPassword", TestData.VALID_PASSWORD,
                "confirmation", "EXCLUIR")).status()).isEqualTo(204);

        // A sessão morreu junto.
        assertThat(api.get("/auth/me").status()).isEqualTo(401);

        // A linha continua existindo — é o que mantém as FKs de mensagens da
        // Fase 3 íntegras —, mas sem nenhum dado pessoal.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT username, email, display_name, bio, avatar_url, status, anonymized_at "
                        + "FROM users WHERE id = ?", userId);

        assertThat(row.get("status")).isEqualTo("DELETED");
        assertThat(row.get("email")).isNull();
        assertThat(row.get("anonymized_at")).isNotNull();
        assertThat(row.get("bio")).isNull();
        assertThat(row.get("avatar_url")).isNull();
        assertThat((String) row.get("username")).startsWith("removido_").doesNotContain(username);
        assertThat(row.get("display_name")).isEqualTo("Usuário removido");

        // Tokens de ação da conta foram apagados.
        Integer tokens = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_action_tokens WHERE user_id = ?", Integer.class, userId);
        assertThat(tokens).isZero();

        // A auditoria sobrevive, com o rótulo já pseudonimizado.
        Integer eventos = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE actor_user_id = ? OR target_user_id = ?",
                Integer.class, userId, userId);
        assertThat(eventos).isPositive();

        Integer rotulosOriginais = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE actor_user_id = ? AND actor_label = ?",
                Integer.class, userId, username);
        assertThat(rotulosOriginais).isZero();

        // O e-mail fica livre para um novo cadastro.
        String novoUsername = TestData.uniqueUsername();
        assertThat(api.post("/auth/register",
                TestData.registerPayload(novoUsername, email)).status()).isEqualTo(202);
    }

    @Test
    @DisplayName("exclusão exige a palavra de confirmação")
    void exclusaoExigeConfirmacao() {
        String username = TestData.uniqueUsername();
        registerVerify(username);
        api.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));

        TestApiClient.Response response = api.delete("/users/me", Map.of(
                "currentPassword", TestData.VALID_PASSWORD,
                "confirmation", "excluir"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(api.get("/auth/me").status()).isEqualTo(200);
    }

    private void registerVerify(String username) {
        String email = TestData.emailFor(username);
        api.post("/auth/register", TestData.registerPayload(username, email));
        api.post("/auth/verify-email", Map.of("token", tokenFromEmail(lastEmailTo(email))));
    }
}
