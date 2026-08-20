package app.concord.server;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChannelMessageService {

    private final ChannelRepository channelRepository;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository memberRepository;
    private final ChannelMessageRepository messageRepository;

    public ChannelMessageService(ChannelRepository channelRepository,
                                 ServerRepository serverRepository,
                                 ServerMemberRepository memberRepository,
                                 ChannelMessageRepository messageRepository) {
        this.channelRepository = channelRepository;
        this.serverRepository = serverRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public ChannelMessageDtos.Page history(User user, UUID channelId) {
        requireMember(channelId, user.getId());
        return new ChannelMessageDtos.Page(messageRepository
                .findHistory(channelId, PageRequest.of(0, 100)).stream()
                .map(ChannelMessageDtos.Response::from).toList());
    }

    @Transactional
    public ChannelMessageDtos.Response send(User user, UUID channelId,
                                            ChannelMessageDtos.SendRequest request) {
        requireMember(channelId, user.getId());
        return ChannelMessageDtos.Response.from(messageRepository
                .findByChannelIdAndClientMessageId(channelId, request.clientMessageId())
                .orElseGet(() -> messageRepository.save(new ChannelMessage(
                        channelId, user.getId(), request.body(), request.clientMessageId()))));
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