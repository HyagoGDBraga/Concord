package app.concord.ws;

import app.concord.user.User;
import app.concord.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Envio de eventos para destinos de usuário.
 *
 * <p>Recebe ids de usuário e traduz para o {@code Principal.getName()} — que no
 * Concord é o username, o mesmo valor que indexa as sessões. Os serviços de
 * negócio não precisam saber nada de STOMP.
 *
 * <p>Falha de entrega nunca derruba a operação: se a mensagem foi gravada, ela
 * existe. O tempo real é entrega otimista; a verdade está no banco, e o cliente
 * a recupera pelo endpoint {@code /messages/since} ao reconectar.
 */
@Component
public class RealtimeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNotifier.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public RealtimeNotifier(SimpMessagingTemplate messagingTemplate,
                            UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public void send(Collection<UUID> userIds, RealtimeEvent event) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Map<UUID, String> usernames = new HashMap<>();
        userRepository.findAllById(userIds)
                .forEach(user -> usernames.put(user.getId(), user.getUsername()));

        for (UUID userId : userIds) {
            String username = usernames.get(userId);
            if (username == null) {
                continue;
            }
            sendToUsername(username, event);
        }
    }

    @Transactional(readOnly = true)
    public void sendToUser(UUID userId, RealtimeEvent event) {
        userRepository.findById(userId)
                .map(User::getUsername)
                .ifPresent(username -> sendToUsername(username, event));
    }

    private void sendToUsername(String username, RealtimeEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(username, WsDestinations.EVENTS, event);
        } catch (Exception ex) {
            log.warn("Falha ao entregar evento {} em tempo real", event.type(), ex);
        }
    }
}
