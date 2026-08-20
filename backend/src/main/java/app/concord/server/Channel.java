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
@Table(name = "concord_channels")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "server_id", nullable = false)
    private UUID serverId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Channel() {
    }

    public Channel(UUID serverId, String name, String type, int position) {
        this.serverId = serverId;
        this.name = name.trim();
        this.type = type;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getServerId() {
        return serverId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getPosition() {
        return position;
    }
}