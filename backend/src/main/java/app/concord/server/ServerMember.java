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
@Table(name = "concord_server_members")
public class ServerMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "server_id", nullable = false)
    private UUID serverId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ServerMember() {
    }

    public ServerMember(UUID serverId, UUID userId, String role) {
        this.serverId = serverId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getServerId() {
        return serverId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }
}