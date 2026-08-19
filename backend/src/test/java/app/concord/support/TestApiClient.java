package app.concord.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP de teste que se comporta como um navegador: guarda cookies entre
 * requisições e devolve o token CSRF no header a cada mutação.
 *
 * <p>Deliberadamente sem {@code TestRestTemplate}: o controle explícito dos
 * cookies é o que permite verificar que o id da sessão muda no login e que uma
 * sessão revogada deixa de valer.
 */
public class TestApiClient {

    public record Response(int status, String body, JsonNode json) {

        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        public String errorCode() {
            return json != null && json.has("code") ? json.get("code").asText() : null;
        }
    }

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, String> cookies = new HashMap<>();

    public TestApiClient(int port, ObjectMapper objectMapper) {
        this.baseUrl = "http://localhost:" + port + "/api";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Response get(String path) {
        return send("GET", path, null);
    }

    public Response post(String path, Object body) {
        return send("POST", path, body);
    }

    public Response patch(String path, Object body) {
        return send("PATCH", path, body);
    }

    public Response delete(String path, Object body) {
        return send("DELETE", path, body);
    }

    /** Valor atual do cookie de sessão, ou {@code null} se não houver sessão. */
    public String sessionCookie() {
        return cookies.get("concord_session");
    }

    public void clearCookies() {
        cookies.clear();
    }

    private Response send(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");

            if (!cookies.isEmpty()) {
                builder.header("Cookie", cookies.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse(""));
            }
            // O cliente devolve o token CSRF lido do cookie, como faz o
            // apiClient do frontend.
            String csrf = cookies.get("XSRF-TOKEN");
            if (csrf != null) {
                builder.header("X-XSRF-TOKEN", csrf);
            }

            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
            builder.method(method, publisher);

            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            storeCookies(response.headers().allValues("Set-Cookie"));

            JsonNode json = null;
            if (response.body() != null && !response.body().isBlank()) {
                try {
                    json = objectMapper.readTree(response.body());
                } catch (Exception ignored) {
                    // resposta sem corpo JSON
                }
            }
            return new Response(response.statusCode(), response.body(), json);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha na requisição de teste: " + method + " " + path, ex);
        }
    }

    private void storeCookies(List<String> setCookieHeaders) {
        for (String header : setCookieHeaders) {
            String pair = header.split(";", 2)[0];
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (value.isEmpty() || header.contains("Max-Age=0")) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }
}
