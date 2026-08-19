package app.concord.auth;

import app.concord.token.ActionTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O hash do token precisa ser determinístico (para a busca funcionar) e ter
 * sempre 64 caracteres hexadecimais — a constraint
 * {@code user_action_tokens_hash_chk} depende disso.
 */
class ActionTokenHashTest {

    @Test
    @DisplayName("hash é determinístico e tem 64 caracteres hexadecimais")
    void hashDeterministico() {
        String hash = ActionTokenService.hash("token-de-exemplo");

        assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(ActionTokenService.hash("token-de-exemplo")).isEqualTo(hash);
    }

    @Test
    @DisplayName("tokens diferentes produzem hashes diferentes")
    void hashesDistintos() {
        assertThat(ActionTokenService.hash("token-a"))
                .isNotEqualTo(ActionTokenService.hash("token-b"));
    }
}
