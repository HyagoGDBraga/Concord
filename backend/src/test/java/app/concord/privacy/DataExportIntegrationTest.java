package app.concord.privacy;

import app.concord.support.AbstractIntegrationTest;
import app.concord.support.TestApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exportacao de dados do titular.
 *
 * <p>O teste mais importante nao e o que o arquivo CONTEM, e o que ele NAO
 * contem: hash de senha e o e-mail do interlocutor.
 */
class DataExportIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("a exportacao traz perfil, contatos e conversas do titular")
    void exportaOsDadosDoTitular() {
        TestUser ana = newUser();
        TestUser bruno = newUser();
        String conversaId = conversar(ana, bruno, "mensagem que deve aparecer");

        JsonNode dados = ana.client().get("/users/me/export").json();

        assertThat(dados.get("formatoVersao").asInt()).isEqualTo(1);
        assertThat(dados.get("perfil").get("nomeDeUsuario").asText())
                .isEqualTo(ana.username());
        assertThat(dados.get("contatos")).hasSize(1);
        assertThat(dados.get("conversas")).hasSize(1);

        JsonNode conversa = dados.get("conversas").get(0);
        assertThat(conversa.get("id").asText()).isEqualTo(conversaId);
        assertThat(conversa.get("mensagens").get(0).get("texto").asText())
                .isEqualTo("mensagem que deve aparecer");
        assertThat(conversa.get("mensagens").get(0).get("euEnviei").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a exportacao nao contem hash de senha nem o e-mail do interlocutor")
    void naoVazaCredencialNemDadoDeTerceiro() {
        TestUser ana = newUser();
        TestUser bruno = newUser();
        conversar(ana, bruno, "oi");

        TestApiClient.Response resposta = ana.client().get("/users/me/export");
        String corpo = resposta.body();

        // Credencial nunca e devolvida: e segredo de autenticacao, nao dado do
        // titular.
        assertThat(corpo).doesNotContain("passwordHash").doesNotContain("$argon2");

        // O e-mail do outro nunca apareceu na interface, entao nao aparece aqui.
        assertThat(corpo).doesNotContain(bruno.username() + "@");

        // O username dele, sim: e o que a tela de contatos ja mostra.
        assertThat(corpo).contains(bruno.username());
    }

    @Test
    @DisplayName("a exportacao e limitada a um pedido por dia")
    void limitadaAUmPedidoPorDia() {
        TestUser ana = newUser();

        assertThat(ana.client().get("/users/me/export").status()).isEqualTo(200);

        TestApiClient.Response segunda = ana.client().get("/users/me/export");
        assertThat(segunda.status()).isEqualTo(429);
        assertThat(segunda.errorCode()).isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("o cadastro registra o aceite dos documentos com a versao vigente")
    void consentimentoRegistradoNoCadastro() {
        TestUser ana = newUser();

        JsonNode status = ana.client().get("/legal/consents").json();
        assertThat(status.get("termsAccepted").asBoolean()).isTrue();
        assertThat(status.get("privacyAccepted").asBoolean()).isTrue();
        assertThat(status.get("termsVersion").asText()).isEqualTo("2026-01");

        JsonNode historico = ana.client().get("/legal/consents/history").json().get("records");
        assertThat(historico).hasSize(2);
        assertThat(historico.get(0).get("acceptedAt")).isNotNull();
    }

    @Test
    @DisplayName("aceitar uma versao que nao e a vigente e recusado")
    void recusaVersaoDesatualizada() {
        TestUser ana = newUser();

        TestApiClient.Response resposta = ana.client().post("/legal/consents",
                Map.of("document", "TERMS_OF_USE", "version", "2020-01"));

        assertThat(resposta.status()).isEqualTo(400);
    }

    private String conversar(TestUser ana, TestUser bruno, String texto) {
        ana.client().post("/contacts/requests", Map.of("username", bruno.username()));
        String pedidoId = bruno.client().get("/contacts").json()
                .get("incoming").get(0).get("id").asText();
        bruno.client().post("/contacts/requests/" + pedidoId + "/accept", null);

        String conversaId = ana.client()
                .post("/conversations", Map.of("userId", bruno.id()))
                .json().get("id").asText();

        ana.client().post("/conversations/" + conversaId + "/messages",
                Map.of("body", texto, "clientMessageId", UUID.randomUUID()));
        return conversaId;
    }
}
