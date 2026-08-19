package app.concord.call;

import app.concord.user.UserDtos;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class CallDtos {

    private CallDtos() {
    }

    public record StartCallRequest(
            @NotNull(message = "Informe a conversa")
            UUID conversationId,
            @NotNull(message = "Informe o tipo da chamada")
            CallType type
    ) {
    }

    /** Estado da chamada, como os dois lados a enxergam. */
    public record CallResponse(
            UUID id,
            UUID conversationId,
            UUID callerId,
            UUID calleeId,
            CallType type,
            CallStatus status,
            CallEndReason endReason,
            Instant createdAt,
            Instant answeredAt,
            Instant endedAt,
            long durationSeconds,
            UserDtos.PublicUserResponse peer,
            /** Quem deve criar a oferta SDP. Ver CallService#start. */
            boolean callerIsOfferer
    ) {
        public static CallResponse from(Call call, UserDtos.PublicUserResponse peer) {
            return new CallResponse(call.getId(), call.getConversationId(),
                    call.getCallerId(), call.getCalleeId(), call.getType(),
                    call.getStatus(), call.getEndReason(), call.getCreatedAt(),
                    call.getAnsweredAt(), call.getEndedAt(),
                    call.duration().toSeconds(), peer, true);
        }
    }

    /**
     * Envelope de sinalização trocado pelo WebSocket.
     *
     * <p>{@code payload} carrega SDP ou candidato ICE e é repassado ao outro
     * lado sem interpretação nem armazenamento — o servidor não tem motivo para
     * ler o conteúdo, e ler significaria registrar topologia de rede.
     *
     * <p>Não existe campo de remetente: ele vem do {@code Principal} da sessão
     * STOMP. Um cliente que enviasse um não seria acreditado.
     */
    public record SignalMessage(
            @NotNull(message = "Informe o tipo do sinal")
            SignalType type,
            Object payload
    ) {
    }

    public enum SignalType {
        /** Oferta SDP de quem inicia a negociação. */
        OFFER,
        /** Resposta SDP. */
        ANSWER,
        /** Candidato ICE. */
        ICE_CANDIDATE,
        /** Renegociação — por exemplo, ao ligar a câmera no meio da chamada. */
        RENEGOTIATE,
        /**
         * Aviso de que o remetente começou ou parou de compartilhar a tela.
         *
         * <p>É apenas um aviso: a mídia em si já trocou por {@code replaceTrack}
         * na conexão existente, sem passar pelo servidor. Sem este sinal o outro
         * lado receberia o vídeo da tela sem saber que é uma tela, e a
         * interface o enquadraria como se fosse um rosto — cortando justamente
         * as bordas, onde costuma estar o que importa.
         *
         * <p>Payload: <code>{ "active": boolean }</code>.
         */
        SCREEN_SHARE
    }

    /** Sinal entregue ao destinatário, já com o remetente resolvido. */
    public record SignalEvent(UUID callId, UUID fromUserId, SignalType type, Object payload) {
    }
}
