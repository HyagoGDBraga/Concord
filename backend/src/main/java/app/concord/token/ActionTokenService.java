package app.concord.token;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Emissão e consumo de tokens de ação.
 *
 * <p>Regras invioláveis:
 * <ul>
 *   <li>256 bits de {@link SecureRandom}, codificados em Base64 URL-safe;</li>
 *   <li>apenas o SHA-256 é persistido — nunca o valor em texto puro;</li>
 *   <li>uso único: consumir marca o token e um segundo uso é recusado;</li>
 *   <li>o token nunca aparece em log, nem em nível de depuração.</li>
 * </ul>
 *
 * <p>SHA-256 sem salt é adequado aqui, ao contrário do que vale para senhas: o
 * token já tem 256 bits de entropia aleatória, então não há espaço de busca a
 * ser protegido por um KDF lento.
 */
@Service
public class ActionTokenService {

    /** Token emitido: o valor em texto puro só existe neste objeto, em memória. */
    public record IssuedToken(String plainToken, UserActionToken entity) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final UserActionTokenRepository repository;

    public ActionTokenService(UserActionTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IssuedToken issue(UUID userId, ActionTokenType action, String payload) {
        return issue(userId, action, payload, action.defaultTtl());
    }

    @Transactional
    public IssuedToken issue(UUID userId, ActionTokenType action, String payload, Duration ttl) {
        // Emitir um token novo invalida os anteriores da mesma ação: um link de
        // reset antigo, possivelmente vazado, deixa de valer.
        repository.deleteByUserAndAction(userId, action);

        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        UserActionToken entity = new UserActionToken(
                userId, action, hash(plainToken), payload, Instant.now().plus(ttl));
        repository.save(entity);

        return new IssuedToken(plainToken, entity);
    }

    /**
     * Valida e consome um token.
     *
     * @throws ApiException {@code TOKEN_INVALID} se não existir ou já tiver sido
     *                      usado; {@code TOKEN_EXPIRED} se estiver vencido.
     */
    @Transactional
    public UserActionToken consume(ActionTokenType action, String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        UserActionToken token = repository
                .findByTokenHashAndAction(hash(plainToken), action)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (token.isUsed()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        if (token.isExpired()) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }
        token.markUsed();
        return repository.save(token);
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.deleteAllByUser(userId);
    }

    static String hash(String plainToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(plainToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", ex);
        }
    }
}
