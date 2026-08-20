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
 * Reação de uma pessoa a uma mensagem.
 *
 * <p>A chave primária composta (mensagem, pessoa, emoji) é a própria regra de
 * negócio: reagir duas vezes com o mesmo emoji é a mesma reação, não duas. O
 * banco recusa a duplicata sem que o serviço precise verificar antes — o que
 * elimina a corrida entre dois cliques rápidos.
 */
@Entity
@Table(name = "concord_message_reactions")
public class MessageReaction {

    @Embeddable
    public record ReactionId(
            @Column(name = "message_id") UUID messageId,
            @Column(name = "user_id") UUID userId,
            @Column(name = "emoji") String emoji
    ) implements Serializable {
    }

    @EmbeddedId
    private ReactionId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MessageReaction() {
    }

    public MessageReaction(UUID messageId, UUID userId, String emoji) {
        this.id = new ReactionId(messageId, userId, emoji);
        this.createdAt = Instant.now();
    }

    public ReactionId getId() {
        return id;
    }

    public UUID getMessageId() {
        return id.messageId();
    }

    public UUID getUserId() {
        return id.userId();
    }

    public String getEmoji() {
        return id.emoji();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
