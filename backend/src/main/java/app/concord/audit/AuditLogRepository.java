package app.concord.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:category IS NULL OR a.category = :category)
              AND (:action IS NULL OR a.action = :action)
              AND (:userId IS NULL OR a.actorUserId = :userId OR a.targetUserId = :userId)
              AND (CAST(:from AS timestamp) IS NULL OR a.createdAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(@Param("category") AuditCategory category,
                          @Param("action") AuditAction action,
                          @Param("userId") UUID userId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);

    /** Anula o IP de registros antigos, mantendo o restante do evento. */
    @Modifying
    @Query("UPDATE AuditLog a SET a.ipAddress = NULL WHERE a.createdAt < :cutoff AND a.ipAddress IS NOT NULL")
    int scrubIpBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.category = :category AND a.createdAt < :cutoff")
    int deleteByCategoryBefore(@Param("category") AuditCategory category,
                               @Param("cutoff") Instant cutoff);

    /** Substitui o rótulo do ator e do alvo quando a conta é anonimizada. */
    @Modifying
    @Query("UPDATE AuditLog a SET a.actorLabel = :pseudonym WHERE a.actorUserId = :userId")
    int pseudonymizeActorLabel(@Param("userId") UUID userId,
                               @Param("pseudonym") String pseudonym);
}
