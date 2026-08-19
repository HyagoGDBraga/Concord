package app.concord.auth;

import app.concord.admin.AdminBootstrapService;
import app.concord.audit.AuditAction;
import app.concord.audit.AuditOutcome;
import app.concord.audit.AuditService;
import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.text.EmailNormalizer;
import app.concord.config.AppProperties;
import app.concord.email.EmailService;
import app.concord.legal.ConsentService;
import app.concord.settings.SettingKey;
import app.concord.settings.SettingsService;
import app.concord.token.ActionTokenService;
import app.concord.token.ActionTokenType;
import app.concord.token.UserActionToken;
import app.concord.user.User;
import app.concord.user.UserRepository;
import app.concord.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Cadastro, verificação de e-mail e disponibilidade de username.
 *
 * <p>Cadastro é aberto (decisão D-03), com contenção em quatro camadas: limite
 * por IP no filtro, honeypot, verificação obrigatória de e-mail e expurgo
 * automático de contas nunca confirmadas.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final ActionTokenService tokenService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final SettingsService settingsService;
    private final AdminBootstrapService adminBootstrapService;
    private final ConsentService consentService;
    private final AppProperties properties;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               PasswordPolicy passwordPolicy, ActionTokenService tokenService,
                               EmailService emailService, AuditService auditService,
                               SettingsService settingsService,
                               AdminBootstrapService adminBootstrapService,
                               ConsentService consentService,
                               AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.adminBootstrapService = adminBootstrapService;
        this.consentService = consentService;
        this.properties = properties;
    }

    /**
     * Cria uma conta pendente de verificação.
     *
     * <p>Responde sempre da mesma forma quando o e-mail já existe — nesse caso
     * nenhuma conta é criada e um aviso é enviado ao endereço existente. Assim o
     * endpoint não serve para descobrir quem tem conta no sistema.
     *
     * <p>O username, ao contrário, responde de forma distinguível: ele é público
     * por natureza e esconder a colisão tornaria o cadastro impraticável.
     */
    @Transactional
    public void register(AuthDtos.RegisterRequest request) {
        if (!settingsService.getBoolean(SettingKey.REGISTRATION_OPEN,
                properties.registrationOpen())) {
            throw new ApiException(ErrorCode.REGISTRATION_CLOSED);
        }

        // Honeypot: bot preencheu um campo que humanos não veem. Descarta em
        // silêncio, para não sinalizar a existência da armadilha.
        if (request.website() != null && !request.website().isBlank()) {
            log.info("Cadastro descartado pelo honeypot");
            return;
        }

        String username = request.username().trim();
        String email = EmailNormalizer.normalize(request.email());

        passwordPolicy.validate(request.password(), username, email);

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN);
        }

        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User owner = existing.get();
            if (owner.getStatus() != UserStatus.DELETED) {
                emailService.sendRegistrationAttempt(owner.getEmail(), owner.getDisplayName());
            }
            auditService.security(AuditAction.USER_REGISTERED, AuditOutcome.FAILURE,
                    null, null, Map.of("reason", "email_already_registered"));
            return;
        }

        User user = User.newPending(username, email,
                passwordEncoder.encode(request.password()), request.displayName().trim());
        userRepository.save(user);

        ActionTokenService.IssuedToken issued =
                tokenService.issue(user.getId(), ActionTokenType.EMAIL_VERIFICATION, null);
        emailService.sendEmailVerification(email, user.getDisplayName(), issued.plainToken());

        // O formulário de cadastro apresenta os dois documentos; o aceite é
        // registrado com a versão vigente, que é o que dá valor ao registro.
        consentService.acceptAllAtRegistration(user.getId());

        auditService.security(AuditAction.USER_REGISTERED, AuditOutcome.SUCCESS,
                user.getId(), user.getUsername(), Map.of());
    }

    /** Confirma o e-mail e ativa a conta. */
    @Transactional
    public void verifyEmail(String plainToken) {
        UserActionToken token = tokenService.consume(ActionTokenType.EMAIL_VERIFICATION, plainToken);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }

        user.markEmailVerified();
        userRepository.save(user);

        auditService.security(AuditAction.EMAIL_VERIFIED, AuditOutcome.SUCCESS,
                user.getId(), user.getUsername(), Map.of());

        // Momento em que o primeiro administrador pode ser promovido, sem
        // exigir reinício do backend.
        adminBootstrapService.tryPromote(user);
    }

    /**
     * Reenvia a verificação. Responde sempre igual, exista a conta ou não.
     */
    @Transactional
    public void resendVerification(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(user -> {
                    ActionTokenService.IssuedToken issued = tokenService.issue(
                            user.getId(), ActionTokenType.EMAIL_VERIFICATION, null);
                    emailService.sendEmailVerification(user.getEmail(), user.getDisplayName(),
                            issued.plainToken());
                    auditService.security(AuditAction.EMAIL_VERIFICATION_RESENT,
                            AuditOutcome.SUCCESS, user.getId(), user.getUsername(), Map.of());
                });
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        if (username == null || !username.matches("^[A-Za-z0-9_]{3,20}$")) {
            return false;
        }
        return !userRepository.existsByUsernameIgnoreCase(username.trim());
    }
}
