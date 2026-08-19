package app.concord.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem de uma conversa.
 *
 * <p>Conteúdo privado. Nenhum serviço do pacote {@code admin} referencia esta
 * classe, e essa ausência é o controle de privacidade da decisão D-04.
 *
 * <p>Apagar não remove a linha: zera o corpo e marca {@code deletedAt}. Remover
 * a linha abriria buraco na ordenação da conversa do interlocutor, e a mensagem
 * também é histórico dele.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column
    private String body;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Message() {
    }

    public Message(UUID conversationId, UUID senderId, String body, UUID clientMessageId) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.body = body;
        this.clientMessageId = clientMessageId;
        this.createdAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void edit(String newBody) {
        this.body = newBody;
        this.editedAt = Instant.now();
    }

    public void softDelete() {
        this.body = null;
        this.deletedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
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

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
