package app.concord.conversation;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.tx.AfterCommit;
import app.concord.contact.ContactService;
import app.concord.message.Message;
import app.concord.message.MessageRepository;
import app.concord.user.User;
import app.concord.user.UserDtos;
import app.concord.user.UserRepository;
import app.concord.user.UserStatus;
import app.concord.ws.RealtimeEvent;
import app.concord.ws.RealtimeNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conversas diretas.
 *
 * <p>Regra central: só se conversa com quem é contato aceito. É a contenção que
 * torna o cadastro aberto (D-03) viável — uma conta recém-criada não consegue
 * escrever para ninguém até ser aceita por alguém.
 *
 * <p>A conversa, uma vez criada, sobrevive ao fim do contato. Desfazer um
 * contato não apaga o histórico dos dois lados; apenas impede novas mensagens.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final ContactService contactService;
    private final UserRepository userRepository;
    private final RealtimeNotifier notifier;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationParticipantRepository participantRepository,
                               MessageRepository messageRepository,
                               ContactService contactService,
                               UserRepository userRepository,
                               RealtimeNotifier notifier) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.contactService = contactService;
        this.userRepository = userRepository;
        this.notifier = notifier;
    }

    /**
     * Abre a conversa direta com outro usuário, criando-a se ainda não existir.
     *
     * <p>Idempotente: chamar duas vezes devolve a mesma conversa, garantido pelo
     * índice único em {@code direct_key}.
     */
    @Transactional
    public Conversation openDirect(User me, UUID peerId) {
        if (peerId.equals(me.getId())) {
            throw new ApiException(ErrorCode.CANNOT_TARGET_SELF_CONTACT);
        }
        User peer = userRepository.findById(peerId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (contactService.isBlockedBetween(me.getId(), peerId)) {
            throw new ApiException(ErrorCode.BLOCKED);
        }
        if (!contactService.areContacts(me.getId(), peerId)) {
            throw new ApiException(ErrorCode.NOT_CONTACTS);
        }

        String directKey = Conversation.directKeyFor(me.getId(), peerId);
        return conversationRepository.findByDirectKey(directKey)
                .orElseGet(() -> createDirect(me.getId(), peer.getId(), directKey));
    }

    private Conversation createDirect(UUID meId, UUID peerId, String directKey) {
        Conversation conversation =
                conversationRepository.save(Conversation.direct(meId, peerId));
        participantRepository.save(
                new ConversationParticipant(conversation.getId(), meId));
        participantRepository.save(
                new ConversationParticipant(conversation.getId(), peerId));
        return conversation;
    }

    /**
     * Verifica a participação e devolve a conversa.
     *
     * <p>Toda leitura e toda escrita de mensagem passa por aqui. Quem não
     * participa recebe {@code 404}, não {@code 403}: um 403 confirmaria que a
     * conversa existe.
     */
    @Transactional(readOnly = true)
    public Conversation requireParticipant(UUID conversationId, UUID userId) {
        participantRepository.find(conversationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONVERSATION_NOT_FOUND));
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    /** Todos os participantes da conversa. Destinatários de qualquer evento dela. */
    @Transactional(readOnly = true)
    public List<UUID> participantIds(UUID conversationId) {
        return participantRepository.findByIdConversationId(conversationId).stream()
                .map(ConversationParticipant::getUserId)
                .toList();
    }

    /** O outro participante de uma conversa direta. */
    @Transactional(readOnly = true)
    public UUID peerIdOf(UUID conversationId, UUID meId) {
        return participantRepository.findByIdConversationId(conversationId).stream()
                .map(ConversationParticipant::getUserId)
                .filter(id -> !id.equals(meId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Transactional
    public void touch(UUID conversationId, Instant when) {
        conversationRepository.findById(conversationId)
                .ifPresent(conversation -> conversation.touch(when));
    }

    @Transactional
    public void markRead(User me, UUID conversationId, UUID messageId) {
        requireParticipant(conversationId, me.getId());
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getConversationId().equals(conversationId))
                .orElseThrow(() -> new ApiException(ErrorCode.MESSAGE_NOT_FOUND));

        ConversationParticipant participant = participantRepository
                .find(conversationId, me.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.CONVERSATION_NOT_FOUND));

        // Nunca retrocede o marcador: uma requisição fora de ordem não deve
        // "desler" mensagens já lidas.
        if (participant.getLastReadAt() == null
                || message.getCreatedAt().isAfter(participant.getLastReadAt())) {
            participant.markRead(message.getId(), message.getCreatedAt());
            participantRepository.save(participant);

            // O interlocutor vê o "lido" na hora. Vai só para ele: confirmação
            // de leitura é assunto dos dois, não da conversa como registro.
            UUID peerId = peerIdOf(conversationId, me.getId());
            UUID readerId = me.getId();
            AfterCommit.run(() -> notifier.sendToUser(peerId,
                    RealtimeEvent.of(RealtimeEvent.MESSAGE_READ,
                            new ReadReceipt(conversationId, readerId, message.getId()))));
        }
    }

    /** Confirmação de leitura enviada em tempo real. */
    public record ReadReceipt(UUID conversationId, UUID readerId, UUID messageId) {
    }

    /** Lista as conversas do usuário, com prévia e contagem de não lidas. */
    @Transactional(readOnly = true)
    public List<ConversationDtos.ConversationResponse> list(User me) {
        List<Conversation> conversations = conversationRepository.findAllOf(me.getId());
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = conversations.stream().map(Conversation::getId).toList();
        Map<UUID, UUID> peerByConversation = new HashMap<>();
        Map<UUID, ConversationParticipant> mine = new HashMap<>();

        for (ConversationParticipant participant
                : participantRepository.findByConversationIds(ids)) {
            if (participant.getUserId().equals(me.getId())) {
                mine.put(participant.getConversationId(), participant);
            } else {
                peerByConversation.put(participant.getConversationId(),
                        participant.getUserId());
            }
        }

        Map<UUID, User> peers = new HashMap<>();
        userRepository.findAllById(peerByConversation.values())
                .forEach(user -> peers.put(user.getId(), user));

        List<ConversationDtos.ConversationResponse> result = new ArrayList<>();
        for (Conversation conversation : conversations) {
            UUID peerId = peerByConversation.get(conversation.getId());
            User peer = peerId == null ? null : peers.get(peerId);
            if (peer == null) {
                continue;
            }
            ConversationParticipant participant = mine.get(conversation.getId());
            Instant since = participant == null ? null : participant.getLastReadAt();

            String preview = messageRepository.findLastOf(conversation.getId())
                    .map(message -> message.isDeleted()
                            ? "Mensagem apagada"
                            : abbreviate(message.getBody()))
                    .orElse(null);

            result.add(new ConversationDtos.ConversationResponse(
                    conversation.getId(),
                    UserDtos.PublicUserResponse.from(peer),
                    conversation.getCreatedAt(),
                    conversation.getLastMessageAt(),
                    preview,
                    messageRepository.countUnread(conversation.getId(), me.getId(), since),
                    contactService.isBlockedBetween(me.getId(), peerId),
                    contactService.areContacts(me.getId(), peerId)));
        }
        return result;
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= 120 ? body : body.substring(0, 120) + "…";
    }
}
