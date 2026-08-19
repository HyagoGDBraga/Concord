package app.concord.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface EmailSuppressionRepository extends JpaRepository<EmailSuppression, UUID> {

    boolean existsByEmailHash(String emailHash);

    /**
     * Libera supressões temporárias vencidas.
     *
     * <p>Caixa cheia é problema passageiro; manter o endereço suprimido para
     * sempre por causa disso puniria o usuário por algo que já se resolveu.
     */
    @Modifying
    @Query("""
            DELETE FROM EmailSuppression s
            WHERE s.reason = app.concord.email.EmailSuppression$Reason.SOFT_BOUNCE
              AND s.createdAt < :cutoff
            """)
    int deleteExpiredSoftBounces(@Param("cutoff") Instant cutoff);
}
