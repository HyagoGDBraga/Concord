package app.concord.presence;

import app.concord.auth.ConcordUserDetails;
import app.concord.call.CallService;
import app.concord.contact.ContactRepository;
import app.concord.ws.RealtimeEvent;
import app.concord.ws.RealtimeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presença: quem está conectado agora.
 *
 * <p>Estado em memória, e é o correto: presença é efêmera por definição. Se o
 * backend reinicia, ninguém está conectado — a informação se reconstrói sozinha
 * quando os clientes reconectam. Persistir isso criaria um registro histórico
 * de quando cada pessoa esteve online, que é exatamente o tipo de metadado que
 * o Concord evita.
 *
 * <p>O evento de presença só vai para os <b>contatos aceitos</b>. Não existe
 * consulta de presença de estranhos.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    /** Uma pessoa pode ter várias abas ou dispositivos abertos ao mesmo tempo. */
    private final Map<UUID, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    private final ContactRepository contactRepository;
    private final RealtimeNotifier notifier;
    private final CallService callService;

    public PresenceService(ContactRepository contactRepository,
                           @Lazy RealtimeNotifier notifier,
                           @Lazy CallService callService) {
        this.contactRepository = contactRepository;
        this.notifier = notifier;
        this.callService = callService;
    }

    public void onConnect(WebSocketSession session) {
        UUID userId = userIdOf(session);
        if (userId == null) {
            return;
        }
        Set<String> sessions = sessionsByUser.computeIfAbsent(
                userId, key -> ConcurrentHashMap.newKeySet());
        boolean first = sessions.isEmpty();
        sessions.add(session.getId());

        // Só avisa na transição offline -> online. Abrir uma segunda aba não é
        // um evento que interesse a ninguém.
        if (first) {
            broadcast(userId, true);
        }
    }

    public void onDisconnect(WebSocketSession session) {
        UUID userId = userIdOf(session);
        if (userId == null) {
            return;
        }
        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session.getId());
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId);
            broadcast(userId, false);

            // A pessoa não tem mais nenhuma conexão: qualquer chamada aberta
            // dela morreu junto. Sem isso, o outro lado continuaria vendo "em
            // chamada" com alguém que já foi embora.
            try {
                callService.endOpenCallsOf(userId);
            } catch (Exception ex) {
                log.warn("Falha ao encerrar chamadas de conexão perdida", ex);
            }
        }
    }

    public boolean isOnline(UUID userId) {
        Set<String> sessions = sessionsByUser.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /** Quais contatos do usuário estão online neste momento. */
    public List<UUID> onlineContactsOf(UUID userId) {
        return contactRepository.findAcceptedContactIds(userId).stream()
                .filter(this::isOnline)
                .toList();
    }

    public int connectedUsers() {
        return sessionsByUser.size();
    }

    private void broadcast(UUID userId, boolean online) {
        List<UUID> contacts = contactRepository.findAcceptedContactIds(userId);
        if (contacts.isEmpty()) {
            return;
        }
        log.debug("Presença alterada: online={}", online);
        notifier.send(contacts, RealtimeEvent.of(RealtimeEvent.PRESENCE,
                new PresenceDtos.PresenceEvent(userId, online, Instant.now())));
    }

    private static UUID userIdOf(WebSocketSession session) {
        Object attribute = session.getAttributes().get("concord.userId");
        if (attribute instanceof UUID id) {
            return id;
        }
        if (session.getPrincipal() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof ConcordUserDetails user) {
            return user.id();
        }
        return null;
    }
}
