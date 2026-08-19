package app.concord.user;

/**
 * Papel global. Único papel administrativo por decisão D-04 — sem níveis, sem
 * permissões granulares. A coluna comporta valores futuros sem migração
 * destrutiva, caso um dia isso mude.
 */
public enum UserRole {
    USER,
    ADMIN
}
