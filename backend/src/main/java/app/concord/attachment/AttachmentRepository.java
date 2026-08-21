package app.concord.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByMessageId(UUID messageId);

    @Query("SELECT a FROM Attachment a WHERE a.messageId IN :messageIds")
    List<Attachment> findByMessageIds(@Param("messageIds") List<UUID> messageIds);

    /** Vencidos, em lotes: um expurgo não deve carregar tudo de uma vez. */
    @Query("""
            SELECT a FROM Attachment a
            WHERE a.expiresAt IS NOT NULL AND a.expiresAt < :agora
            ORDER BY a.expiresAt
            """)
    List<Attachment> findExpired(@Param("agora") Instant agora,
                                 org.springframework.data.domain.Pageable pageable);

    /**
     * Outro anexo com o mesmo conteúdo.
     *
     * <p>Sustenta a deduplicação: se os bytes já estão em disco, o novo registro
     * aponta para o mesmo arquivo em vez de gravá-lo de novo.
     */
    @Query("""
            SELECT a FROM Attachment a
            WHERE a.checksum = :checksum AND a.sizeBytes = :size
            ORDER BY a.createdAt
            """)
    List<Attachment> findByChecksum(@Param("checksum") String checksum,
                                    @Param("size") long size);

    /** Quantos registros ainda apontam para um arquivo em disco. */
    @Query("SELECT count(a) FROM Attachment a WHERE a.storageKey = :key")
    long countByStorageKey(@Param("key") String key);

    @Query("SELECT coalesce(sum(a.sizeBytes), 0) FROM Attachment a")
    long totalBytes();
}
