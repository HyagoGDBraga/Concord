package app.concord.server;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerInviteRepository extends JpaRepository<ServerInvite, UUID> {

    List<ServerInvite> findByInviteeIdAndAcceptedAtIsNullAndDeclinedAtIsNullOrderByCreatedAtDesc(
            UUID inviteeId);

    Optional<ServerInvite> findByIdAndInviteeId(UUID id, UUID inviteeId);
}