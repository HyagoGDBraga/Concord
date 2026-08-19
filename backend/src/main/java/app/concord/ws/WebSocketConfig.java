package app.concord.ws;

import app.concord.presence.PresenceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * Configuração do WebSocket e do STOMP.
 *
 * <p>Broker simples, em memória. É dívida técnica conhecida e registrada: ele
 * obriga o backend a rodar em instância única, porque o estado das assinaturas
 * vive na JVM. Para a escala do Concord isso é adequado; se um dia houver mais
 * de uma instância, a troca é por um broker externo (RabbitMQ) e a mudança fica
 * contida nesta classe.
 *
 * <p>O endpoint fica em {@code /api/ws} — o {@code /api} vem do context-path da
 * aplicação, não é declarado aqui.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthHandshakeInterceptor handshakeInterceptor;
    private final StompInboundInterceptor inboundInterceptor;
    private final WebSocketSessionRegistry sessionRegistry;
    private final PresenceService presenceService;

    public WebSocketConfig(AuthHandshakeInterceptor handshakeInterceptor,
                           StompInboundInterceptor inboundInterceptor,
                           WebSocketSessionRegistry sessionRegistry,
                           PresenceService presenceService) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.inboundInterceptor = inboundInterceptor;
        this.sessionRegistry = sessionRegistry;
        this.presenceService = presenceService;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(handshakeInterceptor);
        // Sem SockJS: os navegadores-alvo e o Electron suportam WebSocket
        // nativo. A camada de fallback do SockJS acrescentaria transportes por
        // polling — mais superfície, sem ganho.
        // Sem setAllowedOrigins: o padrão do Spring já é aceitar apenas a mesma
        // origem, que é exatamente o arranjo do Caddy.
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(WsDestinations.APPLICATION_PREFIX);
        registry.setUserDestinationPrefix(WsDestinations.USER_PREFIX);
        registry.enableSimpleBroker(WsDestinations.BROKER_PREFIX)
                // Heartbeat nos dois sentidos: detecta conexão morta por queda
                // de rede, em que nenhum dos lados recebe o fechamento.
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Teto do frame. Mensagem de texto vai até 4000 caracteres; 64 KiB cobre
        // com folga e impede que um cliente hostil ocupe memória com um frame
        // gigante.
        registration.setMessageSizeLimit(64 * 1024);
        registration.setSendTimeLimit(15_000);
        registration.setSendBufferSizeLimit(512 * 1024);

        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessionRegistry.register(session);
                presenceService.onConnect(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
                    throws Exception {
                sessionRegistry.unregister(session.getId());
                presenceService.onDisconnect(session);
                super.afterConnectionClosed(session, status);
            }
        });
    }

    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
