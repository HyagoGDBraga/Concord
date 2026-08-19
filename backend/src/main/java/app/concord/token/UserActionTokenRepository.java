package app.concord.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserActionTokenRepository extends JpaRepository<UserActionToken, UUID> {

    Optional<UserActionToken> findByTokenHashAndAction(String tokenHash, ActionTokenType action);

    @Modifying
    @Query("DELETE FROM UserActionToken t WHERE t.userId = :userId AND t.action = :action")
    int deleteByUserAndAction(@Param("userId") UUID userId,
                              @Param("action") ActionTokenType action);

    @Modifying
    @Query("DELETE FROM UserActionToken t WHERE t.userId = :userId")
    int deleteAllByUser(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM UserActionToken t WHERE t.expiresAt < :cutoff OR t.usedAt IS NOT NULL")
    int deleteExpiredOrUsed(@Param("cutoff") Instant cutoff);
}
