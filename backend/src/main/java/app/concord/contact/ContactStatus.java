package app.concord.contact;

/**
 * Estado de uma relação de contato.
 *
 * <p>Não existe {@code BLOCKED} aqui. Bloquear é uma ação unidirecional, vive na
 * tabela {@code blocks} e não destrói o registro de que as duas pessoas eram
 * contatos — se o bloqueio for desfeito, a relação continua de pé.
 */
public enum ContactStatus {
    PENDING,
    ACCEPTED
}
