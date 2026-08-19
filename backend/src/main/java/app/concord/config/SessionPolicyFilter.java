package app.concord.config;

import app.concord.auth.SessionAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Aplica o teto absoluto de duração da sessão.
 *
 * <p>O Spring Session expira por inatividade. Uma sessão usada todo dia poderia
 * viver indefinidamente, então este filtro impõe o limite de 30 dias contados a
 * partir da criação, independentemente do uso.
 */
public class SessionPolicyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionPolicyFilter.class);
    private static final Duration ABSOLUTE_MAX_AGE = Duration.ofDays(30);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object createdAt = session.getAttribute(SessionAttributes.CREATED_AT);
            if (createdAt instanceof Long millis) {
                Instant limit = Instant.ofEpochMilli(millis).plus(ABSOLUTE_MAX_AGE);
                if (Instant.now().isAfter(limit)) {
                    log.info("Sessão encerrada por atingir a idade máxima absoluta");
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                }
            }
        }
        chain.doFilter(request, response);
    }
}
