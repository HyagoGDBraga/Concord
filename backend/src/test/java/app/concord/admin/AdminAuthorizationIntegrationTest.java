package app.concord.admin;

import app.concord.support.AbstractIntegrationTest;
import app.concord.support.TestApiClient;
import app.concord.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Autorização do painel administrativo e isolamento entre usuários.
 *
 * <p>Estes testes cobrem o critério de conclusão mais importante da fase: um
 * usuário comum não deve nem descobrir que o painel existe.
 */
class AdminAuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("usuário comum recebe 404 em todas as rotas administrativas")
    void usuarioComumRecebe404() {
        String username = TestData.uniqueUsername();
        registerVerifyAndLogin(username);

        String[] rotas = {"/admin/users", "/admin/audit", "/admin/settings"};
        for (String rota : rotas) {
            TestApiClient.Response response = api.get(rota);
            assertThat(response.status())
                    .as("rota %s deve responder 404 para usuário comum", rota)
                    .isEqualTo(404);
            // A resposta não pode diferenciar "existe mas é proibido" de
            // "não existe": ambos precisam ser NOT_FOUND.
            assertThat(response.errorCode()).isEqualTo("NOT_FOUND");
        }
    }

    @Test
    @DisplayName("requisição anônima em rota administrativa recebe 401")
    void anonimoRecebe401() {
        assertThat(api.get("/admin/users").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("um usuário não consegue revogar a sessão de outro")
    void naoRevogaSessaoAlheia() {
        // Vítima faz login em um cliente próprio.
        String vitima = TestData.uniqueUsername();
        registerVerifyAndLogin(vitima);
        String sessaoDaVitima = api.sessionCookie();
        assertThat(sessaoDaVitima).isNotNull();

        TestApiClient.Response sessoes = api.get("/users/me/sessions");
        String idDaSessaoDaVitima = sessoes.json().get(0).get("id").asText();

        // Atacante, em outro cliente, tenta apagar a sessão da vítima usando o
        // id — que ele teria, por hipótese, obtido de alguma forma.
        TestApiClient atacante = new TestApiClient(port, objectMapper);
        String outro = TestData.uniqueUsername();
        registerVerifyAndLogin(atacante, outro);

        TestApiClient.Response tentativa =
                atacante.delete("/users/me/sessions/" + idDaSessaoDaVitima, null);

        assertThat(tentativa.status()).isEqualTo(404);
        assertThat(tentativa.errorCode()).isEqualTo("SESSION_NOT_FOUND");

        // A sessão da vítima continua valendo.
        assertThat(api.get("/auth/me").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("o rate limit de login dispara e responde 429")
    void rateLimitDeLogin() {
        String username = TestData.uniqueUsername();
        registerVerifyAndLogin(username);
        api.post("/auth/logout", null);

        int status = 0;
        // A regra é 5 por minuto por IP; a sexta tentativa deve ser barrada
        // antes mesmo de chegar à verificação de senha.
        for (int i = 0; i < 8; i++) {
            status = api.post("/auth/login",
                    TestData.loginPayload(username, "senha-incorreta-longa")).status();
            if (status == 429) {
                break;
            }
        }
        assertThat(status).isEqualTo(429);
    }

    private void registerVerifyAndLogin(String username) {
        registerVerifyAndLogin(api, username);
    }

    private void registerVerifyAndLogin(TestApiClient client, String username) {
        String email = TestData.emailFor(username);
        client.post("/auth/register", TestData.registerPayload(username, email));
        client.post("/auth/verify-email",
                Map.of("token", tokenFromEmail(lastEmailTo(email))));
        client.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));
    }
}
