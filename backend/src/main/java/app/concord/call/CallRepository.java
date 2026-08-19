package app.concord.call;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallRepository extends JpaRepository<Call, UUID> {

    /**
     * Chamada aberta de que o usuário participa.
     *
     * <p>É a consulta que impede duas chamadas simultâneas para a mesma pessoa
     * e sustenta o sinal de ocupado.
     */
    @Query("""
            SELECT c FROM Call c
            WHERE (c.callerId = :userId OR c.calleeId = :userId)
              AND c.status IN (app.concord.call.CallStatus.RINGING,
                               app.concord.call.CallStatus.ACTIVE)
            ORDER BY c.createdAt DESC
            """)
    List<Call> findOpenOf(@Param("userId") UUID userId);

    default Optional<Call> findFirstOpenOf(UUID userId) {
        return findOpenOf(userId).stream().findFirst();
    }

    @Query("""
            SELECT c FROM Call c
            WHERE c.conversationId = :conversationId
            ORDER BY c.createdAt DESC
            """)
    Page<Call> findByConversation(@Param("conversationId") UUID conversationId,
                                  Pageable pageable);

    /** Convites que ninguém atendeu dentro do prazo. */
    @Query("""
            SELECT c FROM Call c
            WHERE c.status = app.concord.call.CallStatus.RINGING
              AND c.createdAt < :cutoff
            """)
    List<Call> findStaleRinging(@Param("cutoff") Instant cutoff);

    /** Chamadas em andamento há tempo demais — sinal de conexão perdida. */
    @Query("""
            SELECT c FROM Call c
            WHERE c.status = app.concord.call.CallStatus.ACTIVE
              AND c.answeredAt < :cutoff
            """)
    List<Call> findStaleActive(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM Call c WHERE c.createdAt < :cutoff")
    int deleteBefore(@Param("cutoff") Instant cutoff);
}
