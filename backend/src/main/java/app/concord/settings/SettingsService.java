package app.concord.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Leitura e escrita de configurações persistidas.
 *
 * <p>Sem cache: são duas chaves consultadas em endpoints de baixa frequência
 * (cadastro e inicialização). Um cache aqui só criaria a possibilidade de o
 * painel administrativo alternar o cadastro e o comportamento não mudar.
 */
@Service
public class SettingsService {

    private final AppSettingRepository repository;

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return repository.findById(key)
                .map(setting -> Boolean.parseBoolean(setting.getValue()))
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public boolean exists(String key) {
        return repository.existsById(key);
    }

    @Transactional
    public void setBoolean(String key, boolean value, UUID updatedBy) {
        repository.findById(key).ifPresentOrElse(
                setting -> setting.update(Boolean.toString(value), updatedBy),
                () -> repository.save(new AppSetting(key, Boolean.toString(value), updatedBy)));
    }

    /** Define o valor apenas se a chave ainda não existir. Usado na inicialização. */
    @Transactional
    public void initializeIfAbsent(String key, boolean value) {
        if (!repository.existsById(key)) {
            repository.save(new AppSetting(key, Boolean.toString(value), null));
        }
    }
}
