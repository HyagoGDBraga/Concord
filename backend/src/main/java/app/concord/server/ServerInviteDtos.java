package app.concord.server;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ServerInviteDtos {

    private ServerInviteDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "Informe o username")
            @Size(max = 20)
            String username
    ) {
    }

    public record CreatedResponse(UUID id, UUID serverId, String serverName,
                                  String inviteeUsername, String token, Instant expiresAt) {
    }

    public record PendingResponse(UUID id, UUID serverId, String serverName,
                                  String inviterUsername, Instant expiresAt) {
    }

    public record PendingPage(List<PendingResponse> items) {
    }
}