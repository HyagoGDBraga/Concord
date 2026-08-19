package app.concord.token;

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
 * Token de uso único para uma ação do usuário.
 *
 * <p>Só o hash SHA-256 é persistido. O valor em texto puro existe apenas em
 * memória, no instante do envio do e-mail — quem obtiver o banco não consegue
 * usar um link de redefinição de senha.
 */
@Entity
@Table(name = "user_action_tokens")
public class UserActionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionTokenType action;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    /** Carga auxiliar não secreta. Hoje: o novo endereço em EMAIL_CHANGE. */
    @Column
    private String payload;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserActionToken() {
    }

    public UserActionToken(UUID userId, ActionTokenType action, String tokenHash,
                           String payload, Instant expiresAt) {
        this.userId = userId;
        this.action = action;
        this.tokenHash = tokenHash;
        this.payload = payload;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ActionTokenType getAction() {
        return action;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
