package app.concord.common.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Formato único de erro da API.
 *
 * <p>{@code fieldErrors} só aparece em falhas de validação. Stack trace nunca é
 * exposto: em produção o {@code requestId} é a ponte entre o que o usuário vê e
 * o que está no log do servidor.
 */
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String requestId,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(String code, String message, String requestId) {
        return new ErrorResponse(code, message, Instant.now(), requestId, null);
    }

    public static ErrorResponse of(String code, String message, String requestId,
                                   Map<String, String> fieldErrors) {
        return new ErrorResponse(code, message, Instant.now(), requestId,
                fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors);
    }
}
