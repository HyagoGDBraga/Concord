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

    /** Mensagem a que esta responde. Null quando nao e resposta. */
    @Column(name = "reply_to_id")
    private UUID replyToId;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "pinned_at")
    private Instant pinnedAt;

    @Column(name = "pinned_by")
    private UUID pinnedBy;

    protected ChannelMessage() {
    }

    public ChannelMessage(UUID channelId, UUID senderId, String body, UUID clientMessageId) {
        this(channelId, senderId, body, clientMessageId, null);
    }

    public ChannelMessage(UUID channelId, UUID senderId, String body, UUID clientMessageId,
                          UUID replyToId) {
        this.channelId = channelId;
        this.senderId = senderId;
        this.body = body == null ? "" : body.trim();
        this.clientMessageId = clientMessageId;
        this.replyToId = replyToId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isPinned() {
        return pinnedAt != null;
    }

    public void edit(String newBody) {
        this.body = newBody.trim();
        this.editedAt = Instant.now();
    }

    /**
     * Apaga sem remover a linha.
     *
     * <p>A linha permanece porque outras mensagens podem responder a esta, e
     * porque remover abriria buraco na ordenacao do canal para todo mundo.
     */
    public void softDelete() {
        this.body = null;
        this.deletedAt = Instant.now();
    }

    public void pin(UUID userId) {
        this.pinnedAt = Instant.now();
        this.pinnedBy = userId;
    }

    public void unpin() {
        this.pinnedAt = null;
        this.pinnedBy = null;
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

    public UUID getReplyToId() {
        return replyToId;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }

    public UUID getPinnedBy() {
        return pinnedBy;
    }
}