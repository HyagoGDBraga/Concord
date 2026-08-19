package app.concord.auth;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes da política de senha. Puramente unitário, sem contexto Spring. */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("aceita senha longa e não trivial")
    void aceitaSenhaValida() {
        assertThatCode(() -> policy.validate("corrente-azul-38-vento", "maria", "maria@x.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejeita senha com menos de 12 caracteres")
    void rejeitaSenhaCurta() {
        assertThatThrownBy(() -> policy.validate("abc123", "maria", "maria@x.com"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.WEAK_PASSWORD);
    }

    @Test
    @DisplayName("rejeita senha presente na lista de comuns")
    void rejeitaSenhaComum() {
        assertThatThrownBy(() -> policy.validate("senha12345678", "maria", "maria@x.com"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("rejeita senha que contém o nome de usuário")
    void rejeitaSenhaComUsername() {
        assertThatThrownBy(() -> policy.validate("mariageorgina1234", "maria", "maria@x.com"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("nome de usuário");
    }

    @Test
    @DisplayName("rejeita senha que contém a parte local do e-mail")
    void rejeitaSenhaComEmail() {
        assertThatThrownBy(() -> policy.validate("joaosilva-abcdefgh", "outro", "joaosilva@x.com"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("e-mail");
    }

    @Test
    @DisplayName("rejeita caractere único repetido")
    void rejeitaCaractereRepetido() {
        assertThatThrownBy(() -> policy.validate("aaaaaaaaaaaaaaaa", "maria", "maria@x.com"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("não exige maiúscula, número ou símbolo (NIST 800-63B)")
    void naoExigeComposicao() {
        assertThat(PasswordPolicy.MIN_LENGTH).isEqualTo(12);
        assertThatCode(() -> policy.validate("cavalobateriagrampo", "maria", "maria@x.com"))
                .doesNotThrowAnyException();
    }
}
