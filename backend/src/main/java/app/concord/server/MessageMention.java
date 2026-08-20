package app.concord.server;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de que alguém foi mencionado em uma mensagem.
 *
 * <p>Persistido em vez de deduzido do texto na exibição. Duas razões: responder
 * "o que mencionou você" sem varrer todas as mensagens com {@code LIKE}; e
 * congelar o destinatário no momento do envio — se a pessoa mudar de nome
 * depois, a menção antiga continua apontando para ela.
 */
@Entity
@Table(name = "concord_message_mentions")
public class MessageMention {

    @Embeddable
    public record MentionId(
            @Column(name = "message_id") UUID messageId,
            @Column(name = "user_id") UUID userId
    ) implements Serializable {
    }

    @EmbeddedId
    private MentionId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MessageMention() {
    }

    public MessageMention(UUID messageId, UUID userId) {
        this.id = new MentionId(messageId, userId);
        this.createdAt = Instant.now();
    }

    public UUID getMessageId() {
        return id.messageId();
    }

    public UUID getUserId() {
        return id.userId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
