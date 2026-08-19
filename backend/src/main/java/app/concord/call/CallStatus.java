package app.concord.call;

/** Estados possíveis de uma chamada. Terminal: {@code ENDED}. */
public enum CallStatus {
    /** Convite enviado, aguardando resposta. */
    RINGING,
    /** Atendida; a mídia está fluindo entre os pares. */
    ACTIVE,
    /** Encerrada, por qualquer motivo. */
    ENDED
}
