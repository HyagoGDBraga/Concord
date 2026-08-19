package app.concord.token;

import java.time.Duration;

/**
 * Ações pontuais confirmadas por link enviado ao e-mail do usuário.
 *
 * <p>Conjunto deliberadamente fechado. Esta não é uma tabela genérica de tokens:
 * a autenticação do Concord é por sessão em cookie, e aqui não entram tokens de
 * acesso, refresh tokens ou JWT. Adicionar um tipo exige alterar este enum e a
 * constraint da migration — o atrito é intencional.
 */
public enum ActionTokenType {

    EMAIL_VERIFICATION(Duration.ofHours(24)),
    PASSWORD_RESET(Duration.ofMinutes(30)),
    EMAIL_CHANGE(Duration.ofHours(2));

    private final Duration defaultTtl;

    ActionTokenType(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Duration defaultTtl() {
        return defaultTtl;
    }
}
