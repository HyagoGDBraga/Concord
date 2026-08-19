package app.concord.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

/**
 * Derruba conexões cujo respaldo em sessão HTTP deixou de existir.
 *
 * <p>Este é o elo que fecha a promessa do documento 02 §6: revogar a sessão
 * encerra também o tempo real. Sem ele, um usuário desativado por um
 * administrador continuaria recebendo mensagens até a conexão cair sozinha —
 * o handshake já teria acontecido, e nada mais o reavaliaria.
 *
 * <p>É uma varredura periódica, e não uma reação a evento, porque o Spring
 * Session JDBC não publica notificação confiável de remoção. Trinta segundos de
 * janela é o compromisso entre custo e prontidão.
 */
@Component
public class RevokedSessionSweeper {

    private static final Logger log = LoggerFactory.getLogger(RevokedSessionSweeper.class);

    private static final CloseStatus SESSION_REVOKED =
            new CloseStatus(4401, "Sessão encerrada");

    private final WebSocketSessionRegistry registry;
    private final SessionRepository<? extends Session> sessionRepository;

    public RevokedSessionSweeper(WebSocketSessionRegistry registry,
                                 SessionRepository<? extends Session> sessionRepository) {
        this.registry = registry;
        this.sessionRepository = sessionRepository;
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void closeOrphanConnections() {
        int closed = 0;
        for (WebSocketSessionRegistry.Connection connection : registry.all()) {
            String httpSessionId = connection.httpSessionId();
            if (httpSessionId == null) {
                continue;
            }
            if (sessionRepository.findById(httpSessionId) == null) {
                registry.close(connection, SESSION_REVOKED);
                closed++;
            }
        }
        if (closed > 0) {
            log.info("Conexões WebSocket encerradas por sessão revogada: {}", closed);
        }
    }
}
