package app.concord.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChannelMessageDtos {

    private ChannelMessageDtos() {
    }

    public record SendRequest(
            @NotBlank(message = "A mensagem não pode ser vazia")
            @Size(max = 4000, message = "A mensagem pode ter até 4000 caracteres")
            String body,
            @NotNull(message = "clientMessageId é obrigatório")
            UUID clientMessageId,
            /** Mensagem a que esta responde. Opcional. */
            UUID replyToId
    ) {
    }

    public record EditRequest(
            @NotBlank(message = "A mensagem não pode ser vazia")
            @Size(max = 4000)
            String body
    ) {
    }

    public record ReactRequest(
            @NotBlank(message = "Informe o emoji")
            @Size(max = 32)
            String emoji
    ) {
    }

    /**
     * Reação agregada.
     *
     * <p>{@code mine} evita que o cliente precise procurar o próprio id dentro
     * de {@code userIds} a cada renderização, em cada emoji, de cada mensagem.
     */
    public record ReactionSummary(String emoji, int count, boolean mine, List<UUID> userIds) {
    }

    /** Prévia da mensagem respondida, embutida na resposta. */
    public record ReplyPreview(UUID id, UUID senderId, String excerpt, boolean deleted) {
    }

    public record Response(
            UUID id,
            UUID channelId,
            UUID senderId,
            String body,
            UUID clientMessageId,
            Instant createdAt,
            Instant editedAt,
            boolean deleted,
            boolean pinned,
            Instant pinnedAt,
            ReplyPreview replyTo,
            List<UUID> mentionedUserIds,
            List<ReactionSummary> reactions
    ) {
        /** Versão simples, sem o que depende de consulta extra. */
        static Response from(ChannelMessage message) {
            return new Response(message.getId(), message.getChannelId(), message.getSenderId(),
                    message.getBody(), message.getClientMessageId(), message.getCreatedAt(),
                    message.getEditedAt(), message.isDeleted(), message.isPinned(),
                    message.getPinnedAt(), null, List.of(), List.of());
        }

        Response with(ReplyPreview reply, List<UUID> mentions, List<ReactionSummary> reactions) {
            return new Response(id, channelId, senderId, body, clientMessageId, createdAt,
                    editedAt, deleted, pinned, pinnedAt, reply, mentions, reactions);
        }
    }

    public record Page(List<Response> items) {
    }
}
