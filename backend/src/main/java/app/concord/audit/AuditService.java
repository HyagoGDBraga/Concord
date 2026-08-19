package app.concord.audit;

import app.concord.common.request.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Escrita de eventos de auditoria.
 *
 * <p>Cada registro é gravado em transação própria ({@code REQUIRES_NEW}). Isso é
 * essencial: um login malsucedido lança exceção e desfaz a transação da
 * operação, mas o evento {@code LOGIN_FAILURE} precisa sobreviver — é justamente
 * o que se quer auditar.
 *
 * <p>Falha ao auditar nunca derruba a operação de negócio; é registrada em log.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditCategory category, AuditAction action, AuditOutcome outcome,
                       UUID actorUserId, String actorLabel, UUID targetUserId,
                       Map<String, Object> metadata) {
        try {
            // PRIVACY já nasce sem IP: é prova de atendimento a um direito do
            // titular e não precisa de dado de rede para cumprir essa função.
            String ip = category == AuditCategory.PRIVACY ? null : currentIp();
            repository.save(new AuditLog(category, action, outcome, actorUserId,
                    actorLabel, targetUserId, ip, metadata));
        } catch (Exception ex) {
            log.error("Falha ao gravar auditoria: {} {}", category, action, ex);
        }
    }

    public void security(AuditAction action, AuditOutcome outcome, UUID actorUserId,
                         String actorLabel, Map<String, Object> metadata) {
        record(AuditCategory.SECURITY, action, outcome, actorUserId, actorLabel, actorUserId, metadata);
    }

    public void admin(AuditAction action, AuditOutcome outcome, UUID adminId, String adminLabel,
                      UUID targetUserId, Map<String, Object> metadata) {
        record(AuditCategory.ADMIN, action, outcome, adminId, adminLabel, targetUserId, metadata);
    }

    public void privacy(AuditAction action, UUID actorUserId, String actorLabel,
                        UUID targetUserId, Map<String, Object> metadata) {
        record(AuditCategory.PRIVACY, action, AuditOutcome.SUCCESS, actorUserId,
                actorLabel, targetUserId, metadata);
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return ClientIp.of(request);
        }
        return null;
    }
}
