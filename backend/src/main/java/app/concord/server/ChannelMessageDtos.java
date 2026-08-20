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
            UUID clientMessageId
    ) {
    }

    public record Response(UUID id, UUID channelId, UUID senderId, String body,
                           UUID clientMessageId, Instant createdAt) {
        static Response from(ChannelMessage message) {
            return new Response(message.getId(), message.getChannelId(), message.getSenderId(),
                    message.getBody(), message.getClientMessageId(), message.getCreatedAt());
        }
    }

    public record Page(List<Response> items) {
    }
}