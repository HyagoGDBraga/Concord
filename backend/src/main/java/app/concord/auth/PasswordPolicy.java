package app.concord.auth;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Política de senha, seguindo a NIST SP 800-63B.
 *
 * <p>Comprimento mínimo alto e verificação contra senhas conhecidas, sem exigir
 * "uma maiúscula, um número e um símbolo". Regras de composição empurram as
 * pessoas para padrões previsíveis do tipo {@code Senha@123}, que satisfazem a
 * regra e são triviais de quebrar.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        this.commonPasswords = loadCommonPasswords();
    }

    /**
     * @throws ApiException {@code WEAK_PASSWORD} com a razão específica
     */
    public void validate(String password, String username, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD,
                    "A senha precisa ter pelo menos " + MIN_LENGTH + " caracteres");
        }
        if (password.length() > MAX_LENGTH) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD,
                    "A senha pode ter no máximo " + MAX_LENGTH + " caracteres");
        }

        String normalized = password.toLowerCase(Locale.ROOT);

        if (commonPasswords.contains(normalized)) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD,
                    "Esta senha é muito comum. Escolha outra");
        }
        if (username != null && normalized.contains(username.toLowerCase(Locale.ROOT))) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD,
                    "A senha não pode conter seu nome de usuário");
        }
        if (email != null) {
            String localPart = email.contains("@")
                    ? email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT)
                    : email.toLowerCase(Locale.ROOT);
            if (localPart.length() >= 3 && normalized.contains(localPart)) {
                throw new ApiException(ErrorCode.WEAK_PASSWORD,
                        "A senha não pode conter seu e-mail");
            }
        }
        if (isSingleRepeatedCharacter(normalized)) {
            throw new ApiException(ErrorCode.WEAK_PASSWORD,
                    "A senha não pode ser um único caractere repetido");
        }
    }

    private static boolean isSingleRepeatedCharacter(String value) {
        return value.chars().distinct().count() == 1;
    }

    private static Set<String> loadCommonPasswords() {
        ClassPathResource resource = new ClassPathResource("security/common-passwords.txt");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException ex) {
            throw new IllegalStateException("Lista de senhas comuns ausente", ex);
        }
    }
}
