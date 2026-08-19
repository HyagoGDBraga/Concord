package app.concord.call;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de uma chamada.
 *
 * <p>Guarda apenas o que o próprio usuário vê no histórico. SDP, candidatos ICE
 * e mídia não passam por aqui — o primeiro descreveria o dispositivo, o segundo
 * a topologia de rede, e o terceiro o servidor nem chega a ver.
 */
@Entity
@Table(name = "calls")
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "caller_id", nullable = false)
    private UUID callerId;

    @Column(name = "callee_id", nullable = false)
    private UUID calleeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallStatus status = CallStatus.RINGING;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason")
    private CallEndReason endReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected Call() {
    }

    public Call(UUID conversationId, UUID callerId, UUID calleeId, CallType type) {
        this.conversationId = conversationId;
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.type = type;
        this.status = CallStatus.RINGING;
        this.createdAt = Instant.now();
    }

    public void answer() {
        this.status = CallStatus.ACTIVE;
        this.answeredAt = Instant.now();
    }

    public void end(CallEndReason reason) {
        this.status = CallStatus.ENDED;
        this.endReason = reason;
        this.endedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == CallStatus.RINGING || status == CallStatus.ACTIVE;
    }

    public boolean involves(UUID userId) {
        return callerId.equals(userId) || calleeId.equals(userId);
    }

    /** O outro lado da chamada, visto por {@code userId}. */
    public UUID otherSide(UUID userId) {
        return callerId.equals(userId) ? calleeId : callerId;
    }

    /** Duração da conversa em si; zero se nunca foi atendida. */
    public Duration duration() {
        if (answeredAt == null || endedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(answeredAt, endedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getCallerId() {
        return callerId;
    }

    public UUID getCalleeId() {
        return calleeId;
    }

    public CallType getType() {
        return type;
    }

    public CallStatus getStatus() {
        return status;
    }

    public CallEndReason getEndReason() {
        return endReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
