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
}