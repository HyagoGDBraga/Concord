package app.concord.auth;

import app.concord.user.UserDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Consulta e revogação de sessões.
 *
 * <p>Tudo se apoia no índice {@code SPRING_SESSION_IX3 (PRINCIPAL_NAME)}, que o
 * Spring Session preenche automaticamente a partir do contexto de segurança
 * gravado na sessão. É por isso que o username é imutável no Concord: ele é a
 * chave pela qual as sessões de uma pessoa são localizadas.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SessionService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public List<UserDtos.SessionResponse> listFor(String username, String currentSessionId) {
        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(username);

        return sessions.values().stream()
                .map(session -> toResponse(session, currentSessionId))
                .sorted(Comparator.comparing(UserDtos.SessionResponse::lastAccessedAt).reversed())
                .toList();
    }

    /**
     * Revoga uma sessão específica do usuário.
     *
     * @return {@code true} se a sessão existia e pertencia a ele. A verificação
     *         de posse acontece aqui: um id de sessão de outra pessoa nunca é
     *         apagado, mesmo que o id seja conhecido.
     */
    public boolean revoke(String username, String sessionId) {
        if (!sessionRepository.findByPrincipalName(username).containsKey(sessionId)) {
            return false;
        }
        sessionRepository.deleteById(sessionId);
        return true;
    }

    /** Revoga todas as sessões do usuário, exceto a informada. */
    public int revokeAllExcept(String username, String keepSessionId) {
        int revoked = 0;
        for (String id : sessionRepository.findByPrincipalName(username).keySet()) {
            if (!id.equals(keepSessionId)) {
                sessionRepository.deleteById(id);
                revoked++;
            }
        }
        return revoked;
    }

    /**
     * Revoga todas as sessões do usuário, sem exceção.
     *
     * <p>Usado no reset de senha, na desativação pelo administrador e na
     * exclusão de conta. Também derruba conexões WebSocket a partir da Fase 4:
     * sem linha em {@code SPRING_SESSION}, o handshake é recusado.
     */
    public int revokeAll(String username) {
        int revoked = 0;
        for (String id : sessionRepository.findByPrincipalName(username).keySet()) {
            sessionRepository.deleteById(id);
            revoked++;
        }
        log.info("Sessões revogadas para o usuário: {}", revoked);
        return revoked;
    }

    /** Grava os metadados exibidos na tela de sessões ativas. */
    public void recordMetadata(HttpServletRequest request, java.util.UUID userId, String ip) {
        var session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.setAttribute(SessionAttributes.CREATED_AT, System.currentTimeMillis());
        session.setAttribute(SessionAttributes.USER_ID, userId.toString());
        session.setAttribute(SessionAttributes.IP, ip);
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            session.setAttribute(SessionAttributes.USER_AGENT,
                    userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);
        }
    }

    private UserDtos.SessionResponse toResponse(Session session, String currentSessionId) {
        Object createdAt = session.getAttribute(SessionAttributes.CREATED_AT);
        Instant created = createdAt instanceof Long millis
                ? Instant.ofEpochMilli(millis)
                : session.getCreationTime();

        return new UserDtos.SessionResponse(
                session.getId(),
                created,
                session.getLastAccessedTime(),
                session.getAttribute(SessionAttributes.IP),
                session.getAttribute(SessionAttributes.USER_AGENT),
                session.getId().equals(currentSessionId));
    }
}
