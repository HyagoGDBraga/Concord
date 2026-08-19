package app.concord.call;

/**
 * Tipo da chamada no momento em que foi iniciada.
 *
 * <p>É o estado inicial, não uma trava: uma chamada de áudio pode ganhar vídeo
 * durante a conversa por renegociação, sem virar outra chamada. O campo serve
 * ao histórico e à interface de convite.
 */
public enum CallType {
    AUDIO,
    VIDEO
}
