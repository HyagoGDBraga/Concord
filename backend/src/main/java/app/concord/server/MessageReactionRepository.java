package app.concord.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageReactionRepository
        extends JpaRepository<MessageReaction, MessageReaction.ReactionId> {

    List<MessageReaction> findByIdMessageId(UUID messageId);

    /**
     * Reações de várias mensagens de uma vez.
     *
     * <p>Uma consulta por mensagem transformaria o carregamento do canal em
     * cinquenta idas ao banco — o problema N+1 clássico.
     */
    @Query("SELECT r FROM MessageReaction r WHERE r.id.messageId IN :messageIds")
    List<MessageReaction> findByMessageIds(@Param("messageIds") List<UUID> messageIds);
}
