package app.concord.server;

import app.concord.auth.ConcordUserDetails;
import app.concord.user.AccountService;
import app.concord.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ServerInviteController {

    private final ServerInviteService inviteService;
    private final AccountService accountService;

    public ServerInviteController(ServerInviteService inviteService, AccountService accountService) {
        this.inviteService = inviteService;
        this.accountService = accountService;
    }

    @PostMapping("/servers/{serverId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public ServerInviteDtos.CreatedResponse create(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID serverId,
            @Valid @RequestBody ServerInviteDtos.CreateRequest request) {
        return inviteService.create(me(principal), serverId, request);
    }

    @GetMapping("/server-invites")
    public ServerInviteDtos.PendingPage pending(
            @AuthenticationPrincipal ConcordUserDetails principal) {
        return inviteService.pending(me(principal));
    }

    @PostMapping("/server-invites/{inviteId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal ConcordUserDetails principal,
                       @PathVariable UUID inviteId) {
        inviteService.accept(me(principal), inviteId);
    }

    @DeleteMapping("/server-invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal ConcordUserDetails principal,
                        @PathVariable UUID inviteId) {
        inviteService.decline(me(principal), inviteId);
    }

    private User me(ConcordUserDetails principal) {
        return accountService.requireById(principal.id());
    }
}