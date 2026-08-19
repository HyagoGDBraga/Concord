package app.concord.admin;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditCategory;
import app.concord.audit.AuditLog;
import app.concord.audit.AuditOutcome;
import app.concord.user.User;
import app.concord.user.UserRole;
import app.concord.user.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs do painel administrativo.
 *
 * <p>Nenhum deles carrega conteúdo privado. Não existe DTO de mensagem, de
 * conversa ou de mídia sob {@code /admin} — e essa ausência é o controle, não
 * uma configuração que possa ser ligada.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Visão administrativa de uma conta: estado e datas, nada de conteúdo. */
    public record AdminUserResponse(
            UUID id,
            String username,
            String email,
            String displayName,
            UserRole role,
            UserStatus status,
            Instant emailVerifiedAt,
            Instant lastLoginAt,
            Instant disabledAt,
            String disabledReason,
            boolean temporarilyLocked,
            Instant lockedUntil,
            int failedLoginCount,
            Instant createdAt
    ) {
        public static AdminUserResponse from(User user) {
            return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(),
                    user.getDisplayName(), user.getRole(), user.getStatus(),
                    user.getEmailVerifiedAt(), user.getLastLoginAt(), user.getDisabledAt(),
                    user.getDisabledReason(), user.isTemporarilyLocked(), user.getLockedUntil(),
                    user.getFailedLoginCount(), user.getCreatedAt());
        }
    }

    public record AuditLogResponse(
            Long id,
            Instant createdAt,
            AuditCategory category,
            AuditAction action,
            AuditOutcome outcome,
            UUID actorUserId,
            String actorLabel,
            UUID targetUserId,
            String ipAddress,
            Map<String, Object> metadata
    ) {
        public static AuditLogResponse from(AuditLog entry) {
            return new AuditLogResponse(entry.getId(), entry.getCreatedAt(), entry.getCategory(),
                    entry.getAction(), entry.getOutcome(), entry.getActorUserId(),
                    entry.getActorLabel(), entry.getTargetUserId(), entry.getIpAddress(),
                    entry.getMetadata());
        }
    }

    public record DisableUserRequest(
            @NotBlank(message = "Informe o motivo da desativação")
            @Size(min = 3, max = 200)
            String reason
    ) {
    }

    public record DeleteUserRequest(
            @NotBlank(message = "Informe o motivo da exclusão")
            @Size(min = 3, max = 200)
            String reason
    ) {
    }

    public record SettingsResponse(boolean registrationOpen, boolean adminBootstrapCompleted) {
    }

    public record UpdateSettingsRequest(
            @NotNull(message = "Informe o valor")
            Boolean registrationOpen
    ) {
    }

    public record RevokedSessionsResponse(int revoked) {
    }
}
