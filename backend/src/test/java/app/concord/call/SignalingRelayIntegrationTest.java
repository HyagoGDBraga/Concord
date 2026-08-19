package app.concord.call;

import app.concord.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repasse de sinalizacao WebRTC.
 *
 * <p>O servidor e um carteiro: confere quem envia e entrega ao outro lado sem
 * ler o conteudo. Estes testes cobrem as duas metades disso — a entrega
 * funciona, e ninguem de fora consegue injetar sinal em chamada alheia.
 */
class SignalingRelayIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("o sinal e repassado ao outro participante, intacto")
    void repassaSinalAoPar() throws Exception {
        Cenario cenario = chamadaAtiva();

        try {
            // Ana avisa que comecou a compartilhar a tela.
            cenario.sessaoDaAna().send(
                    "/app/calls/" + cenario.callId() + "/signal",
                    Map.of("type", "SCREEN_SHARE", "payload", Map.of("active", true)));

            Map<String, Object> evento = aguardar(cenario.doBruno(), "CALL_SIGNAL");
            assertThat(evento).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) evento.get("payload");

            assertThat(payload.get("type")).isEqualTo("SCREEN_SHARE");
            assertThat(payload.get("callId")).isEqualTo(cenario.callId());
            // O remetente vem do servidor, nao do que o cliente enviou.
            assertThat(payload.get("fromUserId")).isEqualTo(cenario.anaId());

            @SuppressWarnings("unchecked")
            Map<String, Object> conteudo = (Map<String, Object>) payload.get("payload");
            assertThat(conteudo.get("active")).isEqualTo(true);
        } finally {
            cenario.fechar();
        }
    }

    @Test
    @DisplayName("quem nao participa da chamada nao consegue injetar sinal")
    void estranhoNaoInjetaSinal() throws Exception {
        Cenario cenario = chamadaAtiva();
        TestUser estranho = newUser();
        StompSession sessaoDoEstranho = conectar(estranho, new LinkedBlockingQueue<>());

        try {
            // Descarta o que ja estava na fila antes da tentativa.
            cenario.doBruno().clear();

            sessaoDoEstranho.send("/app/calls/" + cenario.callId() + "/signal",
                    Map.of("type", "OFFER", "payload", Map.of("sdp", "forjado")));

            // Nada chega a Bruno: o servidor recusa antes de repassar.
            assertThat(aguardar(cenario.doBruno(), "CALL_SIGNAL")).isNull();
        } finally {
            sessaoDoEstranho.disconnect();
            cenario.fechar();
        }
    }

    @Test
    @DisplayName("chamada encerrada nao repassa mais sinal")
    void chamadaEncerradaNaoRepassa() throws Exception {
        Cenario cenario = chamadaAtiva();

        try {
            cenario.ana().client().post("/calls/" + cenario.callId() + "/end", null);
            cenario.doBruno().clear();

            cenario.sessaoDaAna().send("/app/calls/" + cenario.callId() + "/signal",
                    Map.of("type", "ICE_CANDIDATE", "payload", Map.of("candidate", "x")));

            assertThat(aguardar(cenario.doBruno(), "CALL_SIGNAL")).isNull();
        } finally {
            cenario.fechar();
        }
    }

    /* --------------------------------------------------------- utilitarios */

    private record Cenario(TestUser ana, TestUser bruno, String callId, String anaId,
                           StompSession sessaoDaAna, StompSession sessaoDoBruno,
                           BlockingQueue<Map<String, Object>> doBruno) {

        void fechar() {
            sessaoDaAna.disconnect();
            sessaoDoBruno.disconnect();
        }
    }

    private Cenario chamadaAtiva() throws Exception {
        TestUser ana = newUser();
        TestUser bruno = newUser();

        ana.client().post("/contacts/requests", Map.of("username", bruno.username()));
        String pedidoId = bruno.client().get("/contacts").json()
                .get("incoming").get(0).get("id").asText();
        bruno.client().post("/contacts/requests/" + pedidoId + "/accept", null);

        String conversaId = ana.client()
                .post("/conversations", Map.of("userId", bruno.id()))
                .json().get("id").asText();

        BlockingQueue<Map<String, Object>> doBruno = new LinkedBlockingQueue<>();
        StompSession sessaoDoBruno = conectar(bruno, doBruno);
        StompSession sessaoDaAna = conectar(ana, new LinkedBlockingQueue<>());

        String callId = ana.client().post("/calls",
                        Map.of("conversationId", conversaId, "type", "VIDEO"))
                .json().get("id").asText();
        bruno.client().post("/calls/" + callId + "/accept", null);

        return new Cenario(ana, bruno, callId, ana.id().toString(),
                sessaoDaAna, sessaoDoBruno, doBruno);
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
        Thread.sleep(300);
        return session;
    }

    private Map<String, Object> aguardar(BlockingQueue<Map<String, Object>> fila, String tipo)
            throws InterruptedException {
        long limite = System.currentTimeMillis() + 6_000;
        while (System.currentTimeMillis() < limite) {
            Map<String, Object> evento = fila.poll(1, TimeUnit.SECONDS);
            if (evento != null && tipo.equals(evento.get("type"))) {
                return evento;
            }
        }
        return null;
    }
}
