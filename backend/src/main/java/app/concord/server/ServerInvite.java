package app.concord.server;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "concord_server_invites")
public class ServerInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "server_id", nullable = false)
    private UUID serverId;

    @Column(name = "inviter_id", nullable = false)
    private UUID inviterId;

    @Column(name = "invitee_id", nullable = false)
    private UUID inviteeId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ServerInvite() {
    }

    public ServerInvite(UUID serverId, UUID inviterId, UUID inviteeId,
                        String tokenHash, Instant expiresAt) {
        this.serverId = serverId;
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isPending() {
        return acceptedAt == null && declinedAt == null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public void accept() {
        acceptedAt = Instant.now();
    }

    public void decline() {
        declinedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getServerId() { return serverId; }
    public UUID getInviterId() { return inviterId; }
    public UUID getInviteeId() { return inviteeId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}