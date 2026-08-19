package app.concord.email;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Lista de supressão de e-mail.
 *
 * <p>Fecha a lacuna registrada na decisão D-07: SMTP entrega a mensagem mas não
 * devolve status. Sem esta lista, um endereço inválido receberia tentativas
 * indefinidamente — o que derruba a reputação do domínio e leva o provedor
 * transacional a bloquear a conta inteira.
 */
@Service
public class EmailSuppressionService {

    private static final Logger log = LoggerFactory.getLogger(EmailSuppressionService.class);

    private final EmailSuppressionRepository repository;
    private final AuditService auditService;

    public EmailSuppressionService(EmailSuppressionRepository repository,
                                   AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public boolean isSuppressed(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }
        return repository.existsByEmailHash(hash(email));
    }

    @Transactional
    public void suppress(String email, EmailSuppression.Reason reason, String providerCode) {
        String emailHash = hash(email);
        if (repository.existsByEmailHash(emailHash)) {
            return;
        }
        repository.save(new EmailSuppression(emailHash, reason, providerCode));

        // O e-mail não entra na auditoria; o motivo, sim. Registrar o endereço
        // aqui recriaria em outra tabela o cadastro que a supressão evita.
        auditService.privacy(AuditAction.EMAIL_SUPPRESSED, null, "system", null,
                Map.of("reason", reason.name()));

        log.info("Endereço suprimido por {}", reason);
    }

    /** SHA-256 do endereço normalizado. */
    public static String hash(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(
                    digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
        }
    }
}
