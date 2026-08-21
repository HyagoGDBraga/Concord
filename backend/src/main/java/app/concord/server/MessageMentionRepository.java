package app.concord.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageMentionRepository
        extends JpaRepository<MessageMention, MessageMention.MentionId> {

    @Query("SELECT m FROM MessageMention m WHERE m.id.messageId IN :messageIds")
    List<MessageMention> findByMessageIds(@Param("messageIds") List<UUID> messageIds);

    @Query("""
            SELECT m.id.messageId FROM MessageMention m
            WHERE m.id.userId = :userId
            ORDER BY m.createdAt DESC
            """)
    List<UUID> findRecentMessageIdsMentioning(@Param("userId") UUID userId);
}
