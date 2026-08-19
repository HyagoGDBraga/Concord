package app.concord.ws;

import app.concord.auth.ConcordUserDetails;
import app.concord.conversation.ConversationService;
import app.concord.user.UserRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Indicador de digitação — o único caminho de escrita que existe pelo WebSocket.
 *
 * <p>Justifica a exceção porque é o oposto de uma mensagem: efêmero, sem valor
 * histórico, e passar por HTTP significaria uma requisição a cada tecla. Nada
 * daqui toca o banco.
 *
 * <p>O remetente <b>nunca</b> vem do payload: é o {@code Principal} da sessão
 * STOMP, estabelecido no handshake. Um cliente que enviasse um {@code senderId}
 * forjado seria ignorado, porque o campo não existe no contrato.
 */
@Controller
public class TypingController {

    /** Sinal de digitação recebido do cliente. */
    public record TypingSignal(boolean typing) {
    }

    /** Sinal repassado ao interlocutor, já com o remetente resolvido. */
    public record TypingEvent(UUID conversationId, UUID userId, boolean typing) {
    }

    private final ConversationService conversationService;
    private final RealtimeNotifier notifier;
    private final UserRepository userRepository;

    public TypingController(ConversationService conversationService,
                            RealtimeNotifier notifier,
                            UserRepository userRepository) {
        this.conversationService = conversationService;
        this.notifier = notifier;
        this.userRepository = userRepository;
    }

    /** Cliente publica em {@code /app/conversations/{id}/typing}. */
    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable UUID conversationId,
                       TypingSignal signal,
                       Principal principal) {
        UUID userId = resolveUserId(principal);
        if (userId == null) {
            return;
        }
        // Mesma verificação de participação do REST. O WebSocket não é um
        // caminho paralelo com regras próprias.
        conversationService.requireParticipant(conversationId, userId);
        UUID peerId = conversationService.peerIdOf(conversationId, userId);

        notifier.sendToUser(peerId, RealtimeEvent.of(RealtimeEvent.TYPING,
                new TypingEvent(conversationId, userId, signal.typing())));
    }

    private UUID resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof ConcordUserDetails user) {
            return user.id();
        }
        if (principal == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(principal.getName())
                .map(app.concord.user.User::getId)
                .orElse(null);
    }
}
