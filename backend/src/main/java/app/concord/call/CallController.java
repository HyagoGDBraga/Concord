package app.concord.call;

import app.concord.auth.ConcordUserDetails;
import app.concord.common.dto.PageResponse;
import app.concord.user.AccountService;
import app.concord.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ciclo de vida da chamada.
 *
 * <p>Aqui ficam as transições que mudam estado persistido. A troca de SDP e
 * candidatos ICE não passa por HTTP: seriam dezenas de requisições nos
 * primeiros segundos de cada chamada, e nada disso precisa ser gravado.
 */
@RestController
@RequestMapping("/calls")
public class CallController {

    private final CallService callService;
    private final AccountService accountService;

    public CallController(CallService callService, AccountService accountService) {
        this.callService = callService;
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CallDtos.CallResponse start(@AuthenticationPrincipal ConcordUserDetails principal,
                                       @Valid @RequestBody CallDtos.StartCallRequest request) {
        User me = me(principal);
        return callService.toResponse(callService.start(me, request), me.getId());
    }

    @PostMapping("/{id}/accept")
    public CallDtos.CallResponse accept(@AuthenticationPrincipal ConcordUserDetails principal,
                                        @PathVariable UUID id) {
        User me = me(principal);
        return callService.toResponse(callService.accept(me, id), me.getId());
    }

    @PostMapping("/{id}/reject")
    public CallDtos.CallResponse reject(@AuthenticationPrincipal ConcordUserDetails principal,
                                        @PathVariable UUID id) {
        User me = me(principal);
        return callService.toResponse(callService.reject(me, id), me.getId());
    }

    @PostMapping("/{id}/end")
    public CallDtos.CallResponse end(@AuthenticationPrincipal ConcordUserDetails principal,
                                     @PathVariable UUID id) {
        User me = me(principal);
        return callService.toResponse(callService.end(me, id), me.getId());
    }

    /**
     * Chamada aberta do usuário, se houver.
     *
     * <p>É o que permite recuperar o estado ao recarregar a página no meio de
     * uma chamada — sem isso, a interface esqueceria e a outra pessoa
     * continuaria vendo "em chamada".
     */
    @GetMapping("/current")
    public CallDtos.CallResponse current(@AuthenticationPrincipal ConcordUserDetails principal) {
        return callService.currentCallOf(principal.id())
                .map(call -> callService.toResponse(call, principal.id()))
                .orElse(null);
    }

    /** Histórico de chamadas de uma conversa. */
    @GetMapping
    public PageResponse<CallDtos.CallResponse> history(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @RequestParam UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User me = me(principal);
        return PageResponse.from(
                callService.history(me, conversationId,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
                call -> callService.toResponse(call, me.getId()));
    }

    private User me(ConcordUserDetails principal) {
        return accountService.requireById(principal.id());
    }
}
