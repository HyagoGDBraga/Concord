package app.concord.server;

import app.concord.auth.ConcordUserDetails;
import app.concord.ws.RealtimeEvent;
import app.concord.ws.RealtimeNotifier;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

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
        if (userId == null || message == null || !isMember(channelId, userId)) {
            return;
        }
        CopyOnWriteArraySet<UUID> room = rooms.computeIfAbsent(channelId,
                ignored -> new CopyOnWriteArraySet<>());

        switch (message.type()) {
            case "JOIN" -> join(channelId, userId, room);
            case "LEAVE" -> leave(channelId, userId, room);
            case "SIGNAL" -> forward(channelId, userId, message, room);
            default -> { }
        }
    }

    private void join(UUID channelId, UUID userId, CopyOnWriteArraySet<UUID> room) {
        List<UUID> existing = room.stream().filter(id -> !id.equals(userId)).toList();
        room.add(userId);
        notifier.sendToUser(userId, RealtimeEvent.of(RealtimeEvent.VOICE_ROOM_STATE,
            Map.of("channelId", channelId, "participantIds", existing,
                "selfUserId", userId)));
        notifyOthers(room, userId, RealtimeEvent.VOICE_USER_JOINED,
                Map.of("channelId", channelId, "userId", userId));
    }

    private void leave(UUID channelId, UUID userId, CopyOnWriteArraySet<UUID> room) {
        room.remove(userId);
        RealtimeEvent event = RealtimeEvent.of(RealtimeEvent.VOICE_USER_LEFT,
            Map.of("channelId", channelId, "userId", userId));
        notifyOthers(room, userId, event);
        notifier.sendToUser(userId, event);
        if (room.isEmpty()) {
            rooms.remove(channelId, room);
        }
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