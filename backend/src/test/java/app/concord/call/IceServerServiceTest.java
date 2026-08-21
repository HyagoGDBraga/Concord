package app.concord.call;

import app.concord.config.AppProperties;
import app.concord.webrtc.IceDtos;
import app.concord.webrtc.IceServerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Credenciais efemeras de TURN.
 *
 * <p>O ponto mais sensivel da fase: se o segredo vazar para o navegador, o
 * servidor de relay vira aberto para qualquer um.
 */
class IceServerServiceTest {

    private static final String SECRET = "segredo-compartilhado-de-teste";

    private final IceServerService service = new IceServerService(properties(true));

    @Test
    @DisplayName("o segredo do coturn nunca aparece na resposta")
    void segredoNuncaVazaParaOCliente() {
        IceDtos.IceConfig config = service.configFor(UUID.randomUUID());

        assertThat(config.toString()).doesNotContain(SECRET);
        for (IceDtos.IceServer server : config.iceServers()) {
            // username e credential sao nulos no STUN, que e anonimo.
            // O que se afirma aqui e "nenhum campo contem o segredo" — e um
            // campo nulo satisfaz isso trivialmente.
            if (server.username() != null) {
                assertThat(server.username()).doesNotContain(SECRET);
            }
            if (server.credential() != null) {
                assertThat(server.credential()).doesNotContain(SECRET);
            }
        }
    }

    @Test
    @DisplayName("a credencial e o HMAC-SHA1 do username, como o coturn espera")
    void credencialConfereComOAlgoritmoDoCoturn() throws Exception {
        UUID userId = UUID.randomUUID();
        IceDtos.IceServer turn = turnServerDe(service.configFor(userId));

        // Reproduz exatamente o que o coturn faz ao validar.
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String esperado = Base64.getEncoder().encodeToString(
                mac.doFinal(turn.username().getBytes(StandardCharsets.UTF_8)));

        assertThat(turn.credential()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("o username carrega a expiracao e o id de quem pediu")
    void usernameTemExpiracaoEUsuario() {
        UUID userId = UUID.randomUUID();
        IceDtos.IceServer turn = turnServerDe(service.configFor(userId));

        String[] partes = turn.username().split(":", 2);
        long expiracao = Long.parseLong(partes[0]);

        assertThat(partes[1]).isEqualTo(userId.toString());
        assertThat(Instant.ofEpochSecond(expiracao)).isAfter(Instant.now());
        assertThat(Instant.ofEpochSecond(expiracao))
                .isBefore(Instant.now().plus(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("usuarios diferentes recebem credenciais diferentes")
    void credenciaisSaoPorUsuario() {
        IceDtos.IceServer a = turnServerDe(service.configFor(UUID.randomUUID()));
        IceDtos.IceServer b = turnServerDe(service.configFor(UUID.randomUUID()));

        assertThat(a.credential()).isNotEqualTo(b.credential());
    }

    @Test
    @DisplayName("o STUN e anonimo: vai sem usuario e sem credencial")
    void stunSemCredencial() {
        IceDtos.IceConfig config = service.configFor(UUID.randomUUID());

        IceDtos.IceServer stun = config.iceServers().stream()
                .filter(server -> server.urls().get(0).startsWith("stun:"))
                .findFirst()
                .orElseThrow();

        assertThat(stun.username()).isNull();
        assertThat(stun.credential()).isNull();
    }

    @Test
    @DisplayName("com TURN desligado, so o STUN e devolvido")
    void semTurnConfigurado() {
        IceDtos.IceConfig config =
                new IceServerService(properties(false)).configFor(UUID.randomUUID());

        assertThat(config.iceServers()).hasSize(1);
        assertThat(config.iceServers().get(0).urls().get(0)).startsWith("stun:");
    }

    private IceDtos.IceServer turnServerDe(IceDtos.IceConfig config) {
        return config.iceServers().stream()
                .filter(server -> server.urls().get(0).startsWith("turn:"))
                .findFirst()
                .orElseThrow();
    }

    private static AppProperties properties(boolean turnEnabled) {
        return new AppProperties(
                "http://localhost", true, "", Duration.ofDays(7),
                new AppProperties.Mail("teste@concord.local", "Concord"),
                new AppProperties.Login(5, Duration.ofMinutes(1), Duration.ofMinutes(15)),
                new AppProperties.Turn(turnEnabled,
                        "stun:stun.exemplo.test:3478",
                        List.of("turn:turn.exemplo.test:3478?transport=udp"),
                        SECRET,
                        Duration.ofHours(1)),
                // Acrescentados na Fase 7. Irrelevantes para este teste, mas o
                // construtor canonico do record exige todos.
                new AppProperties.Legal("2026-01", "2026-01"),
                new AppProperties.Webhook(""),
                new AppProperties.Storage(System.getProperty("java.io.tmpdir")));
    }
}
