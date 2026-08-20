package app.concord.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ServerDtos {

    private ServerDtos() {
    }

    public record CreateServerRequest(
            @NotBlank(message = "Informe o nome do servidor")
            @Size(max = 80, message = "O nome do servidor deve ter no máximo 80 caracteres")
            String name
    ) {
    }

    public record CreateChannelRequest(
            @NotBlank(message = "Informe o nome do canal")
            @Size(max = 80, message = "O nome do canal deve ter no máximo 80 caracteres")
            String name,
            String type
    ) {
    }

    public record ChannelResponse(UUID id, String name, String type, int position) {
        static ChannelResponse from(Channel channel) {
            return new ChannelResponse(channel.getId(), channel.getName(),
                    channel.getType(), channel.getPosition());
        }
    }

    public record ServerResponse(UUID id, String name, UUID ownerId, Instant createdAt,
                                 List<ChannelResponse> channels) {
        static ServerResponse from(Server server, List<Channel> channels) {
            return new ServerResponse(server.getId(), server.getName(), server.getOwnerId(),
                    server.getCreatedAt(), channels.stream().map(ChannelResponse::from).toList());
        }
    }
}