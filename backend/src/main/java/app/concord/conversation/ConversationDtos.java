package app.concord.conversation;

import app.concord.user.UserDtos;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class ConversationDtos {

    private ConversationDtos() {
    }

    /**
     * Conversa como aparece na lista.
     *
     * <p>{@code lastMessagePreview} é o texto da última mensagem, e por isso é
     * conteúdo privado como qualquer outro — só chega a quem participa.
     */
    public record ConversationResponse(
            UUID id,
            UserDtos.PublicUserResponse peer,
            Instant createdAt,
            Instant lastMessageAt,
            String lastMessagePreview,
            long unreadCount,
            boolean peerBlocked,
            boolean stillContacts
    ) {
    }

    public record CreateConversationRequest(
            @NotNull(message = "Informe o usuário")
            UUID userId
    ) {
    }

    public record MarkReadRequest(
            @NotNull(message = "Informe a mensagem")
            UUID messageId
    ) {
    }
}
