package app.concord.server;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.user.User;
import app.concord.user.UserRepository;
import app.concord.user.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ServerService {

    private final ServerRepository serverRepository;
    private final ServerMemberRepository memberRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;

    public ServerService(ServerRepository serverRepository,
                         ServerMemberRepository memberRepository,
                         ChannelRepository channelRepository,
                         UserRepository userRepository) {
        this.serverRepository = serverRepository;
        this.memberRepository = memberRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ServerDtos.ServerResponse> list(User user) {
        return serverRepository.findAllForUser(user.getId()).stream()
                .map(server -> ServerDtos.ServerResponse.from(server,
                        channelRepository.findByServerIdOrderByPositionAscCreatedAtAsc(server.getId())))
                .toList();
    }

    @Transactional
    public ServerDtos.ServerResponse create(User user, ServerDtos.CreateServerRequest request) {
        Server server = serverRepository.saveAndFlush(new Server(request.name(), user.getId()));
        memberRepository.save(new ServerMember(server.getId(), user.getId(), "OWNER"));
        Channel general = channelRepository.save(
                new Channel(server.getId(), "geral", "TEXT", 0));
        return ServerDtos.ServerResponse.from(server, List.of(general));
    }

    @Transactional
    public ServerDtos.ChannelResponse createChannel(User user, UUID serverId,
                                                     ServerDtos.CreateChannelRequest request) {
        requireOwner(serverId, user.getId());
        String name = request.name().trim();
        if (channelRepository.existsByServerIdAndNameIgnoreCase(serverId, name)) {
            throw new ApiException(ErrorCode.CHANNEL_NAME_TAKEN);
        }
        String type = request.type() == null ? "TEXT" : request.type().trim().toUpperCase(Locale.ROOT);
        if (!type.equals("TEXT") && !type.equals("VOICE")) {
            type = "TEXT";
        }
        int position = channelRepository.findByServerIdOrderByPositionAscCreatedAtAsc(serverId).size();
        try {
            return ServerDtos.ChannelResponse.from(
                    channelRepository.save(new Channel(serverId, name, type, position)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.CHANNEL_NAME_TAKEN);
        }
    }

    @Transactional(readOnly = true)
    public List<ServerDtos.ChannelResponse> channels(User user, UUID serverId) {
        requireMember(serverId, user.getId());
        return channelRepository.findByServerIdOrderByPositionAscCreatedAtAsc(serverId)
                .stream().map(ServerDtos.ChannelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ServerDtos.MemberResponse> members(User user, UUID serverId) {
        requireMember(serverId, user.getId());
        List<ServerMember> members = memberRepository.findByServerIdOrderByCreatedAtAsc(serverId);
        return members.stream()
                .flatMap(member -> userRepository.findById(member.getUserId())
                        .map(found -> java.util.stream.Stream.of(
                                ServerDtos.MemberResponse.from(member, found)))
                        .orElseGet(java.util.stream.Stream::empty))
                .toList();
    }

    @Transactional
    public ServerDtos.MemberResponse addMember(User owner, UUID serverId,
                                                ServerDtos.AddMemberRequest request) {
        Server server = requireOwner(serverId, owner.getId());
        User member = userRepository.findByUsernameIgnoreCase(request.username().trim())
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (memberRepository.existsByServerIdAndUserId(server.getId(), member.getId())) {
            throw new ApiException(ErrorCode.MEMBER_ALREADY_EXISTS);
        }
        ServerMember saved = memberRepository.save(
                new ServerMember(server.getId(), member.getId(), "MEMBER"));
        return ServerDtos.MemberResponse.from(saved, member);
    }

    @Transactional
    public void removeMember(User owner, UUID serverId, UUID userId) {
        Server server = requireOwner(serverId, owner.getId());
        if (server.getOwnerId().equals(userId)) {
            throw new ApiException(ErrorCode.CANNOT_REMOVE_OWNER);
        }
        if (!memberRepository.existsByServerIdAndUserId(serverId, userId)) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        memberRepository.deleteByServerIdAndUserId(serverId, userId);
    }

    @Transactional
    public void updateRole(User owner, UUID serverId, UUID userId,
                           ServerDtos.UpdateMemberRoleRequest request) {
        Server server = requireOwner(serverId, owner.getId());
        if (server.getOwnerId().equals(userId)) {
            throw new ApiException(ErrorCode.CANNOT_REMOVE_OWNER);
        }
        ServerMember member = memberRepository.findByServerIdAndUserId(serverId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        member.changeRole(request.role());
    }

    private void requireMember(UUID serverId, UUID userId) {
        if (!serverRepository.existsById(serverId)
                || !memberRepository.existsByServerIdAndUserId(serverId, userId)) {
            throw new ApiException(ErrorCode.SERVER_NOT_FOUND);
        }
    }

    private Server requireOwner(UUID serverId, UUID userId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ApiException(ErrorCode.SERVER_NOT_FOUND));
        if (!server.getOwnerId().equals(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return server;
    }
}