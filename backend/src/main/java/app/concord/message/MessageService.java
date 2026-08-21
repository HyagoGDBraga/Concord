package app.concord.message;

import app.concord.attachment.Attachment;
import app.concord.attachment.AttachmentRepository;
import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.ratelimit.RateLimiter;
import app.concord.common.tx.AfterCommit;
import app.concord.contact.ContactService;
import app.concord.conversation.ConversationService;
import app.concord.user.User;
import app.concord.ws.RealtimeEvent;
import app.concord.ws.RealtimeNotifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Envio, leitura, edição e exclusão de mensagens.
 *
 * <p>Nada aqui é auditado. Registrar "quem escreveu para quem e quando" em uma
 * tabela com retenção de meses produziria o acervo de metadados que o produto
 * existe para não ter — e metadados de conversa são, com frequência, mais
 * reveladores que o próprio conteúdo.
 */
@Service
public class MessageService {

    /** Teto por usuário: conversa humana não passa disso, automação sim. */
    private static final int SEND_LIMIT_PER_MINUTE = 30;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final ContactService contactService;
    private final RateLimiter rateLimiter;
    private final RealtimeNotifier notifier;
    private final AttachmentRepository attachmentRepository;

    public MessageService(MessageRepository messageRepository,
                          ConversationService conversationService,
                          ContactService contactService,
                          RateLimiter rateLimiter,
                          RealtimeNotifier notifier,
                          AttachmentRepository attachmentRepository) {
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
        this.contactService = contactService;
        this.rateLimiter = rateLimiter;
        this.notifier = notifier;
        this.attachmentRepository = attachmentRepository;
    }

    /**
     * Envia uma mensagem.
     *
     * <p>Idempotente por {@code clientMessageId}: um reenvio causado por
     * conexão instável devolve a mensagem já gravada em vez de duplicá-la. A
     * garantia final é o índice único {@code (conversation_id, client_message_id)}
     * — a verificação em Java economiza a exceção no caso comum, mas quem
     * decide é o banco.
     */
    @Transactional
    public Message send(User me, UUID conversationId, MessageDtos.SendMessageRequest request) {
        conversationService.requireParticipant(conversationId, me.getId());

        var existing = messageRepository.findByConversationIdAndClientMessageId(
                conversationId, request.clientMessageId());
        if (existing.isPresent()) {
            return existing.get();
        }

        UUID peerId = conversationService.peerIdOf(conversationId, me.getId());

        // O bloqueio vale nos dois sentidos: quem bloqueou e quem foi bloqueado
        // ficam impedidos de escrever um ao outro.
        if (contactService.isBlockedBetween(me.getId(), peerId)) {
            throw new ApiException(ErrorCode.BLOCKED);
        }
        // Desfazer o contato não apaga o histórico, mas encerra a troca.
        if (!contactService.areContacts(me.getId(), peerId)) {
            throw new ApiException(ErrorCode.NOT_CONTACTS);
        }

        // Limite por usuário, não por IP: aqui já se sabe quem é, e várias
        // pessoas podem compartilhar o mesmo IP.
        if (!rateLimiter.tryConsume("msg:" + me.getId(),
                SEND_LIMIT_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }

        String body = request.body() == null ? "" : request.body().trim();

        // Vazio só é recusado quando NÃO há anexo. Uma foto sem legenda é uma
        // mensagem legítima; exigir texto obrigaria o cliente a inventar um,
        // que era o espaço em branco que o @NotBlank rejeitava de volta.
        if (body.isEmpty() && request.attachmentIds().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Escreva uma mensagem ou anexe um arquivo");
        }

        Message message = messageRepository.save(
                new Message(conversationId, me.getId(), body, request.clientMessageId()));

        prenderAnexos(message, conversationId, me.getId(), request.attachmentIds());
        conversationService.touch(conversationId, message.getCreatedAt());

        // A entrega em tempo real vai para TODOS os participantes, inclusive
        // quem enviou: é o que sincroniza as outras abas e dispositivos da
        // mesma pessoa sem lógica adicional no cliente.
        notifyParticipants(conversationId, RealtimeEvent.MESSAGE_CREATED,
                MessageDtos.MessageResponse.from(message));
        return message;
    }

    /**
     * Histórico da conversa.
     *
     * @param cursor mensagem a partir da qual buscar para trás; {@code null}
     *               traz a página mais recente
     */
    @Transactional(readOnly = true)
    public MessageDtos.MessagePage history(User me, UUID conversationId, String cursor,
                                           Integer size) {
        conversationService.requireParticipant(conversationId, me.getId());

        int limit = clamp(size);
        // Busca um item a mais para saber se existe página anterior, sem
        // precisar de um COUNT sobre a conversa inteira.
        var pageable = PageRequest.of(0, limit + 1);

        List<Message> found = cursor == null
                ? messageRepository.findLatest(conversationId, pageable)
                : decodeAndFind(conversationId, cursor, pageable);

        boolean hasMore = found.size() > limit;
        List<Message> page = new ArrayList<>(hasMore ? found.subList(0, limit) : found);

        // O repositório devolve do mais novo para o mais antigo. Antes de
        // inverter, as duas pontas da página estão nas posições conhecidas.
        String olderCursor = page.isEmpty()
                ? null
                : MessageCursor.of(page.get(page.size() - 1)).encode();
        String latestCursor = page.isEmpty()
                ? null
                : MessageCursor.of(page.get(0)).encode();

        // A tela lê do mais antigo para o mais novo.
        Collections.reverse(page);

        return new MessageDtos.MessagePage(
                comAnexos(page),
                hasMore ? olderCursor : null,
                latestCursor,
                hasMore);
    }

    /**
     * Mensagens posteriores ao cursor, em ordem cronológica.
     *
     * <p>É o que o polling da Fase 3 consome. Na Fase 4 continua útil: depois de
     * uma reconexão do WebSocket, é assim que a lacuna é preenchida.
     */
    @Transactional(readOnly = true)
    public MessageDtos.MessagePage since(User me, UUID conversationId, String cursor) {
        conversationService.requireParticipant(conversationId, me.getId());
        MessageCursor decoded = MessageCursor.decode(cursor);

        List<Message> novas = messageRepository.findAfter(conversationId,
                decoded.createdAt(), decoded.id(), PageRequest.of(0, MAX_PAGE_SIZE));

        // Já vêm em ordem cronológica. O cursor devolvido avança para a última
        // mensagem vista; se nada chegou, o cursor informado continua valendo.
        String latestCursor = novas.isEmpty()
                ? cursor
                : MessageCursor.of(novas.get(novas.size() - 1)).encode();

        return new MessageDtos.MessagePage(
                comAnexos(novas),
                null, latestCursor, false);
    }

    @Transactional
    public Message edit(User me, UUID messageId, String newBody) {
        Message message = requireOwnMessage(me, messageId);
        if (message.isDeleted()) {
            throw new ApiException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        message.edit(newBody.trim());
        Message saved = messageRepository.save(message);
        notifyParticipants(saved.getConversationId(), RealtimeEvent.MESSAGE_UPDATED,
                MessageDtos.MessageResponse.from(saved));
        return saved;
    }

    /**
     * Apaga a mensagem. A linha permanece, sem corpo.
     *
     * <p>Remover a linha abriria buraco na conversa do interlocutor — a
     * mensagem também é histórico dele, e o direito de apagar o próprio texto
     * não alcança o registro de que a troca aconteceu.
     */
    @Transactional
    public void delete(User me, UUID messageId) {
        Message message = requireOwnMessage(me, messageId);
        if (!message.isDeleted()) {
            message.softDelete();
            messageRepository.save(message);
            notifyParticipants(message.getConversationId(), RealtimeEvent.MESSAGE_DELETED,
                    MessageDtos.MessageResponse.from(message));
        }
    }

    /**
     * Emite o evento apenas depois do commit.
     *
     * <p>Notificar dentro da transação abriria a janela em que o destinatário
     * recebe "chegou mensagem", consulta o servidor e não encontra nada —
     * porque o commit ainda não ocorreu, ou porque houve rollback e a mensagem
     * nunca existiu.
     */
    /**
     * Prende à mensagem os anexos que já foram enviados.
     *
     * <p>Três verificações, e nenhuma é redundante: o anexo precisa ser <b>de
     * quem está enviando</b> (senão daria para roubar o arquivo de outra
     * pessoa citando o id), precisa ser <b>desta conversa</b> (senão daria para
     * mover um arquivo de outra conversa para cá) e precisa estar <b>solto</b>
     * (senão o mesmo arquivo apareceria em duas mensagens).
     */
    private void prenderAnexos(Message message, UUID conversationId, UUID uploaderId,
                               List<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        if (ids.size() > 10) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "No máximo 10 arquivos por mensagem");
        }
        for (UUID id : ids) {
            Attachment anexo = attachmentRepository.findById(id)
                    .filter(a -> a.getUploaderId().equals(uploaderId))
                    .filter(a -> conversationId.equals(a.getConversationId()))
                    .filter(Attachment::isPending)
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Anexo inválido"));

            anexo.attachToDirectMessage(message.getId());
            attachmentRepository.save(anexo);
        }
    }

