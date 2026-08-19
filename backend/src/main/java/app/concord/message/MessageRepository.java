package app.concord.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findByConversationIdAndClientMessageId(UUID conversationId,
                                                             UUID clientMessageId);

    /**
     * Página mais recente da conversa.
     *
     * <p>Paginação por keyset, não por offset: em uma conversa que recebe
     * mensagens enquanto o usuário rola, o offset desloca e mostra itens
     * repetidos ou pulados. O cursor {@code (created_at, id)} é estável.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findLatest(@Param("conversationId") UUID conversationId, Pageable pageable);

    /** Página anterior ao cursor informado (rolagem para o passado). */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
              AND (m.createdAt < :beforeAt
                   OR (m.createdAt = :beforeAt AND m.id < :beforeId))
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findBefore(@Param("conversationId") UUID conversationId,
                             @Param("beforeAt") Instant beforeAt,
                             @Param("beforeId") UUID beforeId,
                             Pageable pageable);

    /**
     * Mensagens posteriores ao cursor, em ordem cronológica.
     *
     * <p>É o que sustenta o polling da Fase 3 e continuará servindo na Fase 4
     * para preencher a lacuna após uma reconexão do WebSocket.
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
              AND (m.createdAt > :afterAt
                   OR (m.createdAt = :afterAt AND m.id > :afterId))
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<Message> findAfter(@Param("conversationId") UUID conversationId,
                            @Param("afterAt") Instant afterAt,
                            @Param("afterId") UUID afterId,
                            Pageable pageable);

    @Query("""
            SELECT count(m) FROM Message m
            WHERE m.conversationId = :conversationId
              AND m.senderId <> :userId
              AND (CAST(:since AS timestamp) IS NULL OR m.createdAt > :since)
            """)
    long countUnread(@Param("conversationId") UUID conversationId,
                     @Param("userId") UUID userId,
                     @Param("since") Instant since);

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
            ORDER BY m.createdAt DESC, m.id DESC
            LIMIT 1
            """)
    Optional<Message> findLastOf(@Param("conversationId") UUID conversationId);
}
