package app.concord.ws;

import app.concord.auth.ConcordUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro das conexões WebSocket abertas.
 *
 * <p>Existe por um motivo específico: o broker STOMP não oferece um jeito de
 * fechar uma conexão de fora. Guardando a {@link WebSocketSession}, é possível
 * derrubar quem teve a sessão HTTP revogada — sem isso, um usuário desativado
 * por um administrador continuaria recebendo mensagens em tempo real até a
 * conexão cair por conta própria.
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    /**
     * @param session       conexão viva
     * @param userId        dono da conexão
     * @param httpSessionId sessão HTTP que autorizou o handshake
     */
    public record Connection(WebSocketSession session, UUID userId, String httpSessionId) {
    }

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        UUID userId = resolveUserId(session);
        if (userId == null) {
            return;
        }
        String httpSessionId = (String) session.getAttributes()
                .get(AuthHandshakeInterceptor.HTTP_SESSION_ID);
        connections.put(session.getId(), new Connection(session, userId, httpSessionId));
    }

    public void unregister(String wsSessionId) {
        connections.remove(wsSessionId);
    }

    public List<Connection> all() {
        return List.copyOf(connections.values());
    }

    /** Fecha a conexão informando o motivo, para o cliente não tentar reconectar em vão. */
    public void close(Connection connection, CloseStatus status) {
        connections.remove(connection.session().getId());
        try {
            if (connection.session().isOpen()) {
                connection.session().close(status);
            }
        } catch (IOException ex) {
            log.debug("Falha ao fechar conexão WebSocket", ex);
        }
    }

    public int size() {
        return connections.size();
    }

    private static UUID resolveUserId(WebSocketSession session) {
        Object fromAttributes = session.getAttributes().get(AuthHandshakeInterceptor.USER_ID);
        if (fromAttributes instanceof UUID id) {
            return id;
        }
        if (session.getPrincipal() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof ConcordUserDetails user) {
            return user.id();
        }
        return null;
    }
}
