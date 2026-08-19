package app.concord.auth;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditOutcome;
import app.concord.audit.AuditService;
import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.text.EmailNormalizer;
import app.concord.email.EmailService;
import app.concord.token.ActionTokenService;
import app.concord.token.ActionTokenType;
import app.concord.token.UserActionToken;
import app.concord.user.User;
import app.concord.user.UserRepository;
import app.concord.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Recuperação de senha por e-mail.
 *
 * <p>{@code forgot} responde sempre da mesma forma, exista a conta ou não.
 * {@code reset} revoga <b>todas</b> as sessões, inclusive a de quem está
 * executando: quem precisou redefinir a senha pode ter perdido o controle dela,
 * e nesse cenário manter qualquer sessão aberta é um risco.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final ActionTokenService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final AuditService auditService;

    public PasswordResetService(UserRepository userRepository, ActionTokenService tokenService,
                                EmailService emailService, PasswordEncoder passwordEncoder,
                                PasswordPolicy passwordPolicy, SessionService sessionService,
                                AuditService auditService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
        this.auditService = auditService;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    ActionTokenService.IssuedToken issued = tokenService.issue(
                            user.getId(), ActionTokenType.PASSWORD_RESET, null);
                    emailService.sendPasswordReset(user.getEmail(), user.getDisplayName(),
                            issued.plainToken());
                    auditService.security(AuditAction.PASSWORD_RESET_REQUESTED,
                            AuditOutcome.SUCCESS, user.getId(), user.getUsername(), Map.of());
                });
    }

    @Transactional
    public void resetPassword(String plainToken, String newPassword) {
        UserActionToken token = tokenService.consume(ActionTokenType.PASSWORD_RESET, plainToken);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }

        passwordPolicy.validate(newPassword, user.getUsername(), user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        int revoked = sessionService.revokeAll(user.getUsername());

        auditService.security(AuditAction.PASSWORD_RESET_COMPLETED, AuditOutcome.SUCCESS,
                user.getId(), user.getUsername(), Map.of("sessionsRevoked", revoked));

        emailService.sendNotice(user.getEmail(), user.getDisplayName(),
                "Sua senha do Concord foi alterada",
                "Senha alterada",
                "A senha da sua conta foi redefinida e todas as sessões foram encerradas. "
                        + "Se não foi você, redefina a senha imediatamente.");
    }
}
