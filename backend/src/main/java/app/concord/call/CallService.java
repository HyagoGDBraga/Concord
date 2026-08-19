package app.concord.call;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.tx.AfterCommit;
import app.concord.contact.ContactService;
import app.concord.conversation.ConversationService;
import app.concord.presence.PresenceService;
import app.concord.user.User;
import app.concord.user.UserDtos;
import app.concord.user.UserRepository;
import app.concord.ws.RealtimeEvent;
import app.concord.ws.RealtimeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Ciclo de vida da chamada.
 *
 * <p>Convite, aceite, recusa e encerramento passam por aqui porque mudam estado
 * persistido — mesmo caminho de escrita das mensagens, mesma autorização. A
 * troca de SDP e candidatos ICE não passa: é efêmera e corre pelo
 * {@code CallSignalingController}.
 *
 * <p>Regra que sustenta o resto: <b>uma chamada aberta por pessoa</b>. Sem ela,
 * um cliente com defeito — ou mal-intencionado — poderia disparar convites em
 * série e fazer o telefone de alguém tocar indefinidamente.
 */
@Service
public class CallService {

    private static final Logger log = LoggerFactory.getLogger(CallService.class);

    private final CallRepository callRepository;
    private final ConversationService conversationService;
    private final ContactService contactService;
    private final PresenceService presenceService;
    private final UserRepository userRepository;
    private final RealtimeNotifier notifier;

    public CallService(CallRepository callRepository,
                       ConversationService conversationService,
                       ContactService contactService,
                       PresenceService presenceService,
                       UserRepository userRepository,
                       RealtimeNotifier notifier) {
        this.callRepository = callRepository;
        this.conversationService = conversationService;
        this.contactService = contactService;
        this.presenceService = presenceService;
        this.userRepository = userRepository;
        this.notifier = notifier;
    }

    /**
     * Inicia uma chamada.
     *
     * <p>Quem liga é sempre quem cria a oferta SDP. Fixar isso no servidor
     * elimina a colisão de negociação (<i>glare</i>), em que os dois lados
     * ofertam ao mesmo tempo e a conexão nunca se estabelece.
     */
    @Transactional
    public Call start(User me, CallDtos.StartCallRequest request) {
        conversationService.requireParticipant(request.conversationId(), me.getId());
        UUID peerId = conversationService.peerIdOf(request.conversationId(), me.getId());

        if (contactService.isBlockedBetween(me.getId(), peerId)) {
            throw new ApiException(ErrorCode.BLOCKED);
        }
        if (!contactService.areContacts(me.getId(), peerId)) {
            throw new ApiException(ErrorCode.NOT_CONTACTS);
        }

        // Chamar quem está offline apenas faria o aparelho de quem ligou tocar
        // sozinho até o tempo esgotar.
        if (!presenceService.isOnline(peerId)) {
            throw new ApiException(ErrorCode.CALLEE_UNAVAILABLE);
        }
        if (callRepository.findFirstOpenOf(me.getId()).isPresent()) {
            throw new ApiException(ErrorCode.CALL_ALREADY_ACTIVE);
        }
        if (callRepository.findFirstOpenOf(peerId).isPresent()) {
            throw new ApiException(ErrorCode.CALLEE_BUSY);
        }

        Call call = callRepository.save(
                new Call(request.conversationId(), me.getId(), peerId, request.type()));

        notify(peerId, RealtimeEvent.CALL_INVITE, call, me.getId());
        log.debug("Chamada iniciada: tipo={}", request.type());
        return call;
    }

    @Transactional
    public Call accept(User me, UUID callId) {
        Call call = requireParticipation(me, callId);
        if (call.getStatus() != CallStatus.RINGING) {
            throw new ApiException(ErrorCode.CALL_NOT_RINGING);
        }
        // Só quem recebeu pode atender. Quem ligou "aceitando" a própria
        // chamada colocaria a máquina de estados em um caminho impossível.
        if (!call.getCalleeId().equals(me.getId())) {
            throw new ApiException(ErrorCode.CALL_NOT_FOUND);
        }
        call.answer();
        callRepository.save(call);

        notify(call.getCallerId(), RealtimeEvent.CALL_ACCEPTED, call, me.getId());
        return call;
    }

