package app.concord.presence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PresenceDtos {

    private PresenceDtos() {
    }

    /** Alguém entrou ou saiu. Enviado apenas aos contatos aceitos. */
    public record PresenceEvent(UUID userId, boolean online, Instant at) {
    }

    /** Estado inicial, buscado ao abrir o aplicativo. */
    public record PresenceSnapshot(List<UUID> onlineContactIds) {
    }
}
