package app.concord.conversation;

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

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationType type = ConversationType.DIRECT;

    /** Chave canônica do par. Índice único garante uma conversa por dupla. */
    @Column(name = "direct_key")
    private String directKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    protected Conversation() {
    }

    public static Conversation direct(UUID a, UUID b) {
        Conversation conversation = new Conversation();
        conversation.type = ConversationType.DIRECT;
        conversation.directKey = directKeyFor(a, b);
        conversation.createdAt = Instant.now();
        return conversation;
    }

    public static String directKeyFor(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        return first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
    }

    public void touch(Instant when) {
        if (lastMessageAt == null || when.isAfter(lastMessageAt)) {
            this.lastMessageAt = when;
        }
    }

    public UUID getId() {
        return id;
    }

    public ConversationType getType() {
        return type;
    }

    public String getDirectKey() {
        return directKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
