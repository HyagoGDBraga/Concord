package app.concord.legal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class LegalDtos {

    private LegalDtos() {
    }

    /** Versão vigente de cada documento e se o usuário já a aceitou. */
    public record ConsentStatus(
            String termsVersion,
            String privacyVersion,
            boolean termsAccepted,
            boolean privacyAccepted
    ) {
        public boolean allAccepted() {
            return termsAccepted && privacyAccepted;
        }
    }

    public record AcceptConsentRequest(
            @NotNull(message = "Informe o documento")
            LegalDocument document,
            @NotBlank(message = "Informe a versão")
            String version
    ) {
    }

    public record ConsentRecord(LegalDocument document, String version, Instant acceptedAt) {
    }

    public record ConsentHistory(List<ConsentRecord> records) {
    }
}
