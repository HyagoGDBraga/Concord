package app.concord.server;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.tx.AfterCommit;
import app.concord.user.User;
import app.concord.user.UserRepository;
import app.concord.ws.RealtimeEvent;
import app.concord.ws.RealtimeNotifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mensagens de canal: envio, histórico, respostas, menções, reações, fixação,
 * edição e exclusão.
 *
 * <p>Duas regras percorrem tudo:
 *
 * <ol>
 *   <li><b>Participação primeiro.</b> Nenhum método toca em mensagem antes de
 *       confirmar que quem pediu é membro do servidor. Quem não é recebe
 *       {@code CHANNEL_NOT_FOUND} — não {@code 403}, que confirmaria a
 *       existência do canal.</li>
 *   <li><b>Nada de N+1.</b> Reações, menções e prévias de resposta são
 *       carregadas em consulta única por página. Uma consulta por mensagem
 *       transformaria abrir um canal em cinquenta idas ao banco.</li>
 * </ol>
 */
@Service
public class ChannelMessageService {

    /** Teto de mensagens fixadas por canal, como no Discord. */
    private static final int MAX_PINNED = 50;

    private final ChannelRepository channelRepository;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository memberRepository;
    private final ChannelMessageRepository messageRepository;
    private final MessageReactionRepository reactionRepository;
    private final MessageMentionRepository mentionRepository;
    private final UserRepository userRepository;
    private final RealtimeNotifier notifier;

    public ChannelMessageService(ChannelRepository channelRepository,
                                 ServerRepository serverRepository,
                                 ServerMemberRepository memberRepository,
                                 ChannelMessageRepository messageRepository,
                                 MessageReactionRepository reactionRepository,
                                 MessageMentionRepository mentionRepository,
                                 UserRepository userRepository,
                                 RealtimeNotifier notifier) {
        this.channelRepository = channelRepository;
        this.serverRepository = serverRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.mentionRepository = mentionRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
    }

    /* --------------------------------------------------------- histórico */

    @Transactional(readOnly = true)
    public ChannelMessageDtos.Page history(User user, UUID channelId) {
        requireMember(channelId, user.getId());
        List<ChannelMessage> mensagens =
                messageRepository.findHistory(channelId, PageRequest.of(0, 100));
        return new ChannelMessageDtos.Page(enrich(mensagens, user.getId()));
    }

    @Transactional(readOnly = true)
    public ChannelMessageDtos.Page pinned(User user, UUID channelId) {
        requireMember(channelId, user.getId());
        return new ChannelMessageDtos.Page(
                enrich(messageRepository.findPinned(channelId), user.getId()));
    }

    /* ------------------------------------------------------------- envio */

    @Transactional
    public ChannelMessageDtos.Response send(User user, UUID channelId,
                                            ChannelMessageDtos.SendRequest request) {
        requireMember(channelId, user.getId());

        Optional<ChannelMessage> existente = messageRepository
                .findByChannelIdAndClientMessageId(channelId, request.clientMessageId());
        if (existente.isPresent()) {
            // Idempotência: reenvio por instabilidade de rede devolve a mesma
            // mensagem em vez de duplicá-la.
            return enrich(List.of(existente.get()), user.getId()).get(0);
        }

        UUID replyToId = validarResposta(channelId, request.replyToId());

        ChannelMessage mensagem = messageRepository.save(new ChannelMessage(
                channelId, user.getId(), request.body(), request.clientMessageId(), replyToId));

        List<UUID> mencionados = registrarMencoes(mensagem, channelId);

        ChannelMessageDtos.Response resposta =
                enrich(List.of(mensagem), user.getId()).get(0);

        UUID serverId = channelRepository.findById(channelId).orElseThrow().getServerId();
        List<UUID> destinatarios = memberRepository.findUserIdsByServerId(serverId);

        AfterCommit.run(() -> {
            notifier.send(destinatarios,
                    RealtimeEvent.of(RealtimeEvent.CHANNEL_MESSAGE_CREATED, resposta));
            // Quem foi mencionado recebe um evento próprio: a interface precisa
            // distinguir "chegou mensagem no canal" de "citaram você".
            for (UUID mencionado : mencionados) {
                notifier.sendToUser(mencionado,
                        RealtimeEvent.of(RealtimeEvent.MESSAGE_MENTION, resposta));
            }
        });
        return resposta;
    }

