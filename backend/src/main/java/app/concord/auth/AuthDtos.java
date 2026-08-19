package app.concord.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTOs dos endpoints de autenticação.
 *
 * <p>Agrupados como records aninhados em vez de um arquivo por record: são nove
 * estruturas pequenas, sempre lidas em conjunto, e nove arquivos de dez linhas
 * dificultariam mais a leitura do que ajudariam.
 *
 * <p>Entidades JPA nunca são expostas na API. Estes são os contratos.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Informe um nome de usuário")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$",
                    message = "Use de 3 a 20 caracteres: letras, números e _")
            String username,

            @NotBlank(message = "Informe um e-mail")
            @Email(message = "Formato de e-mail inválido")
            @Size(max = 254, message = "E-mail muito longo")
            String email,

            @NotBlank(message = "Informe uma senha")
            @Size(min = 12, max = 128, message = "A senha precisa ter entre 12 e 128 caracteres")
            String password,

            @NotBlank(message = "Informe um nome de exibição")
            @Size(min = 1, max = 50, message = "O nome de exibição pode ter até 50 caracteres")
            String displayName,

            /**
             * Campo honeypot. Fica oculto no formulário; humanos não o preenchem.
             * Se vier com conteúdo, a requisição é descartada silenciosamente com
             * resposta de sucesso, para não ensinar o bot a contorná-lo.
             */
            String website
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Informe seu usuário ou e-mail")
            @Size(max = 254)
            String usernameOrEmail,

            @NotBlank(message = "Informe sua senha")
            @Size(max = 128)
            String password
    ) {
    }

    public record TokenRequest(
            @NotBlank(message = "Token ausente")
            @Size(max = 256)
            String token
    ) {
    }

    public record ResendVerificationRequest(
            @NotBlank @Email @Size(max = 254)
            String email
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email @Size(max = 254)
            String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Token ausente")
            @Size(max = 256)
            String token,

            @NotBlank(message = "Informe a nova senha")
            @Size(min = 12, max = 128, message = "A senha precisa ter entre 12 e 128 caracteres")
            String newPassword
    ) {
    }

    /** Resposta genérica para operações que não devem revelar resultado. */
    public record AcceptedResponse(String message) {

        public static AcceptedResponse of(String message) {
            return new AcceptedResponse(message);
        }
    }

    public record UsernameAvailabilityResponse(String username, boolean available) {
    }
}
