package app.concord.privacy;

import app.concord.support.AbstractIntegrationTest;
import app.concord.support.TestApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Webhook de bounce — o unico endpoint publico nao autenticado do sistema.
 *
 * <p>Se a assinatura nao fosse verificada, qualquer pessoa poderia suprimir o
 * e-mail de qualquer usuario e impedi-lo de recuperar a senha.
 */
class EmailWebhookIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "segredo-de-webhook-para-teste";

    @Test
    @DisplayName("evento com assinatura valida e aceito")
    void aceitaAssinaturaValida() throws Exception {
        String corpo = """
                {"email":"invalido@exemplo.test","event":"hard_bounce","code":"550"}""";

        assertThat(enviar(corpo, assinar(corpo)).statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("assinatura invalida e recusada com 404, sem revelar o endpoint")
    void recusaAssinaturaInvalida() throws Exception {
        String corpo = """
                {"email":"vitima@exemplo.test","event":"hard_bounce"}""";

        // Um atacante que quisesse suprimir o e-mail de alguem chegaria aqui.
        assertThat(enviar(corpo, "0".repeat(64)).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("requisicao sem assinatura e recusada")
    void recusaSemAssinatura() throws Exception {
        String corpo = """
                {"email":"vitima@exemplo.test","event":"complaint"}""";

        assertThat(enviar(corpo, null).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("assinatura valida de um corpo diferente nao serve")
    void assinaturaNaoPodeSerReaproveitada() throws Exception {
        String original = """
                {"email":"a@exemplo.test","event":"soft_bounce"}""";
        String adulterado = """
                {"email":"vitima@exemplo.test","event":"hard_bounce"}""";

        assertThat(enviar(adulterado, assinar(original)).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("o endpoint nao exige sessao, mas tambem nao aceita uma")
    void naoDependeDeAutenticacao() {
        TestApiClient anonimo = new TestApiClient(port, objectMapper);
        // Sem assinatura, e 404 mesmo sem autenticacao — nao e 401.
        assertThat(anonimo.post("/webhooks/email",
                java.util.Map.of("email", "x@y.test", "event", "bounce")).status())
                .isEqualTo(404);
    }

    private HttpResponse<String> enviar(String corpo, String assinatura) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhooks/email"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo));

        if (assinatura != null) {
            builder.header("X-Concord-Signature", assinatura);
        }
        return HttpClient.newHttpClient()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String assinar(String corpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
    }
}
