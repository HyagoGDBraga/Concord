package app.concord.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, ConversationParticipant.ParticipantId> {

    List<ConversationParticipant> findByIdConversationId(UUID conversationId);

    @Query("""
            SELECT p FROM ConversationParticipant p
            WHERE p.id.conversationId IN :conversationIds
            """)
    List<ConversationParticipant> findByConversationIds(
            @Param("conversationIds") List<UUID> conversationIds);

    default Optional<ConversationParticipant> find(UUID conversationId, UUID userId) {
        return findById(new ConversationParticipant.ParticipantId(conversationId, userId));
    }
}
