package app.concord.auth;

/**
 * Atributos gravados na sessão no momento do login.
 *
 * <p>Servem exclusivamente para a tela "Sessões ativas", onde o titular
 * reconhece um acesso que não é dele. Vivem em
 * {@code SPRING_SESSION_ATTRIBUTES} e morrem com a sessão — não vão para o
 * {@code audit_log}, que tem retenção muito maior.
 */
public final class SessionAttributes {

    public static final String CREATED_AT = "concord.createdAt";
    public static final String IP = "concord.ip";
    public static final String USER_AGENT = "concord.userAgent";
    public static final String USER_ID = "concord.userId";

    private SessionAttributes() {
    }
}
