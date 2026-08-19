package app.concord.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Bloqueio unidirecional de um usuário por outro.
 *
 * <p>O efeito no envio de mensagens é recíproco: se A bloqueia B, nenhum dos
 * dois consegue escrever para o outro. Um bloqueio que só impedisse o
 * bloqueador de escrever não protegeria ninguém.
 */
@Entity
@Table(name = "blocks")
public class Block {

    @Embeddable
    public record BlockId(
            @Column(name = "blocker_id") UUID blockerId,
            @Column(name = "blocked_id") UUID blockedId
    ) implements Serializable {

        public BlockId {
            Objects.requireNonNull(blockerId);
            Objects.requireNonNull(blockedId);
        }
    }

    @EmbeddedId
    private BlockId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Block() {
    }

    public Block(UUID blockerId, UUID blockedId) {
        this.id = new BlockId(blockerId, blockedId);
        this.createdAt = Instant.now();
    }

    public BlockId getId() {
        return id;
    }

    public UUID getBlockerId() {
        return id.blockerId();
    }

    public UUID getBlockedId() {
        return id.blockedId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