    @Transactional
    public Call reject(User me, UUID callId) {
        Call call = requireParticipation(me, callId);
        if (call.getStatus() != CallStatus.RINGING) {
            throw new ApiException(ErrorCode.CALL_NOT_RINGING);
        }
        if (!call.getCalleeId().equals(me.getId())) {
            throw new ApiException(ErrorCode.CALL_NOT_FOUND);
        }
        return endWith(call, CallEndReason.REJECTED, me.getId());
    }

    /** Encerra a chamada. Serve tanto para desligar quanto para desistir. */
    @Transactional
    public Call end(User me, UUID callId) {
        Call call = requireParticipation(me, callId);
        if (!call.isOpen()) {
            return call;
        }
        CallEndReason reason = call.getStatus() == CallStatus.RINGING
                ? CallEndReason.CANCELLED
                : CallEndReason.HANGUP;
        return endWith(call, reason, me.getId());
    }

    /**
     * Encerra as chamadas abertas de quem perdeu a conexão.
     *
     * <p>Chamado quando o WebSocket cai: sem isso, a outra pessoa continuaria
     * vendo "em chamada" com alguém que já foi embora.
     */
    @Transactional
    public void endOpenCallsOf(UUID userId) {
        for (Call call : callRepository.findOpenOf(userId)) {
            endWith(call, CallEndReason.FAILED, userId);
        }
    }

    @Transactional
    public Call endByReaper(Call call, CallEndReason reason) {
        return endWith(call, reason, null);
    }

    private Call endWith(Call call, CallEndReason reason, UUID actorId) {
        call.end(reason);
        callRepository.save(call);

        // Os dois lados são avisados, inclusive quem encerrou: é o que fecha a
        // interface nas outras abas da mesma pessoa.
        notify(call.getCallerId(), RealtimeEvent.CALL_ENDED, call, actorId);
        notify(call.getCalleeId(), RealtimeEvent.CALL_ENDED, call, actorId);
        return call;
    }

    /** Verifica participação e devolve a chamada. */
    @Transactional(readOnly = true)
    public Call requireParticipation(User me, UUID callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new ApiException(ErrorCode.CALL_NOT_FOUND));
        if (!call.involves(me.getId())) {
            // 404, não 403: um 403 confirmaria que a chamada existe.
            throw new ApiException(ErrorCode.CALL_NOT_FOUND);
        }
        return call;
    }

    @Transactional(readOnly = true)
    public Optional<Call> currentCallOf(UUID userId) {
        return callRepository.findFirstOpenOf(userId);
    }

    @Transactional(readOnly = true)
    public Page<Call> history(User me, UUID conversationId, Pageable pageable) {
        conversationService.requireParticipant(conversationId, me.getId());
        return callRepository.findByConversation(conversationId, pageable);
    }

    /** Monta a resposta com o perfil do interlocutor já resolvido. */
    @Transactional(readOnly = true)
    public CallDtos.CallResponse toResponse(Call call, UUID viewerId) {
        UUID peerId = call.otherSide(viewerId);
        UserDtos.PublicUserResponse peer = userRepository.findById(peerId)
                .map(UserDtos.PublicUserResponse::from)
                .orElse(null);
        return CallDtos.CallResponse.from(call, peer);
    }

    private void notify(UUID recipientId, String type, Call call, UUID actorId) {
        CallDtos.CallResponse payload = toResponse(call, recipientId);
        AfterCommit.run(() -> notifier.sendToUser(recipientId,
                RealtimeEvent.of(type, payload)));
    }
}
