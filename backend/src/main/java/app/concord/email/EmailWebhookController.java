package app.concord.email;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Webhook de bounce e reclamação.
 *
 * <p>Este é o único endpoint público não autenticado do sistema, e por isso o
 * mais sensível da fase. Três defesas:
 *
 * <ol>
 *   <li><b>Assinatura HMAC-SHA256</b> sobre o corpo bruto. Sem ela, qualquer
 *       pessoa na internet poderia suprimir o e-mail de qualquer usuário e
 *       impedi-lo de recuperar a senha — negação de serviço direcionada.</li>
 *   <li><b>Comparação em tempo constante</b>. Comparar assinaturas com
 *       {@code equals} vaza, pelo tempo de resposta, quantos bytes iniciais
 *       estavam certos, o que permite descobrir a assinatura byte a byte.</li>
 *   <li><b>Desligado por padrão.</b> Sem segredo configurado, o endpoint recusa
 *       tudo — não fica aberto por esquecimento.</li>
 * </ol>
 *
 * <p>O formato do corpo varia entre provedores. O mapeamento fica isolado em
 * {@code parseEvent}, que é o único ponto a mudar quando o fornecedor for
 * escolhido (D-07 segue pendente).
 */
@RestController
@RequestMapping("/webhooks/email")
public class EmailWebhookController {

    private static final Logger log = LoggerFactory.getLogger(EmailWebhookController.class);
    private static final String SIGNATURE_HEADER = "X-Concord-Signature";

    private final EmailSuppressionService suppressionService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public EmailWebhookController(EmailSuppressionService suppressionService,
                                  AppProperties properties,
                                  ObjectMapper objectMapper) {
        this.suppressionService = suppressionService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Recebe o corpo como {@code String}, não como objeto desserializado: a
     * assinatura é calculada sobre os bytes exatos que chegaram, e qualquer
     * reserialização mudaria espaços e ordem de campos.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(@RequestBody String rawBody,
                        @RequestHeader(value = SIGNATURE_HEADER, required = false)
                        String signature) {
        String secret = properties.webhook().emailSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("Webhook de e-mail recebido, mas nenhum segredo está configurado");
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (signature == null || !isSignatureValid(rawBody, signature, secret)) {
            log.warn("Webhook de e-mail com assinatura inválida foi descartado");
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        try {
            JsonNode event = objectMapper.readTree(rawBody);
            parseEvent(event);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Falha ao processar webhook de e-mail", ex);
            throw new ApiException(ErrorCode.MALFORMED_REQUEST);
        }
    }

    /**
     * Traduz o evento do provedor para a lista de supressão.
     *
     * <p>Formato genérico enquanto o fornecedor não é decidido:
     * <pre>{ "email": "...", "event": "hard_bounce|soft_bounce|complaint", "code": "..." }</pre>
     * Trocar de provedor significa reescrever este método, e só ele.
     */
    private void parseEvent(JsonNode event) {
        String email = event.path("email").asText(null);
        String type = event.path("event").asText("");
        String code = event.path("code").asText(null);

        if (email == null || email.isBlank()) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST);
        }

        EmailSuppression.Reason reason = switch (type.toLowerCase()) {
            case "hard_bounce", "bounce", "permanent" -> EmailSuppression.Reason.HARD_BOUNCE;
            case "soft_bounce", "deferred", "temporary" -> EmailSuppression.Reason.SOFT_BOUNCE;
            case "complaint", "spam" -> EmailSuppression.Reason.COMPLAINT;
            default -> null;
        };

        // Evento de entrega bem-sucedida ou abertura não interessa: registrar
        // isso seria construir rastreamento de comportamento.
        if (reason == null) {
            return;
        }
        suppressionService.suppress(email, reason, code);
    }

    private boolean isSignatureValid(String body, String provided, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(
                    mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

            // Tempo constante: MessageDigest.isEqual não retorna cedo na
            // primeira diferença.
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    provided.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }
}
