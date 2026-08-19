package app.concord.user;

/**
 * Estado da conta.
 *
 * <p>Não existe {@code BLOCKED} aqui: bloqueio é uma relação entre usuários
 * ({@code contacts.status = BLOCKED}, Fase 3), não um estado de conta. O
 * bloqueio temporário por falhas de login é {@code users.locked_until} e expira
 * sozinho.
 */
public enum UserStatus {
    /** Cadastrado, e-mail não confirmado. Não loga, não aparece em buscas. */
    PENDING_VERIFICATION,
    /** Operacional. */
    ACTIVE,
    /** Desativado por um administrador. Reversível. */
    DISABLED,
    /** Excluído e anonimizado. Terminal. */
    DELETED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
