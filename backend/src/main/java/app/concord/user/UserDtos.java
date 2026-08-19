package app.concord.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTOs de perfil, conta e sessões. */
public final class UserDtos {

    private UserDtos() {
    }

    /** Perfil do próprio usuário autenticado. */
    public record MeResponse(
            UUID id,
            String username,
            String email,
            String displayName,
            String avatarUrl,
            String bio,
            UserRole role,
            UserStatus status,
            Instant createdAt,
            Instant lastLoginAt
    ) {
        public static MeResponse from(User user) {
            return new MeResponse(user.getId(), user.getUsername(), user.getEmail(),
                    user.getDisplayName(), user.getAvatarUrl(), user.getBio(),
                    user.getRole(), user.getStatus(), user.getCreatedAt(),
                    user.getLastLoginAt());
        }
    }

    /**
     * Perfil visível para outros usuários. Sem e-mail, sem estado da conta, sem
     * datas de acesso — é o contrato que a Fase 3 usará na lista de contatos.
     */
    public record PublicUserResponse(
            UUID id,
            String username,
            String displayName,
            String avatarUrl,
            String bio
    ) {
        public static PublicUserResponse from(User user) {
            return new PublicUserResponse(user.getId(), user.getUsername(),
                    user.getDisplayName(), user.getAvatarUrl(), user.getBio());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Informe um nome de exibição")
            @Size(min = 1, max = 50)
            String displayName,

            @Size(max = 200, message = "A bio pode ter até 200 caracteres")
            String bio
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Informe a senha atual")
            @Size(max = 128)
            String currentPassword,

            @NotBlank(message = "Informe a nova senha")
            @Size(min = 12, max = 128)
            String newPassword
    ) {
    }

    public record ChangeEmailRequest(
            @NotBlank(message = "Informe a senha atual")
            @Size(max = 128)
            String currentPassword,

            @NotBlank(message = "Informe o novo e-mail")
            @Email(message = "Formato de e-mail inválido")
            @Size(max = 254)
            String newEmail
    ) {
    }

    public record DeleteAccountRequest(
            @NotBlank(message = "Informe sua senha para confirmar")
            @Size(max = 128)
            String currentPassword,

            @NotBlank(message = "Digite EXCLUIR para confirmar")
            String confirmation
    ) {
    }

    /** Sessão ativa, como mostrada ao titular. */
    public record SessionResponse(
            String id,
            Instant createdAt,
            Instant lastAccessedAt,
            String ipAddress,
            String userAgent,
            boolean current
    ) {
    }
}
