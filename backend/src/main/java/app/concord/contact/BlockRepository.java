package app.concord.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, Block.BlockId> {

    /**
     * Existe bloqueio em qualquer direção entre as duas pessoas?
     *
     * <p>Uma única consulta responde pelos dois sentidos, que é o que o envio de
     * mensagem precisa saber.
     */
    @Query("""
            SELECT count(b) > 0 FROM Block b
            WHERE (b.id.blockerId = :a AND b.id.blockedId = :b)
               OR (b.id.blockerId = :b AND b.id.blockedId = :a)
            """)
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query("SELECT b.id.blockedId FROM Block b WHERE b.id.blockerId = :userId")
    List<UUID> findBlockedIdsBy(@Param("userId") UUID userId);
}
