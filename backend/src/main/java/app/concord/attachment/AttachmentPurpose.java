package app.concord.attachment;

import java.time.Duration;

/**
 * Para que serve o anexo — e, por consequência, quanto tempo ele vive.
 */
public enum AttachmentPurpose {

    /** Foto de perfil. Permanente enquanto for a foto atual. */
    AVATAR(null),

    SERVER_ICON(null),

    SERVER_BANNER(null),

    /**
     * Arquivo enviado numa mensagem. Expira em 14 dias.
     *
     * <p>Não é economia de disco por si só: é a decisão de que o Concord não é
     * um serviço de armazenamento. Guardar arquivo de terceiro indefinidamente
     * cria responsabilidade sobre conteúdo que ninguém revisa e transforma o
     * backup num acervo que cresce sem teto.
     */
    MESSAGE(Duration.ofDays(14));

    private final Duration retention;

    AttachmentPurpose(Duration retention) {
        this.retention = retention;
    }

    /** {@code null} quando o anexo não expira. */
    public Duration retention() {
        return retention;
    }

    public boolean isImageOnly() {
        return this != MESSAGE;
    }
}
