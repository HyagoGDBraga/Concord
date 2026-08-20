package app.concord.server;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.user.User;
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

    public ServerService(ServerRepository serverRepository,
                         ServerMemberRepository memberRepository,
                         ChannelRepository channelRepository) {
        this.serverRepository = serverRepository;
        this.memberRepository = memberRepository;
        this.channelRepository = channelRepository;
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
        requireMember(serverId, user.getId());
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

    private void requireMember(UUID serverId, UUID userId) {
        if (!serverRepository.existsById(serverId)
                || !memberRepository.existsByServerIdAndUserId(serverId, userId)) {
            throw new ApiException(ErrorCode.SERVER_NOT_FOUND);
        }
    }
}