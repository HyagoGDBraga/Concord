package app.concord.legal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

    List<UserConsent> findByUserIdOrderByAcceptedAtDesc(UUID userId);

    @Query("""
            SELECT count(c) > 0 FROM UserConsent c
            WHERE c.userId = :userId
              AND c.document = :document
              AND c.version = :version
            """)
    boolean hasAccepted(@Param("userId") UUID userId,
                        @Param("document") LegalDocument document,
                        @Param("version") String version);

    /** Anula o IP de aceites antigos, preservando a prova do consentimento. */
    @Modifying
    @Query("UPDATE UserConsent c SET c.ipAddress = NULL "
            + "WHERE c.acceptedAt < :cutoff AND c.ipAddress IS NOT NULL")
    int scrubIpBefore(@Param("cutoff") Instant cutoff);
}
