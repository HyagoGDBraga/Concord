package app.concord.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ServerRepository extends JpaRepository<Server, UUID> {

    @Query("""
            SELECT s FROM Server s
            JOIN ServerMember m ON m.serverId = s.id
            WHERE m.userId = :userId
            ORDER BY s.createdAt
            """)
    List<Server> findAllForUser(@Param("userId") UUID userId);
}