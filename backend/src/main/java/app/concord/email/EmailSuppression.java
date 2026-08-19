package app.concord.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Endereço para o qual o sistema deixou de enviar.
 *
 * <p>Guarda o hash, não o endereço. A lista só precisa responder "este e-mail
 * está suprimido?", e o hash responde isso sem manter um cadastro de endereços
 * de pessoas que nem têm conta aqui.
 */
@Entity
@Table(name = "email_suppressions")
public class EmailSuppression {

    public enum Reason {
        /** Endereço inexistente. Definitivo: não se tenta de novo. */
        HARD_BOUNCE,
        /** Caixa cheia, indisponibilidade temporária. Expira. */
        SOFT_BOUNCE,
        /** O destinatário marcou como spam. O mais grave para a reputação. */
        COMPLAINT,
        /** Supressão inserida manualmente. */
        MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email_hash", nullable = false)
    private String emailHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Reason reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "provider_code")
    private String providerCode;

    protected EmailSuppression() {
    }

    public EmailSuppression(String emailHash, Reason reason, String providerCode) {
        this.emailHash = emailHash;
        this.reason = reason;
        this.providerCode = providerCode;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public Reason getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getProviderCode() {
        return providerCode;
    }
}
