package app.concord.presence;

import app.concord.auth.ConcordUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado inicial de presença.
 *
 * <p>Os eventos em tempo real contam as mudanças; este endpoint conta o ponto de
 * partida. Sem ele, quem acabou de abrir o aplicativo veria todo mundo offline
 * até alguém se conectar.
 *
 * <p>Devolve apenas contatos aceitos. Não há como consultar a presença de quem
 * não é contato.
 */
@RestController
@RequestMapping("/presence")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping
    public PresenceDtos.PresenceSnapshot snapshot(
            @AuthenticationPrincipal ConcordUserDetails principal) {
        return new PresenceDtos.PresenceSnapshot(
                presenceService.onlineContactsOf(principal.id()));
    }
}
