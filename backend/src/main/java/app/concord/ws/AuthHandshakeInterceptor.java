package app.concord.ws;

import app.concord.auth.ConcordUserDetails;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

/**
 * Autenticação do handshake do WebSocket.
 *
 * <p>O handshake é uma requisição HTTP comum: o navegador anexa o cookie
 * {@code concord_session} sozinho, o filtro do Spring Session resolve a sessão e
 * o Spring Security popula o contexto — tudo antes deste código rodar. Não há
 * token na query string, que ficaria registrado em log de proxy, histórico e
 * cabeçalho Referer.
 *
 * <p>Sem autenticação, a conexão simplesmente não abre.
 */
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthHandshakeInterceptor.class);

    /** Id da sessão HTTP, guardado para a varredura de sessões revogadas. */
    public static final String HTTP_SESSION_ID = "concord.httpSessionId";
    public static final String USER_ID = "concord.userId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        Principal principal = request.getPrincipal();

        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ConcordUserDetails user)) {
            log.debug("Handshake WebSocket recusado: sem sessão autenticada");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(USER_ID, user.id());

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            if (session != null) {
                attributes.put(HTTP_SESSION_ID, session.getId());
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
        // Nada a fazer.
    }
}
