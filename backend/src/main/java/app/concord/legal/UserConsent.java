package app.concord.legal;

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
 * Aceite de um documento legal, em uma versão específica.
 *
 * <p>Imutável por construção — não há setter. Um aceite novo é uma linha nova.
 */
@Entity
@Table(name = "user_consents")
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalDocument document;

    @Column(nullable = false)
    private String version;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt = Instant.now();

    @Column(name = "ip_address")
    private String ipAddress;

    protected UserConsent() {
    }

    public UserConsent(UUID userId, LegalDocument document, String version, String ipAddress) {
        this.userId = userId;
        this.document = document;
        this.version = version;
        this.ipAddress = ipAddress;
        this.acceptedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LegalDocument getDocument() {
        return document;
    }

    public String getVersion() {
        return version;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
