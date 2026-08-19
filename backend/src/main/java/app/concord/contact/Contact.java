package app.concord.contact;

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
 * Relação de contato entre duas pessoas — uma linha por par.
 *
 * <p>O {@code pairKey} é a chave canônica: o menor UUID primeiro, sempre. É ele
 * que impede que A→B e B→A existam simultaneamente como dois pedidos abertos,
 * garantido por índice único no banco em vez de por verificação na aplicação
 * (que teria condição de corrida entre dois pedidos simultâneos).
 */
@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactStatus status = ContactStatus.PENDING;

    @Column(name = "pair_key", nullable = false)
    private String pairKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected Contact() {
    }

    public Contact(UUID requesterId, UUID addresseeId) {
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
        this.pairKey = pairKeyFor(requesterId, addresseeId);
        this.status = ContactStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /** Chave canônica do par, independente de quem pediu. */
    public static String pairKeyFor(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        return first.compareTo(second) <= 0
                ? first + ":" + second
                : second + ":" + first;
    }

    public void accept() {
        this.status = ContactStatus.ACCEPTED;
        this.respondedAt = Instant.now();
    }

    public boolean isAccepted() {
        return status == ContactStatus.ACCEPTED;
    }

    /** O outro lado da relação, visto por {@code userId}. */
    public UUID otherSide(UUID userId) {
        return requesterId.equals(userId) ? addresseeId : requesterId;
    }

    public boolean involves(UUID userId) {
        return requesterId.equals(userId) || addresseeId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getAddresseeId() {
        return addresseeId;
    }

    public ContactStatus getStatus() {
        return status;
    }

    public String getPairKey() {
        return pairKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
