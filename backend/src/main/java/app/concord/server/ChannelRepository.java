package app.concord.server;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    List<Channel> findByServerIdOrderByPositionAscCreatedAtAsc(UUID serverId);

    boolean existsByServerIdAndNameIgnoreCase(UUID serverId, String name);
}