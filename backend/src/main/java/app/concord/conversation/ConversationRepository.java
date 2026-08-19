package app.concord.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByDirectKey(String directKey);

    /**
     * Conversas em que o usuário participa, mais recentes primeiro.
     *
     * <p>O JOIN com participants é o que garante que ninguém receba uma conversa
     * de que não faz parte — a autorização está na consulta, não em um filtro
     * posterior que alguém possa esquecer.
     */
    @Query("""
            SELECT c FROM Conversation c
            JOIN ConversationParticipant p ON p.id.conversationId = c.id
            WHERE p.id.userId = :userId
            ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC
            """)
    List<Conversation> findAllOf(@Param("userId") UUID userId);
}
