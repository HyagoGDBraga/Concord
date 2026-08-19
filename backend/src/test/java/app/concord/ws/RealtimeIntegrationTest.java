package app.concord.ws;

import app.concord.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Entrega em tempo real e autenticacao do WebSocket. */
class RealtimeIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("handshake sem sessao e recusado")
    void handshakeAnonimoRecusado() {
        WebSocketStompClient client = stompClient();

        // Sem cookie de sessao: o AuthHandshakeInterceptor responde 401 e a
        // conexao nao chega a abrir.
        assertThatThrownBy(() -> client
                .connectAsync(wsUrl(), new WebSocketHttpHeaders(),
                        new StompSessionHandlerAdapter() {
                        })
                .get(10, TimeUnit.SECONDS))
                .isNotNull();
    }

    @Test
    @DisplayName("mensagem enviada por REST chega ao destinatario pelo WebSocket")
    void entregaEmTempoReal() throws Exception {
        TestUser ana = newUser();
        TestUser bruno = newUser();
        String conversaId = tornarContatosEAbrirConversa(ana, bruno);

        BlockingQueue<Map<String, Object>> recebidos = new LinkedBlockingQueue<>();
        StompSession sessaoDoBruno = conectar(bruno, recebidos);

        try {
            ana.client().post("/conversations/" + conversaId + "/messages",
                    Map.of("body", "chegou agora", "clientMessageId", UUID.randomUUID()));

            Map<String, Object> evento = recebidos.poll(10, TimeUnit.SECONDS);

            assertThat(evento).isNotNull();
            assertThat(evento.get("type")).isEqualTo(RealtimeEvent.MESSAGE_CREATED);

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) evento.get("payload");
            assertThat(payload.get("body")).isEqualTo("chegou agora");
            assertThat(payload.get("conversationId")).isEqualTo(conversaId);
        } finally {
            sessaoDoBruno.disconnect();
        }
    }

    @Test
    @DisplayName("quem nao participa da conversa nao recebe o evento")
    void estranhoNaoRecebe() throws Exception {
        TestUser ana = newUser();
        TestUser bruno = newUser();
        TestUser estranho = newUser();
        String conversaId = tornarContatosEAbrirConversa(ana, bruno);

        BlockingQueue<Map<String, Object>> doEstranho = new LinkedBlockingQueue<>();
        StompSession sessaoDoEstranho = conectar(estranho, doEstranho);

        try {
            ana.client().post("/conversations/" + conversaId + "/messages",
                    Map.of("body", "assunto privado", "clientMessageId", UUID.randomUUID()));

            // Espera deliberada: se algo fosse entregue por engano, chegaria
            // nesta janela.
            assertThat(doEstranho.poll(3, TimeUnit.SECONDS)).isNull();
        } finally {
            sessaoDoEstranho.disconnect();
        }
    }

    @Test
    @DisplayName("presenca avisa os contatos quando alguem conecta")
    void presencaAvisaContatos() throws Exception {
        TestUser ana = newUser();
        TestUser bruno = newUser();
        tornarContatosEAbrirConversa(ana, bruno);

        BlockingQueue<Map<String, Object>> daAna = new LinkedBlockingQueue<>();
        StompSession sessaoDaAna = conectar(ana, daAna);

        StompSession sessaoDoBruno = null;
        try {
            sessaoDoBruno = conectar(bruno, new LinkedBlockingQueue<>());

            Map<String, Object> evento = aguardarEvento(daAna, RealtimeEvent.PRESENCE);
            assertThat(evento).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) evento.get("payload");
            assertThat(payload.get("userId")).isEqualTo(bruno.id().toString());
            assertThat(payload.get("online")).isEqualTo(true);
        } finally {
            if (sessaoDoBruno != null) {
                sessaoDoBruno.disconnect();
            }
            sessaoDaAna.disconnect();
        }
    }

    @Test
    @DisplayName("o snapshot de presenca so lista contatos aceitos")
    void snapshotSoDeContatos() throws Exception {
        TestUser ana = newUser();
        TestUser bruno = newUser();
        TestUser estranho = newUser();
        tornarContatosEAbrirConversa(ana, bruno);

        StompSession sessaoDoBruno = conectar(bruno, new LinkedBlockingQueue<>());
        StompSession sessaoDoEstranho = conectar(estranho, new LinkedBlockingQueue<>());

        try {
            // Dá tempo de o registro de presença refletir as duas conexões.
            Thread.sleep(500);

            JsonNode snapshot = ana.client().get("/presence").json();
            JsonNode online = snapshot.get("onlineContactIds");

            assertThat(online).hasSize(1);
            assertThat(online.get(0).asText()).isEqualTo(bruno.id().toString());
        } finally {
            sessaoDoBruno.disconnect();
            sessaoDoEstranho.disconnect();
        }
    }

    /* --------------------------------------------------------- utilitarios */

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/api/ws";
    }

    /** Conecta como o usuario informado, reaproveitando o cookie de sessao. */
    private StompSession conectar(TestUser user, BlockingQueue<Map<String, Object>> destino)
            throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "concord_session=" + user.client().sessionCookie());

        StompSession session = stompClient()
                .connectAsync(wsUrl(), headers, new StompSessionHandlerAdapter() {
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
        return session;
    }

    /** Descarta eventos de outros tipos ate encontrar o esperado. */
    private Map<String, Object> aguardarEvento(BlockingQueue<Map<String, Object>> fila,
                                               String tipo) throws InterruptedException {
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite) {
            Map<String, Object> evento = fila.poll(1, TimeUnit.SECONDS);
            if (evento != null && tipo.equals(evento.get("type"))) {
                return evento;
            }
        }
        return null;
    }

    private String tornarContatosEAbrirConversa(TestUser ana, TestUser bruno) {
        ana.client().post("/contacts/requests", Map.of("username", bruno.username()));
        String pedidoId = bruno.client().get("/contacts").json()
                .get("incoming").get(0).get("id").asText();
        bruno.client().post("/contacts/requests/" + pedidoId + "/accept", null);

        return ana.client().post("/conversations", Map.of("userId", bruno.id()))
                .json().get("id").asText();
    }
}
