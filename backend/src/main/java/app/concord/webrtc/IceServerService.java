package app.concord.webrtc;

import app.concord.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Emissão de credenciais efêmeras de TURN.
 *
 * <p>O segredo do coturn <b>nunca</b> chega ao navegador. O que o cliente
 * recebe é um par usuário/senha derivado dele, válido por uma hora:
 *
 * <pre>
 *   username = &lt;expiração unix&gt;:&lt;id do usuário&gt;
 *   password = base64(HMAC-SHA1(segredo, username))
 * </pre>
 *
 * <p>É o mecanismo {@code use-auth-secret} do coturn, que valida a assinatura
 * sem precisar consultar nenhum banco de usuários. Credencial estática embutida
 * no frontend — que é o caminho comum em tutoriais — transformaria o TURN em
 * relay aberto assim que alguém abrisse o DevTools.
 *
 * <p>O id do usuário entra no username por rastreabilidade operacional: se um
 * relay for abusado, o log do coturn diz de quem era a credencial.
 */
@Service
public class IceServerService {

    private static final Logger log = LoggerFactory.getLogger(IceServerService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA1";

    private final AppProperties properties;

    public IceServerService(AppProperties properties) {
        this.properties = properties;
    }

    public IceDtos.IceConfig configFor(UUID userId) {
        AppProperties.Turn turn = properties.turn();
        List<IceDtos.IceServer> servers = new ArrayList<>();

        // STUN é anônimo por natureza: só devolve ao cliente qual é o seu IP
        // público. Não carrega mídia e não precisa de credencial.
        if (turn.stunUrl() != null && !turn.stunUrl().isBlank()) {
            servers.add(new IceDtos.IceServer(List.of(turn.stunUrl()), null, null));
        }

        if (turn.enabled() && turn.secret() != null && !turn.secret().isBlank()
                && turn.urls() != null && !turn.urls().isEmpty()) {
            long expiresAt = Instant.now().plus(turn.credentialTtl()).getEpochSecond();
            String username = expiresAt + ":" + userId;
            servers.add(new IceDtos.IceServer(turn.urls(), username, sign(username, turn.secret())));
        } else if (turn.enabled()) {
            log.warn("TURN habilitado sem segredo ou sem URLs configuradas; "
                    + "chamadas atrás de NAT simétrico vão falhar");
        }

        return new IceDtos.IceConfig(servers, Instant.now().plus(turn.credentialTtl()));
    }

    private static String sign(String username, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao assinar credencial de TURN", ex);
        }
    }
}
