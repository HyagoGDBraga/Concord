package app.concord.server;

import app.concord.auth.ConcordUserDetails;
import app.concord.ws.RealtimeEvent;
import app.concord.ws.RealtimeNotifier;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** Sinalização efêmera das salas de voz. Nenhuma mídia ou SDP é persistido. */
@Controller
public class VoiceSignalingController {

    private static final Logger log =
            LoggerFactory.getLogger(VoiceSignalingController.class);

    private final ServerMemberRepository memberRepository;
    private final ChannelRepository channelRepository;
    private final RealtimeNotifier notifier;
    private final ConcurrentMap<UUID, CopyOnWriteArraySet<UUID>> rooms = new ConcurrentHashMap<>();

    public VoiceSignalingController(ServerMemberRepository memberRepository,
                                    ChannelRepository channelRepository,
                                    RealtimeNotifier notifier) {
        this.memberRepository = memberRepository;
        this.channelRepository = channelRepository;
        this.notifier = notifier;
    }

    @MessageMapping("/servers/{serverId}/channels/{channelId}/voice")
    public void signal(@DestinationVariable UUID serverId,
                       @DestinationVariable UUID channelId,
                       VoiceMessage message,
                       Principal principal) {
        UUID userId = resolveUserId(principal);

        // Cada recusa agora diz o motivo, em vez de sumir. O cliente exibe a
        // mensagem, e o log do servidor registra o caso.
        if (userId == null) {
            log.warn("Sinal de voz sem identidade resolvida; conexão sem sessão?");
            return;
        }
        if (message == null) {
            return;
        }
        if (!isMember(channelId, userId)) {
            log.warn("Usuário {} recusado no canal de voz {}: não é membro do servidor",
                    userId, channelId);
            notifier.sendToUser(userId, RealtimeEvent.of(RealtimeEvent.VOICE_ERROR,
                    Map.of("channelId", channelId,
                            "reason", "NOT_MEMBER",
                            "message", "Você não é membro deste servidor.")));
            return;
        }
        CopyOnWriteArraySet<UUID> room = rooms.computeIfAbsent(channelId,
                ignored -> new CopyOnWriteArraySet<>());

        switch (message.type()) {
            case "JOIN" -> join(channelId, userId, room);
            case "LEAVE" -> leave(channelId, userId, room);
            case "SIGNAL" -> forward(channelId, userId, message, room);
            case "STATE" -> broadcastState(channelId, userId, message, room);
            default -> { }
        }
    }

    private void join(UUID channelId, UUID userId, CopyOnWriteArraySet<UUID> room) {
        List<UUID> existing = room.stream().filter(id -> !id.equals(userId)).toList();
        room.add(userId);
        notifier.sendToUser(userId, RealtimeEvent.of(RealtimeEvent.VOICE_ROOM_STATE,
            Map.of("channelId", channelId, "participantIds", existing,
                "selfUserId", userId)));

        // Aviso para o SERVIDOR INTEIRO, não só para quem já está na sala.
        //
        // Antes só os participantes recebiam, e a consequência era esta: quem
        // estava de fora nunca sabia que havia alguém lá. A lista do canal
        // aparecia vazia, e só entrando é que se descobria quem estava —
        // exatamente o que a barra lateral deveria evitar.
        broadcastToServer(channelId, userId, RealtimeEvent.VOICE_USER_JOINED,
                Map.of("channelId", channelId, "userId", userId));
    }

    private void leave(UUID channelId, UUID userId, CopyOnWriteArraySet<UUID> room) {
        room.remove(userId);
        broadcastToServer(channelId, userId, RealtimeEvent.VOICE_USER_LEFT,
                Map.of("channelId", channelId, "userId", userId));
        RealtimeEvent event = RealtimeEvent.of(RealtimeEvent.VOICE_USER_LEFT,
            Map.of("channelId", channelId, "userId", userId));
        notifyOthers(room, userId, event);
        notifier.sendToUser(userId, event);
        if (room.isEmpty()) {
            rooms.remove(channelId, room);
        }
    }

    /**
     * Repassa o estado do participante (mudo, camera, tela) aos demais.
     *
     * <p>O payload vem do cliente e e repassado sem interpretacao — o servidor
     * nao tem como verificar se alguem realmente esta com a camera ligada, e
     * fingir que verifica seria pior que nao verificar. O que ele garante e a
     * identidade: userId vem do Principal, nunca do payload.
     */
    private void broadcastState(UUID channelId, UUID userId, VoiceMessage message,
                                CopyOnWriteArraySet<UUID> room) {
        if (!room.contains(userId)) {
            return;
        }
        notifyOthers(room, userId, RealtimeEvent.VOICE_USER_STATE,
                Map.of("channelId", channelId, "userId", userId,
                        "state", message.payload() == null ? Map.of() : message.payload()));
    }

    /**
     * Entrega um evento de sala a todos os membros do servidor.
     *
     * <p>Entradas e saídas interessam a quem está fora: é o que permite ver
     * quem está numa sala antes de entrar nela. Já a sinalização (SDP, ICE)
     * continua restrita aos participantes — ela não diz respeito a mais
     * ninguém e multiplicá-la seria desperdício.
     */
    private void broadcastToServer(UUID channelId, UUID origem, String tipo,
                                   Map<String, Object> payload) {
        channelRepository.findById(channelId).ifPresent(canal -> {
            List<UUID> membros = memberRepository.findUserIdsByServerId(canal.getServerId());
            RealtimeEvent evento = RealtimeEvent.of(tipo, payload);
            for (UUID membro : membros) {
                if (!membro.equals(origem)) {
                    notifier.sendToUser(membro, evento);
                }
            }
        });
    }

    private void forward(UUID channelId, UUID userId, VoiceMessage message,
                         CopyOnWriteArraySet<UUID> room) {
        if (message.targetUserId() == null || !room.contains(message.targetUserId())) {
            return;
        }
        notifier.sendToUser(message.targetUserId(), RealtimeEvent.of(RealtimeEvent.VOICE_SIGNAL,
                Map.of("channelId", channelId, "fromUserId", userId,
                        "type", message.signalType(), "payload", message.payload())));
    }

    private void notifyOthers(CopyOnWriteArraySet<UUID> room, UUID excluded,
                              String type, Object payload) {
        notifyOthers(room, excluded, RealtimeEvent.of(type, payload));
    }

    private void notifyOthers(CopyOnWriteArraySet<UUID> room, UUID excluded,
                              RealtimeEvent event) {
        room.stream().filter(id -> !id.equals(excluded))
                .forEach(id -> notifier.sendToUser(id, event));
    }

    private boolean isMember(UUID channelId, UUID userId) {
        return channelRepository.findById(channelId)
                .map(channel -> memberRepository.existsByServerIdAndUserId(channel.getServerId(), userId))
                .orElse(false);
    }

    private UUID resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof ConcordUserDetails user) {
            return user.id();
        }
        return null;
    }

    public record VoiceMessage(String type, UUID targetUserId, String signalType, Object payload) {
    }
}