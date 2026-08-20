package app.concord.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import app.concord.user.UserDtos;

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

    public record AddMemberRequest(
            @NotBlank(message = "Informe o username")
            @Size(max = 20)
            String username
    ) {
    }

        public record UpdateMemberRoleRequest(
            @Pattern(regexp = "ADMIN|MEMBER", message = "Cargo inválido")
            String role
        ) {
        }

    public record MemberResponse(UserDtos.PublicUserResponse user, String role,
                                 Instant joinedAt) {
        static MemberResponse from(ServerMember member, app.concord.user.User user) {
            return new MemberResponse(UserDtos.PublicUserResponse.from(user),
                    member.getRole(), member.getCreatedAt());
        }
    }
}