package app.concord.email;

/** Falha ao entregar a mensagem ao provedor. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
