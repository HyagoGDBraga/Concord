package app.concord.support;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Geradores de dados de teste. */
public final class TestData {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    /** Senha válida pela política: 12+ caracteres, fora da lista de comuns. */
    public static final String VALID_PASSWORD = "corrente-azul-38-vento";

    private TestData() {
    }

    public static String uniqueUsername() {
        return "user_" + COUNTER.incrementAndGet() + "_" + (System.nanoTime() % 100000);
    }

    public static String emailFor(String username) {
        return username + "@exemplo.test";
    }

    public static Map<String, Object> registerPayload(String username, String email) {
        return Map.of(
                "username", username,
                "email", email,
                "password", VALID_PASSWORD,
                "displayName", "Pessoa " + username,
                "website", "");
    }

    public static Map<String, Object> loginPayload(String identifier, String password) {
        return Map.of("usernameOrEmail", identifier, "password", password);
    }
}
