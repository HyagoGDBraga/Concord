package app.concord.common.text;

import java.util.Locale;

/**
 * Normalização de endereços de e-mail.
 *
 * <p>Regra única do sistema: minúsculas e sem espaços nas pontas. Todo lugar que
 * grava, busca ou compara um e-mail passa por aqui — cadastro, login,
 * recuperação de senha, troca de endereço e lista de supressão. Se cada um
 * normalizasse à sua maneira, a mesma pessoa acabaria com duas contas por causa
 * de uma maiúscula.
 *
 * <p>A normalização para deliberadamente por aqui. Remover pontos ou tudo depois
 * de um {@code +} é comportamento específico do Gmail, e aplicá-lo
 * universalmente quebraria provedores em que essas partes são significativas —
 * o endereço deixaria de existir.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /**
     * @param email endereço bruto, possivelmente nulo
     * @return endereço normalizado, ou {@code null} se a entrada era nula
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
