package app.concord.ws;

/**
 * Destinos STOMP do Concord.
 *
 * <p>Só existem destinos de usuário. Não há {@code /topic/} algum — um tópico
 * por conversa exigiria autorizar cada subscrição por destino, e um erro nessa
 * autorização entregaria conversa alheia. Com destino de usuário, o Spring
 * resolve o principal da sessão e ninguém consegue assinar a fila de outra
 * pessoa, independentemente do que enviar no frame SUBSCRIBE.
 *
 * <p>O preço é enviar uma cópia por destinatário em vez de uma só. Em conversa
 * direta, isso significa duas.
 */
public final class WsDestinations {

    /** Prefixo das mensagens que o cliente envia ao servidor. */
    public static final String APPLICATION_PREFIX = "/app";

    /** Prefixo do broker simples, em memória. */
    public static final String BROKER_PREFIX = "/queue";

    /** Prefixo de destino de usuário, resolvido pelo Spring por principal. */
    public static final String USER_PREFIX = "/user";

    /** Fila única de eventos, assinada como {@code /user/queue/events}. */
    public static final String EVENTS = "/queue/events";

    /** Único formato de subscrição aceito pelo interceptor de entrada. */
    public static final String ALLOWED_SUBSCRIPTION_PREFIX = "/user/queue/";

    private WsDestinations() {
    }
}
