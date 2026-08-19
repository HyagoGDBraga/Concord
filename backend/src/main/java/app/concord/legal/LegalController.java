package app.concord.legal;

import app.concord.auth.ConcordUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Consulta e registro de aceite dos documentos legais. */
@RestController
@RequestMapping("/legal")
public class LegalController {

    private final ConsentService consentService;

    public LegalController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping("/consents")
    public LegalDtos.ConsentStatus status(
            @AuthenticationPrincipal ConcordUserDetails principal) {
        return consentService.statusOf(principal.id());
    }

    @PostMapping("/consents")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal ConcordUserDetails principal,
                       @Valid @RequestBody LegalDtos.AcceptConsentRequest request) {
        consentService.accept(principal.id(), request.document(), request.version());
    }

    /** Histórico de aceites do próprio titular — parte do direito de acesso. */
    @GetMapping("/consents/history")
    public LegalDtos.ConsentHistory history(
            @AuthenticationPrincipal ConcordUserDetails principal) {
        return consentService.historyOf(principal.id());
    }
}
