package app.concord.ws;

import java.time.Instant;

/**
 * Envelope único de todo evento em tempo real.
 *
 * <p>Um envelope só, com {@code type} discriminando o conteúdo, em vez de um
 * destino STOMP por tipo de evento: o cliente abre uma assinatura, e adicionar
 * um evento novo não exige mexer na configuração do broker nem no cliente já
 * conectado.
 *
 * @param type    identificador do evento, em maiúsculas
 * @param payload dados do evento; nunca contém segredo nem token
 * @param at      instante de emissão no servidor
 */
public record RealtimeEvent(String type, Object payload, Instant at) {

    public static final String MESSAGE_CREATED = "MESSAGE_CREATED";
    public static final String MESSAGE_UPDATED = "MESSAGE_UPDATED";
    public static final String MESSAGE_DELETED = "MESSAGE_DELETED";
    public static final String MESSAGE_READ = "MESSAGE_READ";
    public static final String TYPING = "TYPING";
    public static final String PRESENCE = "PRESENCE";
    public static final String CONTACT_REQUEST = "CONTACT_REQUEST";
    public static final String CONTACT_ACCEPTED = "CONTACT_ACCEPTED";
    public static final String CHANNEL_MESSAGE_CREATED = "CHANNEL_MESSAGE_CREATED";
    public static final String VOICE_ROOM_STATE = "VOICE_ROOM_STATE";
    public static final String VOICE_USER_JOINED = "VOICE_USER_JOINED";
    public static final String VOICE_USER_LEFT = "VOICE_USER_LEFT";
    public static final String VOICE_SIGNAL = "VOICE_SIGNAL";
    /**
     * Estado de um participante da sala: mudo, camera ligada, compartilhando
     * tela.
     *
     * <p>Existe porque nada disso e dedutivel do WebRTC. Uma trilha de video
     * pode ser camera ou tela — quem recebe nao tem como saber, e sem esta
     * informacao a interface nao consegue mostrar quem esta compartilhando.
     *
     * <p>Efemero: nada e persistido.
     */
    public static final String CHANNEL_MESSAGE_UPDATED = "CHANNEL_MESSAGE_UPDATED";
    public static final String CHANNEL_MESSAGE_DELETED = "CHANNEL_MESSAGE_DELETED";
    public static final String MESSAGE_MENTION = "MESSAGE_MENTION";
    /** Estado inicial de presença, enviado a quem conecta. */
    public static final String PRESENCE_SNAPSHOT = "PRESENCE_SNAPSHOT";
    public static final String VOICE_USER_STATE = "VOICE_USER_STATE";

    // --- chamadas (Fase 5)
    /** Alguém está ligando. */
    public static final String CALL_INVITE = "CALL_INVITE";
    public static final String CALL_ACCEPTED = "CALL_ACCEPTED";
    public static final String CALL_ENDED = "CALL_ENDED";
    /**
     * Sinalização WebRTC: SDP e candidatos ICE.
     *
     * <p>Único evento cujo conteúdo o servidor repassa sem interpretar — e sem
     * armazenar em lugar nenhum.
     */
    public static final String CALL_SIGNAL = "CALL_SIGNAL";

    public static RealtimeEvent of(String type, Object payload) {
        return new RealtimeEvent(type, payload, Instant.now());
    }
}
