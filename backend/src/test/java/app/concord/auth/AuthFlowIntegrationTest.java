package app.concord.auth;

import app.concord.support.AbstractIntegrationTest;
import app.concord.support.TestApiClient;
import app.concord.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Fluxo completo de cadastro, verificação, login e logout. */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("cadastro exige verificação de e-mail antes do primeiro login")
    void fluxoCompleto() {
        String username = TestData.uniqueUsername();
        String email = TestData.emailFor(username);

        // 1. Cadastro devolve 202 e cria conta pendente.
        TestApiClient.Response register =
                api.post("/auth/register", TestData.registerPayload(username, email));
        assertThat(register.status()).isEqualTo(202);

        // 2. Login antes da verificação é recusado com código específico.
        TestApiClient.Response earlyLogin =
                api.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));
        assertThat(earlyLogin.status()).isEqualTo(403);
        assertThat(earlyLogin.errorCode()).isEqualTo("EMAIL_NOT_VERIFIED");

        // 3. Verificação usando o token lido do e-mail.
        String token = tokenFromEmail(lastEmailTo(email));
        assertThat(api.post("/auth/verify-email", Map.of("token", token)).status())
                .isEqualTo(204);

        // 4. Login agora funciona.
        TestApiClient.Response login =
                api.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.json().get("username").asText()).isEqualTo(username);
        assertThat(login.json().get("status").asText()).isEqualTo("ACTIVE");

        // 5. /auth/me responde com a sessão ativa.
        TestApiClient.Response me = api.get("/auth/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("email").asText()).isEqualTo(email);

        // 6. Logout encerra a sessão.
        assertThat(api.post("/auth/logout", null).status()).isEqualTo(204);
        assertThat(api.get("/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("o id de sessão muda no login (proteção contra session fixation)")
    void trocaIdDeSessaoNoLogin() {
        String username = TestData.uniqueUsername();
        String email = TestData.emailFor(username);
        registerAndVerify(username, email);

        // Cria uma sessão anônima antes do login.
        api.get("/auth/me");
        String before = api.sessionCookie();

        api.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));
        String after = api.sessionCookie();

        assertThat(after).isNotNull();
        if (before != null) {
            assertThat(after).isNotEqualTo(before);
        }
    }

    @Test
    @DisplayName("token de verificação é de uso único")
    void tokenDeUsoUnico() {
        String username = TestData.uniqueUsername();
        String email = TestData.emailFor(username);
        api.post("/auth/register", TestData.registerPayload(username, email));
        String token = tokenFromEmail(lastEmailTo(email));

        assertThat(api.post("/auth/verify-email", Map.of("token", token)).status()).isEqualTo(204);

        TestApiClient.Response second = api.post("/auth/verify-email", Map.of("token", token));
        assertThat(second.status()).isEqualTo(400);
        assertThat(second.errorCode()).isEqualTo("TOKEN_INVALID");
    }

    @Test
    @DisplayName("cadastro com e-mail já usado responde 202 e não revela a colisão")
    void naoRevelaEmailExistente() {
        String first = TestData.uniqueUsername();
        String email = TestData.emailFor(first);
        registerAndVerify(first, email);

        String second = TestData.uniqueUsername();
        TestApiClient.Response response =
                api.post("/auth/register", TestData.registerPayload(second, email));

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body()).doesNotContain("existe").doesNotContain("cadastrado");

        // A conta nova não foi criada: o username continua disponível.
        TestApiClient.Response availability =
                api.get("/auth/username-available?username=" + second);
        assertThat(availability.json().get("available").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("username já usado responde 409, porque username é público")
    void revelaUsernameExistente() {
        String username = TestData.uniqueUsername();
        registerAndVerify(username, TestData.emailFor(username));

        TestApiClient.Response response = api.post("/auth/register",
                TestData.registerPayload(username, TestData.emailFor("outro" + username)));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("USERNAME_TAKEN");
    }

    @Test
    @DisplayName("senha errada não revela se a conta existe")
    void senhaErradaRespostaGenerica() {
        String username = TestData.uniqueUsername();
        registerAndVerify(username, TestData.emailFor(username));

        TestApiClient.Response existente =
                api.post("/auth/login", TestData.loginPayload(username, "senha-errada-mas-longa"));
        api.clearCookies();
        TestApiClient.Response inexistente = api.post("/auth/login",
                TestData.loginPayload("nao_existe_mesmo", "senha-errada-mas-longa"));

        assertThat(existente.status()).isEqualTo(401);
        assertThat(inexistente.status()).isEqualTo(401);
        assertThat(existente.errorCode()).isEqualTo(inexistente.errorCode());
        assertThat(existente.json().get("message").asText())
                .isEqualTo(inexistente.json().get("message").asText());
    }

    @Test
    @DisplayName("reset de senha revoga todas as sessões, inclusive a atual")
    void resetRevogaTodasAsSessoes() {
        String username = TestData.uniqueUsername();
        String email = TestData.emailFor(username);
        registerAndVerify(username, email);
        api.post("/auth/login", TestData.loginPayload(username, TestData.VALID_PASSWORD));
        assertThat(api.get("/auth/me").status()).isEqualTo(200);

        api.post("/auth/password/forgot", Map.of("email", email));
        String token = tokenFromEmail(lastEmailTo(email));

        String novaSenha = "trilha-larga-64-porta";
        assertThat(api.post("/auth/password/reset",
                Map.of("token", token, "newPassword", novaSenha)).status()).isEqualTo(204);

        // A sessão anterior deixou de valer.
        assertThat(api.get("/auth/me").status()).isEqualTo(401);

        // A nova senha funciona; a antiga, não.
        assertThat(api.post("/auth/login",
                TestData.loginPayload(username, TestData.VALID_PASSWORD)).status()).isEqualTo(401);
        assertThat(api.post("/auth/login",
                TestData.loginPayload(username, novaSenha)).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("requisição sem token CSRF é rejeitada")
    void exigeCsrf() {
        String username = TestData.uniqueUsername();
        registerAndVerify(username, TestData.emailFor(username));

        // Cliente novo, sem nunca ter feito um GET: não possui o cookie XSRF-TOKEN.
        TestApiClient semCsrf = new TestApiClient(port, objectMapper);
        TestApiClient.Response response = semCsrf.post("/auth/login",
                TestData.loginPayload(username, TestData.VALID_PASSWORD));

        assertThat(response.status()).isEqualTo(403);
    }

    /** Atalho: cadastra e confirma o e-mail, deixando a conta ACTIVE. */
    protected void registerAndVerify(String username, String email) {
        api.post("/auth/register", TestData.registerPayload(username, email));
        api.post("/auth/verify-email", Map.of("token", tokenFromEmail(lastEmailTo(email))));
    }
}
