package app.concord.job;

import app.concord.audit.AuditCategory;
import app.concord.audit.AuditLogRepository;
import app.concord.common.ratelimit.RateLimiter;
import app.concord.email.EmailSuppressionRepository;
import app.concord.legal.UserConsentRepository;
import app.concord.config.AppProperties;
import app.concord.privacy.AccountDeletionService;
import app.concord.token.UserActionTokenRepository;
import app.concord.user.User;
import app.concord.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Tarefas de retenção e limpeza.
 *
 * <p>Concentradas em uma classe: são quatro métodos curtos que compartilham o
 * mesmo propósito. Espalhá-los em quatro classes só aumentaria a distância entre
 * a política de retenção e a sua implementação.
 *
 * <p>Retenção da auditoria (§13.1 do documento de arquitetura):
 * IP anulado em 6 meses; SECURITY apagado em 6 meses; ADMIN em 24 meses;
 * PRIVACY em 60 meses.
 */
@Component
public class CleanupJobs {

    private static final Logger log = LoggerFactory.getLogger(CleanupJobs.class);

    private static final Duration IP_RETENTION = Duration.ofDays(180);
    private static final Duration SECURITY_RETENTION = Duration.ofDays(180);
    private static final Duration ADMIN_RETENTION = Duration.ofDays(730);
    private static final Duration PRIVACY_RETENTION = Duration.ofDays(1825);

    private final UserActionTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccountDeletionService deletionService;
    private final RateLimiter rateLimiter;
    private final UserConsentRepository consentRepository;
    private final EmailSuppressionRepository suppressionRepository;
    private final AppProperties properties;

    public CleanupJobs(UserActionTokenRepository tokenRepository, UserRepository userRepository,
                       AuditLogRepository auditLogRepository,
                       AccountDeletionService deletionService, RateLimiter rateLimiter,
                       UserConsentRepository consentRepository,
                       EmailSuppressionRepository suppressionRepository,
                       AppProperties properties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.deletionService = deletionService;
        this.rateLimiter = rateLimiter;
        this.consentRepository = consentRepository;
        this.suppressionRepository = suppressionRepository;
        this.properties = properties;
    }

    /** Remove tokens vencidos ou já usados. Todo dia às 03:10. */
    @Scheduled(cron = "0 10 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int removed = tokenRepository.deleteExpiredOrUsed(Instant.now());
        if (removed > 0) {
            log.info("Tokens de ação removidos: {}", removed);
        }
    }

    /**
     * Expurga contas que nunca confirmaram o e-mail.
     *
     * <p>É também a forma indireta de lidar com bounce na Fase 2: um endereço
     * inválido nunca confirma, e a conta desaparece sozinha.
     */
    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void purgeUnverifiedAccounts() {
        Instant cutoff = Instant.now().minus(properties.unverifiedAccountTtl());
        List<User> stale = userRepository.findUnverifiedBefore(cutoff);
        for (User user : stale) {
            deletionService.purgeUnverified(user);
        }
        if (!stale.isEmpty()) {
            log.info("Contas não verificadas expurgadas: {}", stale.size());
        }
    }

    /** Aplica a política de retenção da auditoria. Todo dia às 03:30. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void applyAuditRetention() {
        Instant now = Instant.now();

        int scrubbed = auditLogRepository.scrubIpBefore(now.minus(IP_RETENTION));
        int security = auditLogRepository.deleteByCategoryBefore(
                AuditCategory.SECURITY, now.minus(SECURITY_RETENTION));
        int admin = auditLogRepository.deleteByCategoryBefore(
                AuditCategory.ADMIN, now.minus(ADMIN_RETENTION));
        int privacy = auditLogRepository.deleteByCategoryBefore(
                AuditCategory.PRIVACY, now.minus(PRIVACY_RETENTION));

        if (scrubbed + security + admin + privacy > 0) {
            log.info("Retenção da auditoria: {} IPs anulados, {} SECURITY, {} ADMIN, {} PRIVACY removidos",
                    scrubbed, security, admin, privacy);
        }
    }

    /**
     * Retenção dos dados acessórios de privacidade.
     *
     * <p>O IP do aceite sai depois de seis meses: ele dá valor probatório ao
     * consentimento no curto prazo, mas o consentimento em si — quem, qual
     * documento, qual versão, quando — permanece.
     *
     * <p>Bounce temporário também expira: caixa cheia é problema passageiro, e
     * suprimir para sempre por causa disso puniria o usuário por algo já
     * resolvido.
     */
    @Scheduled(cron = "0 50 3 * * *")
    @Transactional
    public void applyPrivacyRetention() {
        Instant now = Instant.now();

        int consentIps = consentRepository.scrubIpBefore(now.minus(IP_RETENTION));
        int softBounces = suppressionRepository.deleteExpiredSoftBounces(
                now.minus(Duration.ofDays(30)));

        if (consentIps + softBounces > 0) {
            log.info("Retenção de privacidade: {} IPs de consentimento anulados, "
                    + "{} supressões temporárias liberadas", consentIps, softBounces);
        }
    }

    /** Libera janelas de rate limit já vencidas. A cada hora. */
    @Scheduled(fixedDelay = 3_600_000L)
    public void purgeRateLimitWindows() {
        int removed = rateLimiter.purgeExpired();
        if (removed > 0) {
            log.debug("Janelas de rate limit liberadas: {}", removed);
        }
    }
}
