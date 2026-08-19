package app.concord.admin;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditOutcome;
import app.concord.audit.AuditService;
import app.concord.config.AppProperties;
import app.concord.settings.SettingKey;
import app.concord.settings.SettingsService;
import app.concord.user.User;
import app.concord.user.UserRepository;
import app.concord.user.UserRole;
import app.concord.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

/**
 * Promoção do primeiro administrador.
 *
 * <p>Fluxo: usuário verificado → e-mail corresponde ao bootstrap → ADMIN →
 * audit_log → bootstrap concluído.
 *
 * <p>O estado "concluído" é <b>persistente</b>, guardado em
 * {@code app_settings}. Depois que existe um administrador, alterar a variável
 * de ambiente não promove mais ninguém — a variável sozinha nunca é a fonte da
 * verdade. Nenhuma senha de administrador existe em migration, seed ou código.
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final AppProperties properties;

    public AdminBootstrapService(UserRepository userRepository, SettingsService settingsService,
                                 AuditService auditService, AppProperties properties) {
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * Promove o usuário a ADMIN se todas as condições forem satisfeitas.
     * Silencioso e idempotente quando não são.
     */
    @Transactional
    public void tryPromote(User user) {
        if (settingsService.getBoolean(SettingKey.ADMIN_BOOTSTRAP_COMPLETED, false)) {
            return;
        }

        String configured = properties.bootstrapAdminEmail();
        if (configured == null || configured.isBlank()) {
            return;
        }

        // Já existe administrador: o bootstrap perdeu a razão de ser e é
        // encerrado definitivamente, mesmo que ninguém tenha sido promovido aqui.
        long admins = userRepository.countByRoleAndStatusNot(UserRole.ADMIN, UserStatus.DELETED);
        if (admins > 0) {
            settingsService.setBoolean(SettingKey.ADMIN_BOOTSTRAP_COMPLETED, true, null);
            log.info("Bootstrap de administrador encerrado: já existe ADMIN no sistema");
            return;
        }

        if (user.getStatus() != UserStatus.ACTIVE || user.getEmail() == null) {
            return;
        }
        if (!user.getEmail().equalsIgnoreCase(configured.trim().toLowerCase(Locale.ROOT))) {
            return;
        }

        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        settingsService.setBoolean(SettingKey.ADMIN_BOOTSTRAP_COMPLETED, true, user.getId());

        auditService.admin(AuditAction.ADMIN_BOOTSTRAP_PROMOTED, AuditOutcome.SUCCESS,
                null, "system", user.getId(), Map.of("username", user.getUsername()));

        log.warn("Primeiro administrador promovido: {}. "
                + "Esvazie CONCORD_BOOTSTRAP_ADMIN_EMAIL do ambiente.", user.getUsername());
    }
}