    /**
     * Confere que a mensagem respondida existe e pertence ao mesmo canal.
     *
     * <p>Sem a verificação de canal, seria possível responder a uma mensagem de
     * outro servidor e vazar o trecho dela na prévia.
     */
    private UUID validarResposta(UUID channelId, UUID replyToId) {
        if (replyToId == null) {
            return null;
        }
        ChannelMessage alvo = messageRepository.findById(replyToId)
                .orElseThrow(() -> new ApiException(ErrorCode.MESSAGE_NOT_FOUND));
        if (!alvo.getChannelId().equals(channelId)) {
            throw new ApiException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        return replyToId;
    }

    /** Resolve os @usuario do texto para membros do servidor. */
    private List<UUID> registrarMencoes(ChannelMessage mensagem, UUID channelId) {
        Set<String> usernames = MentionParser.extract(mensagem.getBody());
        if (usernames.isEmpty()) {
            return List.of();
        }
        UUID serverId = channelRepository.findById(channelId).orElseThrow().getServerId();
        List<UUID> mencionados = new ArrayList<>();

        for (String username : usernames) {
            userRepository.findByUsernameIgnoreCase(username)
                    // Só vale mencionar quem está no servidor. Citar alguém de
                    // fora não deve notificar ninguém nem revelar que a conta
                    // existe.
                    .filter(alvo -> memberRepository
                            .existsByServerIdAndUserId(serverId, alvo.getId()))
                    .filter(alvo -> !alvo.getId().equals(mensagem.getSenderId()))
                    .ifPresent(alvo -> {
                        mentionRepository.save(
                                new MessageMention(mensagem.getId(), alvo.getId()));
                        mencionados.add(alvo.getId());
                    });
        }
        return mencionados;
    }

    /* ------------------------------------------------- edição e exclusão */

    @Transactional
    public ChannelMessageDtos.Response edit(User user, UUID messageId,
                                            ChannelMessageDtos.EditRequest request) {
        ChannelMessage mensagem = requireOwnMessage(user, messageId);
        if (mensagem.isDeleted()) {
            throw new ApiException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        mensagem.edit(request.body());
        messageRepository.save(mensagem);

        // As menções são recalculadas: editar pode acrescentar ou remover
        // citações, e o registro precisa refletir o texto atual.
        mentionRepository.findByMessageIds(List.of(messageId))
                .forEach(mentionRepository::delete);
        registrarMencoes(mensagem, mensagem.getChannelId());

        return publicar(mensagem, user.getId(), RealtimeEvent.CHANNEL_MESSAGE_UPDATED);
    }

    /**
     * Apaga a mensagem.
     *
     * <p>Autor sempre pode. Dono e moderador do servidor também — sem isso, não
     * haveria como remover abuso de terceiro, que é a razão de existir
     * moderação.
     */
    @Transactional
    public ChannelMessageDtos.Response delete(User user, UUID messageId) {
        ChannelMessage mensagem = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(ErrorCode.MESSAGE_NOT_FOUND));
        requireMember(mensagem.getChannelId(), user.getId());

        if (!mensagem.getSenderId().equals(user.getId())
                && !podeModerar(mensagem.getChannelId(), user.getId())) {
            throw new ApiException(ErrorCode.NOT_MESSAGE_AUTHOR);
        }
        if (!mensagem.isDeleted()) {
            mensagem.softDelete();
            messageRepository.save(mensagem);
        }
        return publicar(mensagem, user.getId(), RealtimeEvent.CHANNEL_MESSAGE_DELETED);
    }

    /* ----------------------------------------------------------- fixação */

    @Transactional
    public ChannelMessageDtos.Response togglePin(User user, UUID messageId) {
        ChannelMessage mensagem = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(ErrorCode.MESSAGE_NOT_FOUND));
        requireMember(mensagem.getChannelId(), user.getId());

        if (!podeModerar(mensagem.getChannelId(), user.getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Só moderadores podem fixar mensagens");
        }
        if (mensagem.isPinned()) {
            mensagem.unpin();
        } else {
            if (messageRepository.findPinned(mensagem.getChannelId()).size() >= MAX_PINNED) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "O canal já tem " + MAX_PINNED + " mensagens fixadas");
            }
            mensagem.pin(user.getId());
        }
        messageRepository.save(mensagem);
        return publicar(mensagem, user.getId(), RealtimeEvent.CHANNEL_MESSAGE_UPDATED);
    }

    /* ----------------------------------------------------------- reações */

    @Transactional
    public ChannelMessageDtos.Response react(User user, UUID messageId, String emoji) {
        ChannelMessage mensagem = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(ErrorCode.MESSAGE_NOT_FOUND));
        requireMember(mensagem.getChannelId(), user.getId());

        MessageReaction.ReactionId id =
                new MessageReaction.ReactionId(messageId, user.getId(), emoji);

        // Alternância: quem já reagiu com aquele emoji está retirando a reação.
        if (reactionRepository.existsById(id)) {
            reactionRepository.deleteById(id);
        } else {
            reactionRepository.save(new MessageReaction(messageId, user.getId(), emoji));
        }
        return publicar(mensagem, user.getId(), RealtimeEvent.CHANNEL_MESSAGE_UPDATED);
    }

    /* -------------------------------------------------------- utilitários */

    private ChannelMessageDtos.Response publicar(ChannelMessage mensagem, UUID viewerId,
                                                  String evento) {
        ChannelMessageDtos.Response resposta = enrich(List.of(mensagem), viewerId).get(0);
        UUID serverId = channelRepository.findById(mensagem.getChannelId())
                .orElseThrow().getServerId();
        List<UUID> destinatarios = memberRepository.findUserIdsByServerId(serverId);

        AfterCommit.run(() -> notifier.send(destinatarios,
                RealtimeEvent.of(evento, resposta)));
        return resposta;
    }

    /**
     * Acrescenta reações, menções e prévias de resposta a uma página.
     *
     * <p>Três consultas no total, independentemente de quantas mensagens a
     * página tiver.
     */
    private List<ChannelMessageDtos.Response> enrich(List<ChannelMessage> mensagens,
                                                      UUID viewerId) {
        if (mensagens.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = mensagens.stream().map(ChannelMessage::getId).toList();

        // 1. Reações, agrupadas por mensagem e emoji.
        Map<UUID, Map<String, List<UUID>>> reacoes = new HashMap<>();
        for (MessageReaction reacao : reactionRepository.findByMessageIds(ids)) {
            reacoes.computeIfAbsent(reacao.getMessageId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(reacao.getEmoji(), k -> new ArrayList<>())
                    .add(reacao.getUserId());
        }

        // 2. Menções.
        Map<UUID, List<UUID>> mencoes = new HashMap<>();
        for (MessageMention mencao : mentionRepository.findByMessageIds(ids)) {
            mencoes.computeIfAbsent(mencao.getMessageId(), k -> new ArrayList<>())
                    .add(mencao.getUserId());
        }

        // 3. Prévias das mensagens respondidas.
        List<UUID> respondidas = mensagens.stream()
                .map(ChannelMessage::getReplyToId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, ChannelMessage> originais = new HashMap<>();
        if (!respondidas.isEmpty()) {
            messageRepository.findAllByIdIn(respondidas)
                    .forEach(m -> originais.put(m.getId(), m));
        }

        List<ChannelMessageDtos.Response> resultado = new ArrayList<>(mensagens.size());
        for (ChannelMessage mensagem : mensagens) {
            resultado.add(ChannelMessageDtos.Response.from(mensagem).with(
                    previewDe(originais.get(mensagem.getReplyToId())),
                    mencoes.getOrDefault(mensagem.getId(), List.of()),
                    resumirReacoes(reacoes.get(mensagem.getId()), viewerId)));
        }
        return resultado;
    }

    private ChannelMessageDtos.ReplyPreview previewDe(ChannelMessage original) {
        if (original == null) {
            return null;
        }
        String corpo = original.getBody();
        // Trecho curto: a prévia é uma referência, não uma cópia da mensagem.
        String excerto = corpo == null
                ? null
                : (corpo.length() <= 120 ? corpo : corpo.substring(0, 120) + "…");

        return new ChannelMessageDtos.ReplyPreview(original.getId(), original.getSenderId(),
                excerto, original.isDeleted());
    }

    private List<ChannelMessageDtos.ReactionSummary> resumirReacoes(
            Map<String, List<UUID>> porEmoji, UUID viewerId) {
        if (porEmoji == null || porEmoji.isEmpty()) {
            return List.of();
        }
        List<ChannelMessageDtos.ReactionSummary> resumo = new ArrayList<>();
        porEmoji.forEach((emoji, usuarios) -> resumo.add(
                new ChannelMessageDtos.ReactionSummary(emoji, usuarios.size(),
                        usuarios.contains(viewerId), List.copyOf(usuarios))));
        return resumo;
    }

    private ChannelMessage requireOwnMessage(User user, UUID messageId) {
        ChannelMessage mensagem = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(ErrorCode.MESSAGE_NOT_FOUND));
        requireMember(mensagem.getChannelId(), user.getId());
        if (!mensagem.getSenderId().equals(user.getId())) {
            throw new ApiException(ErrorCode.NOT_MESSAGE_AUTHOR);
        }
        return mensagem;
    }

    private boolean podeModerar(UUID channelId, UUID userId) {
        UUID serverId = channelRepository.findById(channelId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHANNEL_NOT_FOUND))
                .getServerId();
        return memberRepository.findByServerIdAndUserId(serverId, userId)
                .map(membro -> !"MEMBER".equals(membro.getRole()))
                .orElse(false);
    }

    private void requireMember(UUID channelId, UUID userId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHANNEL_NOT_FOUND));
        if (!serverRepository.existsById(channel.getServerId())
                || !memberRepository.existsByServerIdAndUserId(channel.getServerId(), userId)) {
            throw new ApiException(ErrorCode.CHANNEL_NOT_FOUND);
        }
    }
}
