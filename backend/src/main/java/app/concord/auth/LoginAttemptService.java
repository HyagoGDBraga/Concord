package app.concord.auth;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditOutcome;
import app.concord.audit.AuditService;
import app.concord.config.AppProperties;
import app.concord.user.User;
import app.concord.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Contagem de falhas de login e bloqueio temporário por conta.
 *
 * <p>Complementa o limite por IP do {@code RateLimitFilter}: o limite por IP
 * contém automação vinda de um endereço; o bloqueio por conta contém ataque
 * distribuído contra um alvo específico.
 *
 * <p>Backoff exponencial a partir do limite configurado, com teto. O contador é
 * zerado em qualquer login bem-sucedido.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AppProperties properties;

    public LoginAttemptService(UserRepository userRepository, AuditService auditService,
                               AppProperties properties) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * Registra uma falha em transação própria: o fluxo de login lança exceção e
     * desfaz a sua transação, mas o contador precisa persistir.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(User user) {
        User managed = userRepository.findById(user.getId()).orElse(null);
        if (managed == null) {
            return;
        }
        int failures = managed.getFailedLoginCount() + 1;
        managed.setFailedLoginCount(failures);

        int threshold = properties.login().maxFailedAttempts();
        if (failures >= threshold) {
            Duration lock = backoff(failures - threshold);
            managed.setLockedUntil(Instant.now().plus(lock));
            userRepository.save(managed);
            auditService.security(AuditAction.ACCOUNT_LOCKED, AuditOutcome.SUCCESS,
                    managed.getId(), managed.getUsername(),
                    Map.of("failures", failures, "lockSeconds", lock.toSeconds()));
            return;
        }
        userRepository.save(managed);
    }

    @Transactional
    public void registerSuccess(User user) {
        user.registerSuccessfulLogin();
        userRepository.save(user);
    }

    /**
     * Duração do bloqueio: base * 2^excedente, limitada pelo máximo configurado.
     * Com base de 1 minuto e teto de 15: 1, 2, 4, 8, 15, 15...
     */
    private Duration backoff(int exceededBy) {
        Duration base = properties.login().lockBaseDuration();
        Duration max = properties.login().lockMaxDuration();
        long multiplier = 1L << Math.min(exceededBy, 20);
        Duration candidate = base.multipliedBy(multiplier);
        return candidate.compareTo(max) > 0 ? max : candidate;
    }
}
