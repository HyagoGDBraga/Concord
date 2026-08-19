package app.concord.common.ratelimit;

import java.time.Duration;

/**
 * Regra de limite para um par método + caminho.
 *
 * @param method  método HTTP
 * @param path    caminho do servlet, já sem o context-path {@code /api}
 * @param limit   número de requisições permitidas na janela
 * @param window  duração da janela
 */
public record RateLimitRule(String method, String path, int limit, Duration window) {

    public boolean matches(String requestMethod, String requestPath) {
        return method.equalsIgnoreCase(requestMethod) && path.equals(requestPath);
    }
}
