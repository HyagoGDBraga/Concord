package app.concord.call;

/** Por que a chamada terminou. */
public enum CallEndReason {
    /** Alguém desligou uma chamada em andamento. */
    HANGUP,
    /** O destinatário recusou. */
    REJECTED,
    /** Ninguém atendeu dentro do prazo. */
    MISSED,
    /** O destinatário já estava em outra chamada. */
    BUSY,
    /** Queda de conexão ou falha na negociação. */
    FAILED,
    /** Quem ligou desistiu antes de o outro atender. */
    CANCELLED
}
