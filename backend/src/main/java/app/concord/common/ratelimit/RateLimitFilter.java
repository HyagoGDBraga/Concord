package app.concord.common.ratelimit;

import app.concord.common.dto.ErrorResponse;
import app.concord.common.exception.ErrorCode;
import app.concord.common.request.ClientIp;
import app.concord.common.request.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Aplica limites de taxa aos endpoints públicos de autenticação, antes que a
 * requisição chegue ao Spring Security.
 *
 * <p>A chave é o IP de origem. Limites por conta (que dependem de saber quem é o
 * usuário) ficam no {@code LoginAttemptService}, dentro do fluxo de login.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("POST", "/auth/register", 3, Duration.ofHours(1)),
            new RateLimitRule("POST", "/auth/login", 5, Duration.ofMinutes(1)),
            new RateLimitRule("POST", "/auth/password/forgot", 3, Duration.ofHours(1)),
            new RateLimitRule("POST", "/auth/password/reset", 5, Duration.ofHours(1)),
            new RateLimitRule("POST", "/auth/verify-email/resend", 1, Duration.ofMinutes(5)),
            new RateLimitRule("GET", "/auth/username-available", 20, Duration.ofMinutes(1))
    );

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        String method = request.getMethod();

        RateLimitRule rule = RULES.stream()
                .filter(r -> r.matches(method, path))
                .findFirst()
                .orElse(null);

        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip = ClientIp.of(request);
        String key = "ip:" + (ip == null ? "unknown" : ip) + ":" + method + ":" + path;

        if (rateLimiter.tryConsume(key, rule.limit(), rule.window())) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit atingido: {} {} ip={}", method, path, ip);
        writeTooManyRequests(request, response, rule);
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                      RateLimitRule rule) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMITED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", String.valueOf(rule.window().toSeconds()));

        ErrorResponse body = ErrorResponse.of(
                ErrorCode.RATE_LIMITED.name(),
                ErrorCode.RATE_LIMITED.defaultMessage(),
                RequestIdFilter.current(request));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
