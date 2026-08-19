package app.concord.call;

import app.concord.auth.ConcordUserDetails;
import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.user.UserRepository;
import app.concord.ws.RealtimeEvent;
import app.concord.ws.RealtimeNotifier;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.UUID;

/**
 * Repasse de sinalização WebRTC.
 *
 * <p>O servidor é um carteiro: confere que quem envia participa da chamada,
 * confere que a chamada está aberta e entrega o envelope ao outro lado. O
 * conteúdo — SDP ou candidato ICE — não é lido, não é validado e não é gravado.
 *
 * <p>Não gravar é escolha de privacidade, não preguiça: SDP descreve codecs e
 * capacidades do dispositivo, e candidatos ICE revelam IP local, IP público e
 * topologia da rede de quem está falando.
 *
 * <p>O remetente vem do {@code Principal} da sessão STOMP. Não existe campo de
 * remetente no payload que possa ser forjado.
 */
@Controller
public class CallSignalingController {

    private final CallRepository callRepository;
    private final RealtimeNotifier notifier;
    private final UserRepository userRepository;

    public CallSignalingController(CallRepository callRepository,
                                   RealtimeNotifier notifier,
                                   UserRepository userRepository) {
        this.callRepository = callRepository;
        this.notifier = notifier;
        this.userRepository = userRepository;
    }

    /** Cliente publica em {@code /app/calls/{callId}/signal}. */
    @MessageMapping("/calls/{callId}/signal")
    @Transactional(readOnly = true)
    public void signal(@DestinationVariable UUID callId,
                       CallDtos.SignalMessage message,
                       Principal principal) {
        UUID senderId = resolveUserId(principal);
        if (senderId == null || message == null || message.type() == null) {
            return;
        }

        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new ApiException(ErrorCode.CALL_NOT_FOUND));

        if (!call.involves(senderId)) {
            throw new ApiException(ErrorCode.CALL_NOT_FOUND);
        }
        // Chamada encerrada não repassa mais nada: sem essa verificação, um
        // cliente poderia continuar despejando candidatos ICE no aparelho de
        // alguém que já desligou.
        if (!call.isOpen()) {
            throw new ApiException(ErrorCode.CALL_NOT_FOUND);
        }

        notifier.sendToUser(call.otherSide(senderId),
                RealtimeEvent.of(RealtimeEvent.CALL_SIGNAL,
                        new CallDtos.SignalEvent(callId, senderId,
                                message.type(), message.payload())));
    }

    private UUID resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof ConcordUserDetails user) {
            return user.id();
        }
        if (principal == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(principal.getName())
                .map(app.concord.user.User::getId)
                .orElse(null);
    }
}
