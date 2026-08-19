package app.concord.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Participação de um usuário em uma conversa.
 *
 * <p>Esta tabela é a fronteira de autorização do chat: toda leitura e toda
 * escrita de mensagem verifica primeiro se existe a linha correspondente. Não
 * há caminho de acesso a mensagem que não passe por aqui.
 */
@Entity
@Table(name = "conversation_participants")
public class ConversationParticipant {

    @Embeddable
    public record ParticipantId(
            @Column(name = "conversation_id") UUID conversationId,
            @Column(name = "user_id") UUID userId
    ) implements Serializable {
    }

    @EmbeddedId
    private ParticipantId id;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    protected ConversationParticipant() {
    }

    public ConversationParticipant(UUID conversationId, UUID userId) {
        this.id = new ParticipantId(conversationId, userId);
        this.joinedAt = Instant.now();
    }

    public void markRead(UUID messageId, Instant readAt) {
        this.lastReadMessageId = messageId;
        this.lastReadAt = readAt;
    }

    public ParticipantId getId() {
        return id;
    }

    public UUID getConversationId() {
        return id.conversationId();
    }

    public UUID getUserId() {
        return id.userId();
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    public UUID getLastReadMessageId() {
        return lastReadMessageId;
    }
}
