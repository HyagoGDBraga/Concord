package app.concord.server;

import app.concord.auth.ConcordUserDetails;
import app.concord.user.AccountService;
import app.concord.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/channels/{channelId}/messages")
public class ChannelMessageController {

    private final ChannelMessageService messageService;
    private final AccountService accountService;

    public ChannelMessageController(ChannelMessageService messageService,
                                    AccountService accountService) {
        this.messageService = messageService;
        this.accountService = accountService;
    }

    @GetMapping
    public ChannelMessageDtos.Page history(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID channelId) {
        return messageService.history(me(principal), channelId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelMessageDtos.Response send(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID channelId,
            @Valid @RequestBody ChannelMessageDtos.SendRequest request) {
        return messageService.send(me(principal), channelId, request);
    }

    private User me(ConcordUserDetails principal) {
        return accountService.requireById(principal.id());
    }

    /** Mensagens fixadas do canal. */
    @GetMapping("/pinned")
    public ChannelMessageDtos.Page pinned(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID channelId) {
        return messageService.pinned(me(principal), channelId);
    }

    @PatchMapping("/{messageId}")
    public ChannelMessageDtos.Response edit(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @Valid @RequestBody ChannelMessageDtos.EditRequest request) {
        return messageService.edit(me(principal), messageId, request);
    }

    @DeleteMapping("/{messageId}")
    public ChannelMessageDtos.Response delete(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID channelId,
            @PathVariable UUID messageId) {
        return messageService.delete(me(principal), messageId);
    }

    /** Fixa ou desafixa. Alternância num endpoint só: o estado é binário. */
    @PostMapping("/{messageId}/pin")
    public ChannelMessageDtos.Response togglePin(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID channelId,
            @PathVariable UUID messageId) {
        return messageService.togglePin(me(principal), messageId);
    }

    /** Adiciona ou remove a reação, conforme já exista. */
    @PostMapping("/{messageId}/reactions")
    public ChannelMessageDtos.Response react(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @Valid @RequestBody ChannelMessageDtos.ReactRequest request) {
        return messageService.react(me(principal), messageId, request.emoji().trim());
    }
}
