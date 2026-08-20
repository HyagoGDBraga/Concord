package app.concord.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Catálogo fechado de erros da API.
 *
 * <p>O cliente reage ao {@code code}, nunca ao texto da mensagem — textos mudam,
 * códigos não. As mensagens aqui são deliberadamente genéricas nos casos ligados
 * a autenticação, para não revelar se uma conta existe.
 */
public enum ErrorCode {

    // --- validação e requisição
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dados inválidos"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Requisição malformada"),

    // --- autenticação
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos"),
    NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED, "Autenticação necessária"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Confirme seu e-mail antes de entrar"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Esta conta está desativada"),
    ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente novamente em alguns minutos"),

    // --- autorização
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acesso negado"),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "Token de segurança inválido. Recarregue a página"),

    // --- recursos
    NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Usuário não encontrado"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Sessão não encontrada"),

    // --- cadastro
    REGISTRATION_CLOSED(HttpStatus.FORBIDDEN, "O cadastro está fechado no momento"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "Este nome de usuário já está em uso"),
    WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "Senha muito fraca"),

    // --- tokens de ação
    TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Link inválido ou já utilizado"),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "Link expirado. Solicite um novo"),

    // --- administração
    LAST_ADMIN(HttpStatus.CONFLICT, "Não é possível remover o último administrador"),
    CANNOT_TARGET_SELF(HttpStatus.CONFLICT, "Esta ação não pode ser aplicada à própria conta"),

    // --- contatos e conversas
    CONTACT_NOT_FOUND(HttpStatus.NOT_FOUND, "Contato não encontrado"),
    CONTACT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Já existe uma relação com este usuário"),
    CANNOT_TARGET_SELF_CONTACT(HttpStatus.BAD_REQUEST, "Você não pode adicionar a si mesmo"),
    NOT_CONTACTS(HttpStatus.FORBIDDEN, "Vocês precisam ser contatos para conversar"),
    BLOCKED(HttpStatus.FORBIDDEN, "Não é possível enviar mensagens para este usuário"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversa não encontrada"),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Mensagem não encontrada"),
    NOT_MESSAGE_AUTHOR(HttpStatus.FORBIDDEN, "Só o autor pode alterar a mensagem"),

    // --- servidores e canais
    SERVER_NOT_FOUND(HttpStatus.NOT_FOUND, "Servidor não encontrado"),
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "Canal não encontrado"),
    CHANNEL_NAME_TAKEN(HttpStatus.CONFLICT, "Já existe um canal com esse nome"),
    MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Este usuário já é membro do servidor"),
    CANNOT_REMOVE_OWNER(HttpStatus.CONFLICT, "O proprietário não pode ser removido"),
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "Convite não encontrado"),
    INVITE_EXPIRED(HttpStatus.BAD_REQUEST, "Este convite expirou"),

    // --- chamadas
    CALL_NOT_FOUND(HttpStatus.NOT_FOUND, "Chamada não encontrada"),
    CALL_NOT_RINGING(HttpStatus.CONFLICT, "Esta chamada não está mais tocando"),
    CALL_ALREADY_ACTIVE(HttpStatus.CONFLICT, "Você já está em uma chamada"),
    CALLEE_BUSY(HttpStatus.CONFLICT, "A pessoa já está em outra chamada"),
    CALLEE_UNAVAILABLE(HttpStatus.CONFLICT, "A pessoa não está disponível no momento"),

    // --- limites
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Muitas requisições. Aguarde um momento"),

    // --- genérico
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
