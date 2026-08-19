package app.concord.contact;

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

/**
 * Contatos e bloqueio.
 *
 * <p>Toda rota opera a partir do usuário autenticado — não há parâmetro que
 * permita agir em nome de outra pessoa.
 */
@RestController
@RequestMapping("/contacts")
public class ContactController {

    private final ContactService contactService;
    private final AccountService accountService;

    public ContactController(ContactService contactService, AccountService accountService) {
        this.contactService = contactService;
        this.accountService = accountService;
    }

    @GetMapping
    public ContactDtos.ContactsOverview list(
            @AuthenticationPrincipal ConcordUserDetails principal) {
        return contactService.overview(me(principal));
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public void request(@AuthenticationPrincipal ConcordUserDetails principal,
                        @Valid @RequestBody ContactDtos.CreateContactRequest request) {
        contactService.request(me(principal), request.username());
    }

    @PostMapping("/requests/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal ConcordUserDetails principal,
                       @PathVariable UUID id) {
        contactService.accept(me(principal), id);
    }

    /** Recusa um pedido recebido ou cancela um enviado. */
    @DeleteMapping("/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineOrCancel(@AuthenticationPrincipal ConcordUserDetails principal,
                                @PathVariable UUID id) {
        contactService.declineOrCancel(me(principal), id);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal ConcordUserDetails principal,
                       @PathVariable UUID userId) {
        contactService.remove(me(principal), userId);
    }

    @PostMapping("/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal ConcordUserDetails principal,
                      @PathVariable UUID userId) {
        contactService.block(me(principal), userId);
    }

    @DeleteMapping("/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal ConcordUserDetails principal,
                        @PathVariable UUID userId) {
        contactService.unblock(me(principal), userId);
    }

    private User me(ConcordUserDetails principal) {
        return accountService.requireById(principal.id());
    }
}
