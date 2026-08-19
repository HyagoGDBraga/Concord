package app.concord.webrtc;

import java.time.Instant;
import java.util.List;

public final class IceDtos {

    private IceDtos() {
    }

    /**
     * Servidor ICE no formato que o {@code RTCPeerConnection} espera.
     *
     * <p>{@code username} e {@code credential} são nulos para STUN, que é
     * anônimo.
     */
    public record IceServer(List<String> urls, String username, String credential) {
    }

    /**
     * @param iceServers servidores a passar ao {@code RTCPeerConnection}
     * @param expiresAt  quando as credenciais deixam de valer; o cliente deve
     *                   buscar novas antes de iniciar uma chamada após esse
     *                   instante
     */
    public record IceConfig(List<IceServer> iceServers, Instant expiresAt) {
    }
}
