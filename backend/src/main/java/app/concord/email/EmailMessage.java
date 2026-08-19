package app.concord.email;

/**
 * Mensagem pronta para envio.
 *
 * @param to      destinatário
 * @param subject assunto
 * @param html    corpo em HTML
 * @param text    alternativa em texto puro, para clientes que não renderizam HTML
 */
public record EmailMessage(String to, String subject, String html, String text) {
}
