package app.concord.server;

import app.concord.auth.ConcordUserDetails;
import app.concord.user.AccountService;
import app.concord.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/servers")
public class ServerController {

    private final ServerService serverService;
    private final AccountService accountService;

    public ServerController(ServerService serverService, AccountService accountService) {
        this.serverService = serverService;
        this.accountService = accountService;
    }

    @GetMapping
    public List<ServerDtos.ServerResponse> list(
            @AuthenticationPrincipal ConcordUserDetails principal) {
        return serverService.list(me(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServerDtos.ServerResponse create(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @Valid @RequestBody ServerDtos.CreateServerRequest request) {
        return serverService.create(me(principal), request);
    }

    @GetMapping("/{serverId}/channels")
    public List<ServerDtos.ChannelResponse> channels(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID serverId) {
        return serverService.channels(me(principal), serverId);
    }

    @PostMapping("/{serverId}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ServerDtos.ChannelResponse createChannel(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID serverId,
            @Valid @RequestBody ServerDtos.CreateChannelRequest request) {
        return serverService.createChannel(me(principal), serverId, request);
    }

    private User me(ConcordUserDetails principal) {
        return accountService.requireById(principal.id());
    }
}