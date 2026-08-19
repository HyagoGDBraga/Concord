package app.concord.conversation;

/**
 * Tipo de conversa.
 *
 * <p>Só {@code DIRECT} no MVP. O enum e a coluna existem para que grupos entrem
 * sem migration destrutiva — a constraint do banco é que precisará mudar, não
 * o modelo.
 */
public enum ConversationType {
    DIRECT
}
