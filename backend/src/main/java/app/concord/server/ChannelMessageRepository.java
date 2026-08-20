package app.concord.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, UUID> {

    Optional<ChannelMessage> findByChannelIdAndClientMessageId(UUID channelId,
                                                                UUID clientMessageId);

    @Query("""
            SELECT m FROM ChannelMessage m
            WHERE m.channelId = :channelId
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<ChannelMessage> findHistory(@Param("channelId") UUID channelId,
                                     org.springframework.data.domain.Pageable pageable);

    /** Mensagens fixadas do canal, das mais recentes para as mais antigas. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT m FROM ChannelMessage m
            WHERE m.channelId = :channelId AND m.pinnedAt IS NOT NULL
            ORDER BY m.pinnedAt DESC
            """)
    java.util.List<ChannelMessage> findPinned(
            @org.springframework.data.repository.query.Param("channelId") java.util.UUID channelId);

    /** Carrega várias mensagens de uma vez, para montar as prévias de resposta. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT m FROM ChannelMessage m WHERE m.id IN :ids")
    java.util.List<ChannelMessage> findAllByIdIn(
            @org.springframework.data.repository.query.Param("ids") java.util.List<java.util.UUID> ids);
}