    /**
     * Converte a página em resposta, carregando os anexos numa consulta só.
     *
     * <p>Uma consulta por mensagem transformaria abrir uma conversa em cinquenta
     * idas ao banco — e o caso comum é nenhuma mensagem ter anexo.
     */
    private List<MessageDtos.MessageResponse> comAnexos(List<Message> mensagens) {
        if (mensagens.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = mensagens.stream().map(Message::getId).toList();

        Map<UUID, List<app.concord.attachment.AttachmentDtos.Response>> porMensagem =
                new HashMap<>();
        for (Attachment anexo : attachmentRepository.findByDirectMessageIds(ids)) {
            porMensagem
                    .computeIfAbsent(anexo.getMessageId(), k -> new ArrayList<>())
                    .add(app.concord.attachment.AttachmentDtos.Response.from(anexo));
        }

        return mensagens.stream()
                .map(mensagem -> MessageDtos.MessageResponse.from(
                        mensagem,
                        porMensagem.getOrDefault(mensagem.getId(), List.of())))
                .toList();
    }

    private void notifyParticipants(UUID conversationId, String type, Object payload) {
        List<UUID> recipients = conversationService.participantIds(conversationId);
        AfterCommit.run(() -> notifier.send(recipients, RealtimeEvent.of(type, payload)));
    }

    private Message requireOwnMessage(User me, UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(ErrorCode.MESSAGE_NOT_FOUND));
        // Confere a participação antes de tudo: sem isso, um id de mensagem
        // conhecido revelaria a existência de uma conversa alheia.
        conversationService.requireParticipant(message.getConversationId(), me.getId());
        if (!message.getSenderId().equals(me.getId())) {
            throw new ApiException(ErrorCode.NOT_MESSAGE_AUTHOR);
        }
        return message;
    }

    private List<Message> decodeAndFind(UUID conversationId, String cursor,
                                        PageRequest pageable) {
        MessageCursor decoded = MessageCursor.decode(cursor);
        return messageRepository.findBefore(conversationId, decoded.createdAt(),
                decoded.id(), pageable);
    }

    private static int clamp(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
