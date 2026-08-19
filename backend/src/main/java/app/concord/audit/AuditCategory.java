package app.concord.audit;

/**
 * Categoria do evento auditado. Determina a política de retenção aplicada pelo
 * {@code AuditRetentionJob}.
 */
public enum AuditCategory {
    /** Autenticação e integridade de conta. Retenção: 6 meses. */
    SECURITY,
    /** Ações executadas por um administrador. Retenção: 24 meses. */
    ADMIN,
    /** Exercício de direitos do titular. Retenção: 60 meses, já sem IP. */
    PRIVACY
}
