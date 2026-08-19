package app.concord.admin;

import app.concord.config.AppProperties;
import app.concord.settings.SettingKey;
import app.concord.settings.SettingsService;
import app.concord.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Inicialização: garante a configuração padrão e tenta o bootstrap do
 * administrador para quem já se cadastrou e verificou o e-mail antes de a
 * variável ser preenchida.
 *
 * <p>A tentativa também ocorre no momento da verificação de e-mail, de modo que
 * o caminho normal não exige reiniciar o backend.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final SettingsService settingsService;
    private final UserRepository userRepository;
    private final AdminBootstrapService bootstrapService;
    private final AppProperties properties;

    public AdminBootstrapRunner(SettingsService settingsService, UserRepository userRepository,
                                AdminBootstrapService bootstrapService, AppProperties properties) {
        this.settingsService = settingsService;
        this.userRepository = userRepository;
        this.bootstrapService = bootstrapService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        // O valor do ambiente só define o padrão inicial. Depois disso, quem
        // manda é o app_settings, alterável pelo painel administrativo.
        settingsService.initializeIfAbsent(SettingKey.REGISTRATION_OPEN,
                properties.registrationOpen());

        String email = properties.bootstrapAdminEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        if (settingsService.getBoolean(SettingKey.ADMIN_BOOTSTRAP_COMPLETED, false)) {
            log.info("Bootstrap de administrador já concluído. Variável de ambiente ignorada.");
            return;
        }
        userRepository.findByEmailIgnoreCase(email.trim())
                .ifPresent(bootstrapService::tryPromote);
    }
}
