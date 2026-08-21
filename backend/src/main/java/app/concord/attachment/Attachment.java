package app.concord.attachment;

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
 * Metadado de um arquivo. Os bytes ficam em disco, referenciados por
 * {@code storageKey}.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "uploader_id", nullable = false)
    private UUID uploaderId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttachmentPurpose purpose;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "channel_id")
    private UUID channelId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected Attachment() {
    }

    public Attachment(UUID uploaderId, String originalName, String storageKey,
                      String contentType, long sizeBytes, String checksum,
                      AttachmentPurpose purpose, UUID channelId) {
        this.uploaderId = uploaderId;
        this.originalName = originalName;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.purpose = purpose;
        this.channelId = channelId;
        this.createdAt = Instant.now();
        this.expiresAt = purpose.retention() == null
                ? null
                : this.createdAt.plus(purpose.retention());
    }

    /** Vincula o anexo à mensagem depois que ela é criada. */
    public void attachTo(UUID messageId) {
        this.messageId = messageId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUploaderId() {
        return uploaderId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public AttachmentPurpose getPurpose() {
        return purpose;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
