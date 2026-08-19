package app.concord.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Guarda do canal de entrada do STOMP.
 *
 * <p>Duas verificações, nesta ordem:
 *
 * <ol>
 *   <li><b>Principal presente.</b> O handshake já exigiu autenticação, mas cada
 *       frame é reconferido — o custo é uma comparação e a alternativa é
 *       confiar em estado que pode ter mudado.</li>
 *   <li><b>Subscrição apenas em {@code /user/queue/}.</b> Sem isso, um cliente
 *       poderia assinar diretamente {@code /queue/events-<sufixo>}, o destino
 *       interno para onde o Spring reescreve as filas de usuário, e receber
 *       eventos de outra pessoa se acertasse o sufixo.</li>
 * </ol>
 */
@Component
public class StompInboundInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompInboundInterceptor.class);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.DISCONNECT) {
            return message;
        }

        if (accessor.getUser() == null) {
            log.warn("Frame STOMP {} sem principal recusado", command);
            throw new IllegalArgumentException("Não autenticado");
        }

        if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (destination == null
                    || !destination.startsWith(WsDestinations.ALLOWED_SUBSCRIPTION_PREFIX)) {
                log.warn("Subscrição recusada em destino não permitido: {}", destination);
                throw new IllegalArgumentException("Destino não permitido");
            }
        }
        return message;
    }
}
