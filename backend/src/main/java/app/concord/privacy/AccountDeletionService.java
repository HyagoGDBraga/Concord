package app.concord.privacy;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditLogRepository;
import app.concord.audit.AuditService;
import app.concord.auth.SessionService;
import app.concord.email.EmailService;
import app.concord.token.ActionTokenService;
import app.concord.user.User;
import app.concord.user.UserRepository;
import app.concord.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Exclusão de conta por anonimização (decisão D-05).
 *
 * <p>A linha de {@code users} nunca é removida. Apagá-la quebraria as chaves
 * estrangeiras de {@code messages} na Fase 3 e destruiria o histórico legítimo
 * do interlocutor — mensagens são um dado com dois titulares, e o direito de
 * eliminação de um não anula o do outro.
 *
 * <p>Sobre a auditoria: {@code actor_user_id} é preservado, porque é um
 * pseudônimo estável que mantém a correlação entre eventos, enquanto
 * {@code actor_label} — que contém o username legível — é substituído. É essa
 * separação que permite atender à eliminação sem destruir a trilha de
 * segurança.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ActionTokenService tokenService;
    private final SessionService sessionService;
    private final EmailService emailService;
    private final AuditService auditService;

    public AccountDeletionService(UserRepository userRepository,
                                  AuditLogRepository auditLogRepository,
                                  ActionTokenService tokenService,
                                  SessionService sessionService,
                                  EmailService emailService,
                                  AuditService auditService) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    /** Exclusão pedida pelo próprio titular. */
    @Transactional
    public void deleteByOwner(User user) {
        anonymize(user, user.getId(), user.getUsername(), AuditAction.ACCOUNT_DELETED_BY_OWNER,
                Map.of("initiatedBy", "owner"), true);
    }

    /** Exclusão executada por um administrador, com motivo obrigatório. */
    @Transactional
    public void deleteByAdmin(User target, UUID adminId, String adminLabel, String reason) {
        anonymize(target, adminId, adminLabel, AuditAction.ADMIN_ACCOUNT_DELETED,
                Map.of("initiatedBy", "admin", "reason", reason), true);
    }

    /** Expurgo de conta que nunca confirmou o e-mail. Sem notificação. */
    @Transactional
    public void purgeUnverified(User user) {
        anonymize(user, null, "system", AuditAction.UNVERIFIED_ACCOUNT_PURGED,
                Map.of("initiatedBy", "system", "reason", "never_verified"), false);
    }

    private void anonymize(User user, UUID actorId, String actorLabel, AuditAction action,
                           Map<String, Object> metadata, boolean notify) {
        if (user.getStatus() == UserStatus.DELETED) {
            return;
        }

        String originalEmail = user.getEmail();
        String originalDisplayName = user.getDisplayName();
        String originalUsername = user.getUsername();
        UUID userId = user.getId();

        // 1. Encerrar todo acesso, antes de qualquer outra coisa.
        sessionService.revokeAll(originalUsername);
        tokenService.revokeAll(userId);

        // 2. Notificar enquanto o endereço ainda existe.
        if (notify && originalEmail != null) {
            emailService.sendNotice(originalEmail, originalDisplayName,
                    "Sua conta do Concord foi excluída",
                    "Conta excluída",
                    "Sua conta foi excluída e seus dados pessoais foram removidos. "
                            + "As mensagens que você enviou permanecem nas conversas dos "
                            + "destinatários, exibidas como \"Usuário removido\".");
        }

        // 3. Anonimizar. A ordem importa: o e-mail precisa sair antes do commit,
        //    senão a constraint users_deleted_chk rejeita a transação — e é
        //    exatamente para isso que ela existe.
        String pseudonym = pseudonymFor(userId);
        user.setUsername(pseudonym);
        user.setEmail(null);
        user.setDisplayName("Usuário removido");
        user.setAvatarUrl(null);
        user.setBio(null);
        // Hash aleatório e inutilizável. Nunca vazio: a coluna é NOT NULL e um
        // valor em branco convidaria a um bug de autenticação.
        user.setPasswordHash("{noop-disabled}" + randomToken());
        user.setStatus(UserStatus.DELETED);
        user.setAnonymizedAt(Instant.now());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // 4. Substituir o rótulo legível na auditoria, preservando o vínculo por id.
        auditLogRepository.pseudonymizeActorLabel(userId, pseudonym);

        auditService.privacy(action, actorId, actorLabel, userId, metadata);
        auditService.privacy(AuditAction.ACCOUNT_ANONYMIZED, actorId, actorLabel, userId,
                Map.of("pseudonym", pseudonym));

        log.info("Conta anonimizada: {}", pseudonym);
    }

    static String pseudonymFor(UUID userId) {
        return "removido_" + userId.toString().replace("-", "").substring(0, 8);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
