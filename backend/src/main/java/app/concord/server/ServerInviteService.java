package app.concord.server;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.user.User;
import app.concord.user.UserRepository;
import app.concord.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ServerInviteService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TTL = Duration.ofDays(7);

    private final ServerRepository serverRepository;
    private final ServerMemberRepository memberRepository;
    private final ServerInviteRepository inviteRepository;
    private final UserRepository userRepository;

    public ServerInviteService(ServerRepository serverRepository,
                               ServerMemberRepository memberRepository,
                               ServerInviteRepository inviteRepository,
                               UserRepository userRepository) {
        this.serverRepository = serverRepository;
        this.memberRepository = memberRepository;
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ServerInviteDtos.CreatedResponse create(User owner, UUID serverId,
                                                   ServerInviteDtos.CreateRequest request) {
        Server server = requireOwner(serverId, owner.getId());
        User invitee = userRepository.findByUsernameIgnoreCase(request.username().trim())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (memberRepository.existsByServerIdAndUserId(serverId, invitee.getId())) {
            throw new ApiException(ErrorCode.MEMBER_ALREADY_EXISTS);
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ServerInvite invite = inviteRepository.save(new ServerInvite(
                serverId, owner.getId(), invitee.getId(), hash(token),
                Instant.now().plus(TTL)));
        return new ServerInviteDtos.CreatedResponse(invite.getId(), serverId, server.getName(),
                invitee.getUsername(), token, invite.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public ServerInviteDtos.PendingPage pending(User user) {
        List<ServerInviteDtos.PendingResponse> items = inviteRepository
                .findByInviteeIdAndAcceptedAtIsNullAndDeclinedAtIsNullOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(ServerInvite::isPending)
                .filter(invite -> !invite.isExpired())
                .map(invite -> {
                    Server server = serverRepository.findById(invite.getServerId()).orElse(null);
                    User inviter = userRepository.findById(invite.getInviterId()).orElse(null);
                    return server == null || inviter == null ? null : new ServerInviteDtos.PendingResponse(
                            invite.getId(), server.getId(), server.getName(), inviter.getUsername(),
                            invite.getExpiresAt());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return new ServerInviteDtos.PendingPage(items);
    }

    @Transactional
    public void accept(User user, UUID inviteId) {
        ServerInvite invite = inviteRepository.findByIdAndInviteeId(inviteId, user.getId())
                .filter(ServerInvite::isPending)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_NOT_FOUND));
        if (invite.isExpired()) {
            throw new ApiException(ErrorCode.INVITE_EXPIRED);
        }
        if (memberRepository.existsByServerIdAndUserId(invite.getServerId(), user.getId())) {
            invite.accept();
            return;
        }
        memberRepository.save(new ServerMember(invite.getServerId(), user.getId(), "MEMBER"));
        invite.accept();
    }

    @Transactional
    public void decline(User user, UUID inviteId) {
        ServerInvite invite = inviteRepository.findByIdAndInviteeId(inviteId, user.getId())
                .filter(ServerInvite::isPending)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_NOT_FOUND));
        invite.decline();
    }

    private Server requireOwner(UUID serverId, UUID userId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ApiException(ErrorCode.SERVER_NOT_FOUND));
        if (!server.getOwnerId().equals(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return server;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
        }
    }
}