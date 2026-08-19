package app.concord.webrtc;

import app.concord.auth.ConcordUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega das credenciais de ICE.
 *
 * <p>Exige autenticação: sem isso, qualquer pessoa na internet poderia obter
 * credenciais válidas e usar o TURN como relay de tráfego próprio, às custas da
 * banda do servidor.
 */
@RestController
@RequestMapping("/webrtc")
public class IceController {

    private final IceServerService iceServerService;

    public IceController(IceServerService iceServerService) {
        this.iceServerService = iceServerService;
    }

    @GetMapping("/ice")
    public IceDtos.IceConfig ice(@AuthenticationPrincipal ConcordUserDetails principal) {
        return iceServerService.configFor(principal.id());
    }
}
