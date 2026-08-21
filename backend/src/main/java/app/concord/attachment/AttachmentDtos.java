package app.concord.attachment;

import java.time.Instant;
import java.util.UUID;

public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    public record Response(
            UUID id,
            String name,
            String contentType,
            long sizeBytes,
            boolean image,
            String url,
            Instant expiresAt
    ) {
        public static Response from(Attachment anexo) {
            return new Response(
                    anexo.getId(),
                    anexo.getOriginalName(),
                    anexo.getContentType(),
                    anexo.getSizeBytes(),
                    anexo.getContentType().startsWith("image/"),
                    "/api/attachments/" + anexo.getId(),
                    anexo.getExpiresAt());
        }
    }
}
