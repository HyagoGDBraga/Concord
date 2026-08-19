package app.concord.call;

import app.concord.support.AbstractIntegrationTest;
import app.concord.support.TestApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Ciclo de vida da chamada e repasse de sinalizacao. */
class CallLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("nao se liga para quem esta offline")
    void naoLigaParaOffline() {
        Par par = contatos();
        // Ninguem conectou o WebSocket: os dois estao offline.
        TestApiClient.Response response = par.ana().client().post("/calls",
                Map.of("conversationId", par.conversaId(), "type", "AUDIO"));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("CALLEE_UNAVAILABLE");
    }

    @Test
    @DisplayName("convite, aceite e encerramento seguem a maquina de estados")
    void cicloCompleto() throws Exception {
        Par par = contatos();
        BlockingQueue<Map<String, Object>> doBruno = new LinkedBlockingQueue<>();
        StompSession sessaoBruno = conectar(par.bruno(), doBruno);
        StompSession sessaoAna = conectar(par.ana(), new LinkedBlockingQueue<>());

        try {
            TestApiClient.Response convite = par.ana().client().post("/calls",
                    Map.of("conversationId", par.conversaId(), "type", "AUDIO"));

            assertThat(convite.status()).isEqualTo(201);
            assertThat(convite.json().get("status").asText()).isEqualTo("RINGING");
            String callId = convite.json().get("id").asText();

            // O convite chega ao destinatario em tempo real.
            Map<String, Object> evento = aguardar(doBruno, "CALL_INVITE");
            assertThat(evento).isNotNull();

            // Aceitar leva a chamada para ACTIVE.
            TestApiClient.Response aceite =
                    par.bruno().client().post("/calls/" + callId + "/accept", null);
            assertThat(aceite.status()).isEqualTo(200);
            assertThat(aceite.json().get("status").asText()).isEqualTo("ACTIVE");

            // Enquanto esta aberta, aparece em /calls/current para os dois.
            assertThat(par.ana().client().get("/calls/current").json()
                    .get("id").asText()).isEqualTo(callId);

            // Desligar encerra com HANGUP.
            TestApiClient.Response fim =
                    par.ana().client().post("/calls/" + callId + "/end", null);
            assertThat(fim.json().get("status").asText()).isEqualTo("ENDED");
            assertThat(fim.json().get("endReason").asText()).isEqualTo("HANGUP");

            assertThat(par.bruno().client().get("/calls/current").body()).isEmpty();
        } finally {
            sessaoBruno.disconnect();
            sessaoAna.disconnect();
        }
    }

    @Test
    @DisplayName("recusar encerra a chamada com REJECTED")
    void recusa() throws Exception {
        Par par = contatos();
        StompSession sessaoBruno = conectar(par.bruno(), new LinkedBlockingQueue<>());
        StompSession sessaoAna = conectar(par.ana(), new LinkedBlockingQueue<>());

        try {
            String callId = par.ana().client().post("/calls",
                            Map.of("conversationId", par.conversaId(), "type", "VIDEO"))
                    .json().get("id").asText();

            TestApiClient.Response recusa =
                    par.bruno().client().post("/calls/" + callId + "/reject", null);

            assertThat(recusa.json().get("endReason").asText()).isEqualTo("REJECTED");

            // Quem ligou nao pode "aceitar" a propria chamada, nem depois.
            assertThat(par.ana().client().post("/calls/" + callId + "/accept", null)
                    .status()).isIn(404, 409);
        } finally {
            sessaoBruno.disconnect();
            sessaoAna.disconnect();
        }
    }

    @Test
    @DisplayName("quem ja esta em chamada nao recebe outra")
    void ocupado() throws Exception {
        Par par = contatos();
        TestUser carla = newUser();
        tornarContatos(par.ana(), carla);

        StompSession s1 = conectar(par.ana(), new LinkedBlockingQueue<>());
        StompSession s2 = conectar(par.bruno(), new LinkedBlockingQueue<>());
        StompSession s3 = conectar(carla, new LinkedBlockingQueue<>());

        try {
            par.ana().client().post("/calls",
                    Map.of("conversationId", par.conversaId(), "type", "AUDIO"));

            String conversaComCarla = par.ana().client()
                    .post("/conversations", Map.of("userId", carla.id()))
                    .json().get("id").asText();

            TestApiClient.Response segunda = par.ana().client().post("/calls",
                    Map.of("conversationId", conversaComCarla, "type", "AUDIO"));

            assertThat(segunda.status()).isEqualTo(409);
            assertThat(segunda.errorCode()).isEqualTo("CALL_ALREADY_ACTIVE");
        } finally {
            s1.disconnect();
            s2.disconnect();
            s3.disconnect();
        }
    }

    @Test
    @DisplayName("estranho nao acessa a chamada nem recebe sinalizacao")
    void estranhoNaoParticipa() throws Exception {
        Par par = contatos();
        TestUser estranho = newUser();

        StompSession s1 = conectar(par.ana(), new LinkedBlockingQueue<>());
        StompSession s2 = conectar(par.bruno(), new LinkedBlockingQueue<>());
        BlockingQueue<Map<String, Object>> doEstranho = new LinkedBlockingQueue<>();
        StompSession s3 = conectar(estranho, doEstranho);

        try {
            String callId = par.ana().client().post("/calls",
                            Map.of("conversationId", par.conversaId(), "type", "AUDIO"))
                    .json().get("id").asText();

            assertThat(estranho.client().post("/calls/" + callId + "/accept", null)
                    .status()).isEqualTo(404);
            assertThat(estranho.client().post("/calls/" + callId + "/end", null)
                    .status()).isEqualTo(404);

            // Nenhum evento da chamada vaza para quem nao participa.
            assertThat(aguardar(doEstranho, "CALL_INVITE")).isNull();
        } finally {
            s1.disconnect();
            s2.disconnect();
            s3.disconnect();
        }
    }

    @Test
    @DisplayName("as credenciais de ICE exigem autenticacao")
    void iceExigeAutenticacao() {
        TestApiClient anonimo = new TestApiClient(port, objectMapper);
        assertThat(anonimo.get("/webrtc/ice").status()).isEqualTo(401);

        TestUser ana = newUser();
        JsonNode config = ana.client().get("/webrtc/ice").json();
        assertThat(config.get("iceServers")).isNotEmpty();
        assertThat(config.get("expiresAt")).isNotNull();
    }

    /* --------------------------------------------------------- utilitarios */

    private record Par(TestUser ana, TestUser bruno, String conversaId) {
    }

    private Par contatos() {
        TestUser ana = newUser();
        TestUser bruno = newUser();
        tornarContatos(ana, bruno);
        String conversaId = ana.client()
                .post("/conversations", Map.of("userId", bruno.id()))
                .json().get("id").asText();
        return new Par(ana, bruno, conversaId);
    }

    private void tornarContatos(TestUser a, TestUser b) {
        a.client().post("/contacts/requests", Map.of("username", b.username()));
        String pedidoId = b.client().get("/contacts").json()
                .get("incoming").get(0).get("id").asText();
        b.client().post("/contacts/requests/" + pedidoId + "/accept", null);
    }

    private StompSession conectar(TestUser user, BlockingQueue<Map<String, Object>> destino)
            throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "concord_session=" + user.client().sessionCookie());

        StompSession session = client
                .connectAsync("ws://localhost:" + port + "/api/ws", headers,
                        new StompSessionHandlerAdapter() {
                        })
                .get(10, TimeUnit.SECONDS);

        session.subscribe("/user/queue/events", new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                if (payload instanceof Map<?, ?> map) {
                    destino.add((Map<String, Object>) map);
                }
            }
        });
        // Deixa a presenca se registrar antes de qualquer chamada.
        Thread.sleep(300);
        return session;
    }

    private Map<String, Object> aguardar(BlockingQueue<Map<String, Object>> fila, String tipo)
            throws InterruptedException {
        long limite = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < limite) {
            Map<String, Object> evento = fila.poll(1, TimeUnit.SECONDS);
            if (evento != null && tipo.equals(evento.get("type"))) {
                return evento;
            }
        }
        return null;
    }
}
