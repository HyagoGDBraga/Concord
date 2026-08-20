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
@Table(name = "concord_channel_messages")
public class ChannelMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false)
    private String body;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ChannelMessage() {
    }

    public ChannelMessage(UUID channelId, UUID senderId, String body, UUID clientMessageId) {
        this.channelId = channelId;
        this.senderId = senderId;
        this.body = body.trim();
        this.clientMessageId = clientMessageId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getBody() {
        return body;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}