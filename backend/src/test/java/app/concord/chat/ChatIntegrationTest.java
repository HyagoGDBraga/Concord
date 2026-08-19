package app.concord.chat;

import app.concord.support.AbstractIntegrationTest;
import app.concord.support.TestApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contatos, conversas e mensagens. */
class ChatIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("nao se abre conversa com quem nao e contato aceito")
    void exigeContatoAceito() {
        TestUser ana = newUser();
        TestUser bruno = newUser();

        TestApiClient.Response semContato =
                ana.client().post("/conversations", Map.of("userId", bruno.id()));
        assertThat(semContato.status()).isEqualTo(403);
        assertThat(semContato.errorCode()).isEqualTo("NOT_CONTACTS");

        // Pedido enviado, ainda nao aceito: continua barrado.
        ana.client().post("/contacts/requests", Map.of("username", bruno.username()));
        assertThat(ana.client().post("/conversations", Map.of("userId", bruno.id())).status())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("fluxo completo: pedido, aceite, conversa e mensagens")
    void fluxoCompleto() {
        TestUser ana = newUser();
        TestUser bruno = newUser();

        // 1. Ana pede contato pelo username exato.
        assertThat(ana.client()
                .post("/contacts/requests", Map.of("username", bruno.username()))
                .status()).isEqualTo(201);

        // 2. Bruno ve o pedido e aceita.
        JsonNode entrada = bruno.client().get("/contacts").json().get("incoming");
        assertThat(entrada).hasSize(1);
        String pedidoId = entrada.get(0).get("id").asText();
        assertThat(bruno.client().post("/contacts/requests/" + pedidoId + "/accept", null)
                .status()).isEqualTo(204);

        // 3. Ana abre a conversa.
        TestApiClient.Response abertura =
                ana.client().post("/conversations", Map.of("userId", bruno.id()));
        assertThat(abertura.status()).isEqualTo(200);
        String conversaId = abertura.json().get("id").asText();

        // Abrir de novo devolve a mesma conversa.
        assertThat(bruno.client().post("/conversations", Map.of("userId", ana.id()))
                .json().get("id").asText()).isEqualTo(conversaId);

        // 4. Ana escreve, Bruno responde.
        assertThat(enviar(ana, conversaId, "oi, tudo bem?").status()).isEqualTo(201);
        assertThat(enviar(bruno, conversaId, "tudo, e voce?").status()).isEqualTo(201);

        // 5. Historico chega em ordem cronologica para os dois.
        JsonNode itens = ana.client()
                .get("/conversations/" + conversaId + "/messages").json().get("items");
        assertThat(itens).hasSize(2);
        assertThat(itens.get(0).get("body").asText()).isEqualTo("oi, tudo bem?");
        assertThat(itens.get(1).get("body").asText()).isEqualTo("tudo, e voce?");

        // 6. A lista de conversas mostra previa e nao lidas.
        JsonNode lista = ana.client().get("/conversations").json();
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("peer").get("username").asText())
                .isEqualTo(bruno.username());
        assertThat(lista.get(0).get("unreadCount").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("reenvio com o mesmo clientMessageId nao duplica a mensagem")
    void envioIdempotente() {
        Conversa conversa = conversaEntreContatos();
        UUID clientId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("body", "mensagem unica", "clientMessageId", clientId);

        TestApiClient.Response primeira =
                conversa.ana().client().post("/conversations/" + conversa.id() + "/messages", payload);
        TestApiClient.Response segunda =
                conversa.ana().client().post("/conversations/" + conversa.id() + "/messages", payload);

        assertThat(primeira.json().get("id").asText())
                .isEqualTo(segunda.json().get("id").asText());
        assertThat(conversa.ana().client()
                .get("/conversations/" + conversa.id() + "/messages").json().get("items"))
                .hasSize(1);
    }

    @Test
    @DisplayName("terceiro nao acessa a conversa nem sabe que ela existe")
    void terceiroNaoAcessa() {
        Conversa conversa = conversaEntreContatos();
        enviar(conversa.ana(), conversa.id(), "assunto privado");

        TestUser estranho = newUser();

        TestApiClient.Response leitura = estranho.client()
                .get("/conversations/" + conversa.id() + "/messages");
        assertThat(leitura.status()).isEqualTo(404);
        assertThat(leitura.errorCode()).isEqualTo("CONVERSATION_NOT_FOUND");
        assertThat(leitura.body()).doesNotContain("assunto privado");

        assertThat(enviar(estranho, conversa.id(), "intrometido").status()).isEqualTo(404);
        assertThat(estranho.client().get("/conversations").json()).isEmpty();
    }

    @Test
    @DisplayName("bloqueio impede o envio nos dois sentidos")
    void bloqueioImpedeEnvio() {
        Conversa conversa = conversaEntreContatos();

        assertThat(conversa.ana().client()
                .post("/contacts/" + conversa.bruno().id() + "/block", null)
                .status()).isEqualTo(204);

        assertThat(enviar(conversa.ana(), conversa.id(), "ainda escrevo?").errorCode())
                .isEqualTo("BLOCKED");
        // O efeito e reciproco: quem foi bloqueado tambem nao escreve.
        assertThat(enviar(conversa.bruno(), conversa.id(), "e eu?").errorCode())
                .isEqualTo("BLOCKED");

        // Desbloquear restaura a troca.
        conversa.ana().client().delete("/contacts/" + conversa.bruno().id() + "/block", null);
        assertThat(enviar(conversa.ana(), conversa.id(), "voltamos").status()).isEqualTo(201);
    }

    @Test
    @DisplayName("apagar mantem a linha sem corpo e so o autor pode fazer isso")
    void exclusaoDeMensagem() {
        Conversa conversa = conversaEntreContatos();
        String mensagemId = enviar(conversa.ana(), conversa.id(), "vou apagar")
                .json().get("id").asText();
        enviar(conversa.bruno(), conversa.id(), "recebi");

        // Bruno nao apaga mensagem de Ana.
        assertThat(conversa.bruno().client().delete("/messages/" + mensagemId, null).status())
                .isEqualTo(403);

        assertThat(conversa.ana().client().delete("/messages/" + mensagemId, null).status())
                .isEqualTo(204);

        JsonNode itens = conversa.bruno().client()
                .get("/conversations/" + conversa.id() + "/messages").json().get("items");

        // A ordem da conversa continua intacta: duas mensagens, a primeira sem corpo.
        assertThat(itens).hasSize(2);
        assertThat(itens.get(0).get("deleted").asBoolean()).isTrue();
        assertThat(itens.get(0).has("body")).isFalse();
        assertThat(itens.get(1).get("body").asText()).isEqualTo("recebi");
    }

    @Test
    @DisplayName("paginacao por cursor percorre o historico sem repetir")
    void paginacaoPorCursor() {
        Conversa conversa = conversaEntreContatos();
        for (int i = 1; i <= 7; i++) {
            enviar(conversa.ana(), conversa.id(), "mensagem " + i);
        }

        JsonNode primeira = conversa.ana().client()
                .get("/conversations/" + conversa.id() + "/messages?size=3").json();
        assertThat(primeira.get("items")).hasSize(3);
        assertThat(primeira.get("hasMore").asBoolean()).isTrue();
        // A pagina mais recente traz as ultimas mensagens, em ordem cronologica.
        assertThat(primeira.get("items").get(2).get("body").asText()).isEqualTo("mensagem 7");

        JsonNode segunda = conversa.ana().client()
                .get("/conversations/" + conversa.id() + "/messages?size=3&cursor="
                        + primeira.get("cursor").asText()).json();
        assertThat(segunda.get("items").get(2).get("body").asText()).isEqualTo("mensagem 4");
        assertThat(segunda.get("items").get(0).get("body").asText()).isEqualTo("mensagem 2");
    }

    @Test
    @DisplayName("since devolve apenas o que chegou depois do cursor")
    void mensagensNovasDesdeOCursor() {
        Conversa conversa = conversaEntreContatos();
        enviar(conversa.ana(), conversa.id(), "primeira");

        // Bruno carrega a conversa e guarda o cursor da mensagem mais recente.
        JsonNode pagina = conversa.bruno().client()
                .get("/conversations/" + conversa.id() + "/messages").json();
        String cursor = pagina.get("latestCursor").asText();
        assertThat(cursor).isNotBlank();

        // Sem novidade, a chamada volta vazia e o cursor nao se move.
        JsonNode vazio = conversa.bruno().client()
                .get("/conversations/" + conversa.id() + "/messages/since?cursor=" + cursor)
                .json();
        assertThat(vazio.get("items")).isEmpty();
        assertThat(vazio.get("latestCursor").asText()).isEqualTo(cursor);

        enviar(conversa.ana(), conversa.id(), "segunda");
        enviar(conversa.ana(), conversa.id(), "terceira");

        JsonNode novas = conversa.bruno().client()
                .get("/conversations/" + conversa.id() + "/messages/since?cursor=" + cursor)
                .json();

        // So o que chegou depois, em ordem cronologica. "primeira" nao volta.
        assertThat(novas.get("items")).hasSize(2);
        assertThat(novas.get("items").get(0).get("body").asText()).isEqualTo("segunda");
        assertThat(novas.get("items").get(1).get("body").asText()).isEqualTo("terceira");
        assertThat(novas.get("latestCursor").asText()).isNotEqualTo(cursor);
    }

    @Test
    @DisplayName("pedido de contato para si mesmo e recusado")
    void naoAdicionaSiMesmo() {
        TestUser ana = newUser();
        TestApiClient.Response response =
                ana.client().post("/contacts/requests", Map.of("username", ana.username()));
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("CANNOT_TARGET_SELF_CONTACT");
    }

    @Test
    @DisplayName("pedidos cruzados viram contato aceito direto")
    void pedidosCruzados() {
        TestUser ana = newUser();
        TestUser bruno = newUser();

        ana.client().post("/contacts/requests", Map.of("username", bruno.username()));
        // Bruno pede de volta sem ter visto o pedido de Ana.
        assertThat(bruno.client()
                .post("/contacts/requests", Map.of("username", ana.username()))
                .status()).isEqualTo(201);

        assertThat(ana.client().get("/contacts").json().get("contacts")).hasSize(1);
        assertThat(bruno.client().get("/contacts").json().get("contacts")).hasSize(1);
    }

    /* --------------------------------------------------------- utilitarios */

    private record Conversa(TestUser ana, TestUser bruno, String id) {
    }

    /** Duas pessoas ja aceitas como contato, com a conversa aberta. */
    private Conversa conversaEntreContatos() {
        TestUser ana = newUser();
        TestUser bruno = newUser();

        ana.client().post("/contacts/requests", Map.of("username", bruno.username()));
        String pedidoId = bruno.client().get("/contacts").json()
                .get("incoming").get(0).get("id").asText();
        bruno.client().post("/contacts/requests/" + pedidoId + "/accept", null);

        String id = ana.client().post("/conversations", Map.of("userId", bruno.id()))
                .json().get("id").asText();
        return new Conversa(ana, bruno, id);
    }

    private TestApiClient.Response enviar(TestUser user, String conversationId, String body) {
        return user.client().post("/conversations/" + conversationId + "/messages",
                Map.of("body", body, "clientMessageId", UUID.randomUUID()));
    }
}
