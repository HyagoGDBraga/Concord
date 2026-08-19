package app.concord.message;

import app.concord.auth.ConcordUserDetails;
import app.concord.user.AccountService;
import app.concord.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Edição e exclusão de mensagem — sempre pelo próprio autor. */
@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;
    private final AccountService accountService;

    public MessageController(MessageService messageService, AccountService accountService) {
        this.messageService = messageService;
        this.accountService = accountService;
    }

    @PatchMapping("/{id}")
    public MessageDtos.MessageResponse edit(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody MessageDtos.EditMessageRequest request) {
        return MessageDtos.MessageResponse.from(
                messageService.edit(me(principal), id, request.body()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal ConcordUserDetails principal,
                       @PathVariable UUID id) {
        messageService.delete(me(principal), id);
    }

    private User me(ConcordUserDetails principal) {
        return accountService.requireById(principal.id());
    }
}
