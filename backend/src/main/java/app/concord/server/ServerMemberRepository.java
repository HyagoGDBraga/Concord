package app.concord.server;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServerMemberRepository extends JpaRepository<ServerMember, UUID> {

    boolean existsByServerIdAndUserId(UUID serverId, UUID userId);
}