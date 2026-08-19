package app.concord.message;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Cursor de paginação por keyset.
 *
 * <p>Codifica o par {@code (createdAt, id)} da mensagem de referência. Vai
 * opaco para o cliente — em Base64 — não por segurança, mas para que o formato
 * possa mudar sem quebrar quem já tem um cursor guardado.
 *
 * @param createdAt instante da mensagem de referência
 * @param id        identificador, que desempata mensagens do mesmo instante
 */
public record MessageCursor(Instant createdAt, UUID id) {

    public String encode() {
        String raw = createdAt.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static MessageCursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new MessageCursor(Instant.ofEpochMilli(Long.parseLong(parts[0])),
                    UUID.fromString(parts[1]));
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Cursor inválido");
        }
    }

    public static MessageCursor of(Message message) {
        return new MessageCursor(message.getCreatedAt(), message.getId());
    }
}
