package app.concord.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // As buscas usam lower(...) explicitamente para casar com os índices
    // funcionais users_username_lower_key e users_email_lower_key.

    @Query("SELECT u FROM User u WHERE lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("""
            SELECT u FROM User u
            WHERE lower(u.username) = lower(:identifier)
               OR lower(u.email) = lower(:identifier)
            """)
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    long countByRoleAndStatusNot(UserRole role, UserStatus status);

    List<User> findByRole(UserRole role);

    @Query("""
            SELECT u FROM User u
            WHERE (:status IS NULL OR u.status = :status)
              AND (:query IS NULL
                   OR lower(u.username) LIKE lower(concat('%', :query, '%'))
                   OR lower(u.email) LIKE lower(concat('%', :query, '%'))
                   OR lower(u.displayName) LIKE lower(concat('%', :query, '%')))
            ORDER BY u.createdAt DESC
            """)
    Page<User> search(@Param("query") String query,
                      @Param("status") UserStatus status,
                      Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.status = app.concord.user.UserStatus.PENDING_VERIFICATION
              AND u.createdAt < :cutoff
            """)
    List<User> findUnverifiedBefore(@Param("cutoff") Instant cutoff);
}
