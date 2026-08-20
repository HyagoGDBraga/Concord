package app.concord.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerMemberRepository extends JpaRepository<ServerMember, UUID> {

    boolean existsByServerIdAndUserId(UUID serverId, UUID userId);

    @Query("SELECT m.userId FROM ServerMember m WHERE m.serverId = :serverId")
    List<UUID> findUserIdsByServerId(@Param("serverId") UUID serverId);

    List<ServerMember> findByServerIdOrderByCreatedAtAsc(UUID serverId);

    Optional<ServerMember> findByServerIdAndUserId(UUID serverId, UUID userId);

    void deleteByServerIdAndUserId(UUID serverId, UUID userId);
}