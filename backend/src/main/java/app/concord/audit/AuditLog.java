package app.concord.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Registro de auditoria.
 *
 * <p>Minimização: sem user-agent, sem identificador de sessão e sem qualquer
 * conteúdo privado. O {@code metadata} aceita apenas valores não pessoais —
 * motivos administrativos, contadores, códigos. E-mail, senha, token e texto de
 * mensagem nunca entram aqui.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditOutcome outcome;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /**
     * Rótulo do ator no momento do evento. Preservado independentemente da conta
     * e substituído por pseudônimo quando a conta é anonimizada — o vínculo entre
     * eventos permanece por {@code actorUserId}, sem manter o identificador
     * pessoal legível.
     */
    @Column(name = "actor_label")
    private String actorLabel;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "ip_address")
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = Map.of();

    protected AuditLog() {
    }

    public AuditLog(AuditCategory category, AuditAction action, AuditOutcome outcome,
                    UUID actorUserId, String actorLabel, UUID targetUserId,
                    String ipAddress, Map<String, Object> metadata) {
        this.category = category;
        this.action = action;
        this.outcome = outcome;
        this.actorUserId = actorUserId;
        this.actorLabel = actorLabel;
        this.targetUserId = targetUserId;
        this.ipAddress = ipAddress;
        this.metadata = metadata == null ? Map.of() : metadata;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
