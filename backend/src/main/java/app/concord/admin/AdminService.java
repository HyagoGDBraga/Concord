package app.concord.admin;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditCategory;
import app.concord.audit.AuditLog;
import app.concord.audit.AuditLogRepository;
import app.concord.audit.AuditOutcome;
import app.concord.audit.AuditService;
import app.concord.auth.SessionService;
import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.email.EmailService;
import app.concord.privacy.AccountDeletionService;
import app.concord.settings.SettingKey;
import app.concord.settings.SettingsService;
import app.concord.user.User;
import app.concord.user.UserRepository;
import app.concord.user.UserRole;
import app.concord.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Operações administrativas.
 *
 * <p>Escopo fechado (decisão D-04): listar, pesquisar, ver estado, desativar,
 * reativar, encerrar sessões, excluir, consultar auditoria e alternar o
 * cadastro. Não há e não deve haver nenhum método que retorne conteúdo de
 * mensagem, mídia ou lista de contatos de terceiros.
 *
 * <p>Duas salvaguardas estruturais: o último administrador não pode ser
 * desativado nem excluído, e nenhum administrador pode aplicar essas ações à
 * própria conta.
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final SessionService sessionService;
    private final AccountDeletionService deletionService;
    private final SettingsService settingsService;
    private final EmailService emailService;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository, AuditLogRepository auditLogRepository,
                        SessionService sessionService, AccountDeletionService deletionService,
                        SettingsService settingsService, EmailService emailService,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.sessionService = sessionService;
        this.deletionService = deletionService;
        this.settingsService = settingsService;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<User> searchUsers(String query, UserStatus status, Pageable pageable) {
        String normalized = (query == null || query.isBlank()) ? null : query.trim();
        return userRepository.search(normalized, status, pageable);
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public User disable(UUID targetId, User admin, String reason) {
        User target = requireUser(targetId);
        guardSelf(target, admin);
        guardLastAdmin(target);

        if (target.getStatus() == UserStatus.DELETED) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }

        target.disable(reason);
        userRepository.save(target);

        int revoked = sessionService.revokeAll(target.getUsername());

        auditService.admin(AuditAction.ADMIN_ACCOUNT_DISABLED, AuditOutcome.SUCCESS,
                admin.getId(), admin.getUsername(), target.getId(),
                Map.of("reason", reason, "sessionsRevoked", revoked));

        if (target.getEmail() != null) {
            emailService.sendNotice(target.getEmail(), target.getDisplayName(),
                    "Sua conta do Concord foi desativada",
                    "Conta desativada",
                    "Sua conta foi desativada por um administrador e as sessões abertas foram "
                            + "encerradas. Motivo informado: " + reason);
        }
        return target;
    }

    @Transactional
    public User enable(UUID targetId, User admin) {
        User target = requireUser(targetId);
        if (target.getStatus() == UserStatus.DELETED) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        target.enable();
        userRepository.save(target);

        auditService.admin(AuditAction.ADMIN_ACCOUNT_ENABLED, AuditOutcome.SUCCESS,
                admin.getId(), admin.getUsername(), target.getId(), Map.of());

        if (target.getEmail() != null) {
            emailService.sendNotice(target.getEmail(), target.getDisplayName(),
                    "Sua conta do Concord foi reativada",
                    "Conta reativada",
                    "Sua conta voltou a ficar ativa. Você já pode entrar normalmente.");
        }
        return target;
    }

    @Transactional
    public int revokeSessions(UUID targetId, User admin) {
        User target = requireUser(targetId);
        int revoked = sessionService.revokeAll(target.getUsername());
        auditService.admin(AuditAction.ADMIN_SESSIONS_REVOKED, AuditOutcome.SUCCESS,
                admin.getId(), admin.getUsername(), target.getId(),
                Map.of("sessionsRevoked", revoked));
        return revoked;
    }

    @Transactional
    public void deleteUser(UUID targetId, User admin, String reason) {
        User target = requireUser(targetId);
        guardSelf(target, admin);
        guardLastAdmin(target);
        deletionService.deleteByAdmin(target, admin.getId(), admin.getUsername(), reason);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> searchAudit(AuditCategory category, AuditAction action, UUID userId,
                                      Instant from, Instant to, Pageable pageable) {
        return auditLogRepository.search(category, action, userId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public AdminDtos.SettingsResponse getSettings(boolean registrationOpenDefault) {
        return new AdminDtos.SettingsResponse(
                settingsService.getBoolean(SettingKey.REGISTRATION_OPEN, registrationOpenDefault),
                settingsService.getBoolean(SettingKey.ADMIN_BOOTSTRAP_COMPLETED, false));
    }

    @Transactional
    public void setRegistrationOpen(boolean open, User admin) {
        settingsService.setBoolean(SettingKey.REGISTRATION_OPEN, open, admin.getId());
        auditService.admin(AuditAction.ADMIN_SETTING_CHANGED, AuditOutcome.SUCCESS,
                admin.getId(), admin.getUsername(), null,
                Map.of("key", SettingKey.REGISTRATION_OPEN, "value", open));
    }

    private void guardSelf(User target, User admin) {
        if (target.getId().equals(admin.getId())) {
            throw new ApiException(ErrorCode.CANNOT_TARGET_SELF);
        }
    }

    private void guardLastAdmin(User target) {
        if (target.getRole() != UserRole.ADMIN) {
            return;
        }
        long remaining = userRepository.countByRoleAndStatusNot(UserRole.ADMIN, UserStatus.DELETED);
        if (remaining <= 1) {
            throw new ApiException(ErrorCode.LAST_ADMIN);
        }
    }
}
