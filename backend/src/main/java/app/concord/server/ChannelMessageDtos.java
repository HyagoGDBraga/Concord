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
            /** Vazio só é aceito com anexo. Verificado no serviço. */
            @Size(max = 4000, message = "A mensagem pode ter até 4000 caracteres")
            String body,
            @NotNull(message = "clientMessageId é obrigatório")
            UUID clientMessageId,
            /** Mensagem a que esta responde. Opcional. */
            UUID replyToId,
            /** Anexos já enviados, a serem presos a esta mensagem. */
            List<UUID> attachmentIds
    ) {
        /** Nunca nulo, para o serviço não precisar verificar. */
        public List<UUID> attachmentIds() {
            return attachmentIds == null ? List.of() : attachmentIds;
        }
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
            List<ReactionSummary> reactions,
            List<app.concord.attachment.AttachmentDtos.Response> attachments
    ) {
        /** Versão simples, sem o que depende de consulta extra. */
        static Response from(ChannelMessage message) {
            return new Response(message.getId(), message.getChannelId(), message.getSenderId(),
                    message.getBody(), message.getClientMessageId(), message.getCreatedAt(),
                    message.getEditedAt(), message.isDeleted(), message.isPinned(),
                    message.getPinnedAt(), null, List.of(), List.of(), List.of());
        }

        Response with(ReplyPreview reply, List<UUID> mentions, List<ReactionSummary> reactions) {
            return with(reply, mentions, reactions, attachments);
        }

        Response with(ReplyPreview reply, List<UUID> mentions,
                      List<ReactionSummary> reactions,
                      List<app.concord.attachment.AttachmentDtos.Response> anexos) {
            return new Response(id, channelId, senderId, body, clientMessageId, createdAt,
                    editedAt, deleted, pinned, pinnedAt, reply, mentions, reactions, anexos);
        }
    }

    public record Page(List<Response> items) {
    }
}
