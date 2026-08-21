package app.concord.server;

import app.concord.auth.ConcordUserDetails;
import app.concord.user.AccountService;
import app.concord.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final app.concord.attachment.ServerIconService serverIconService;
    private final AccountService accountService;

    public ServerController(ServerService serverService, AccountService accountService,
                            app.concord.attachment.ServerIconService serverIconService) {
        this.serverService = serverService;
        this.serverIconService = serverIconService;
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

    @GetMapping("/{serverId}/members")
    public List<ServerDtos.MemberResponse> members(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID serverId) {
        return serverService.members(me(principal), serverId);
    }

    @PostMapping("/{serverId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ServerDtos.MemberResponse addMember(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID serverId,
            @Valid @RequestBody ServerDtos.AddMemberRequest request) {
        return serverService.addMember(me(principal), serverId, request);
    }

    @DeleteMapping("/{serverId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID serverId,
            @PathVariable UUID userId) {
        serverService.removeMember(me(principal), serverId, userId);
    }

    @PatchMapping("/{serverId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRole(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @Valid @RequestBody ServerDtos.UpdateMemberRoleRequest request) {
        serverService.updateRole(me(principal), serverId, userId, request);
    }

    private User me(ConcordUserDetails principal) {
        return accountService.requireById(principal.id());
    }

    /**
     * Troca o ícone do servidor.
     *
     * <p>Só dono e moderador. O ícone é a identidade visual do lugar; deixar
     * qualquer membro trocá-lo seria o mesmo que deixar qualquer um renomear o
     * servidor.
     */
    @PostMapping("/{serverId}/icon")
    public app.concord.attachment.AttachmentDtos.Response uploadIcon(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable java.util.UUID serverId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return serverIconService.replace(me(principal), serverId, file);
    }

    @DeleteMapping("/{serverId}/icon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeIcon(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable java.util.UUID serverId) {
        serverIconService.remove(me(principal), serverId);
    }
}
