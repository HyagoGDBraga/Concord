package app.concord.user;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditOutcome;
import app.concord.audit.AuditService;
import app.concord.auth.PasswordPolicy;
import app.concord.auth.RegistrationService;
import app.concord.auth.SessionService;
import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.email.EmailService;
import app.concord.privacy.AccountDeletionService;
import app.concord.token.ActionTokenService;
import app.concord.token.ActionTokenType;
import app.concord.token.UserActionToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Operações do usuário sobre a própria conta. */
@Service
public class AccountService {

    private static final String DELETE_CONFIRMATION = "EXCLUIR";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final ActionTokenService tokenService;
    private final EmailService emailService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final AccountDeletionService deletionService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy, ActionTokenService tokenService,
                          EmailService emailService, SessionService sessionService,
                          AuditService auditService, AccountDeletionService deletionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.deletionService = deletionService;
    }

    @Transactional(readOnly = true)
    public User requireById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public User updateProfile(User user, UserDtos.UpdateProfileRequest request) {
        user.setDisplayName(request.displayName().trim());
        user.setBio(request.bio() == null || request.bio().isBlank()
                ? null : request.bio().trim());
        return userRepository.save(user);
    }

    /**
     * Altera a senha. Exige a senha atual e revoga as demais sessões, mantendo
     * a de quem executou a troca.
     */
    @Transactional
    public void changePassword(User user, UserDtos.ChangePasswordRequest request,
                               String currentSessionId) {
        requireCurrentPassword(user, request.currentPassword(), AuditAction.PASSWORD_CHANGED);
        passwordPolicy.validate(request.newPassword(), user.getUsername(), user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        int revoked = sessionService.revokeAllExcept(user.getUsername(), currentSessionId);

        auditService.security(AuditAction.PASSWORD_CHANGED, AuditOutcome.SUCCESS,
                user.getId(), user.getUsername(), Map.of("sessionsRevoked", revoked));

        emailService.sendNotice(user.getEmail(), user.getDisplayName(),
                "Sua senha do Concord foi alterada",
                "Senha alterada",
                "A senha da sua conta foi alterada e as outras sessões foram encerradas. "
                        + "Se não foi você, redefina a senha imediatamente.");
    }

    /**
     * Inicia a troca de e-mail. O endereço atual permanece válido até a
     * confirmação; ambos os endereços são avisados.
     */
    @Transactional
    public void requestEmailChange(User user, UserDtos.ChangeEmailRequest request) {
        requireCurrentPassword(user, request.currentPassword(), AuditAction.EMAIL_CHANGE_REQUESTED);

        String newEmail = RegistrationService.normalizeEmail(request.newEmail());
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "O novo e-mail é igual ao atual");
        }

        // Colisão não é revelada: a resposta é a mesma, e o e-mail de
        // confirmação simplesmente não chega. Caso contrário o endpoint viraria
        // um verificador de contas existentes para quem já está autenticado.
        if (!userRepository.existsByEmailIgnoreCase(newEmail)) {
            ActionTokenService.IssuedToken issued = tokenService.issue(
                    user.getId(), ActionTokenType.EMAIL_CHANGE, newEmail);
            emailService.sendEmailChangeConfirmation(newEmail, user.getDisplayName(),
                    issued.plainToken());
        }

        if (user.getEmail() != null) {
            emailService.sendNotice(user.getEmail(), user.getDisplayName(),
                    "Pedido de troca de e-mail no Concord",
                    "Troca de e-mail solicitada",
                    "Foi pedida a troca do e-mail da sua conta. Até a confirmação no novo "
                            + "endereço, este continua sendo o e-mail ativo. Se não foi você, "
                            + "altere sua senha.");
        }

        auditService.security(AuditAction.EMAIL_CHANGE_REQUESTED, AuditOutcome.SUCCESS,
                user.getId(), user.getUsername(), Map.of());
    }

    @Transactional
    public void confirmEmailChange(String plainToken) {
        UserActionToken token = tokenService.consume(ActionTokenType.EMAIL_CHANGE, plainToken);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        String newEmail = token.getPayload();
        if (newEmail == null || userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }

        user.setEmail(newEmail);
        userRepository.save(user);

        auditService.security(AuditAction.EMAIL_CHANGED, AuditOutcome.SUCCESS,
                user.getId(), user.getUsername(), Map.of());
    }

    /** Exclusão da própria conta. Exige senha e confirmação digitada. */
    @Transactional
    public void deleteOwnAccount(User user, UserDtos.DeleteAccountRequest request) {
        requireCurrentPassword(user, request.currentPassword(),
                AuditAction.ACCOUNT_DELETED_BY_OWNER);

        if (!DELETE_CONFIRMATION.equals(request.confirmation())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Digite " + DELETE_CONFIRMATION + " para confirmar",
                    Map.of("confirmation", "Confirmação incorreta"));
        }
        deletionService.deleteByOwner(user);
    }

    private void requireCurrentPassword(User user, String rawPassword, AuditAction action) {
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            auditService.security(action, AuditOutcome.DENIED, user.getId(), user.getUsername(),
                    Map.of("reason", "wrong_current_password"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Senha atual incorreta");
        }
    }
}
