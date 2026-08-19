package app.concord.legal;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.request.ClientIp;
import app.concord.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Registro de aceite dos termos e da política de privacidade.
 *
 * <p>Versionado de propósito. "Aceitou em 12/03" não prova nada se o texto mudou
 * em 15/03 — o que sustenta a base legal é saber qual texto a pessoa leu. Quando
 * a versão muda, o aceite anterior deixa de valer para a nova e o usuário é
 * consultado de novo.
 */
@Service
public class ConsentService {

    private final UserConsentRepository repository;
    private final AppProperties properties;

    public ConsentService(UserConsentRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public String currentVersionOf(LegalDocument document) {
        return document == LegalDocument.TERMS_OF_USE
                ? properties.legal().termsVersion()
                : properties.legal().privacyVersion();
    }

    @Transactional(readOnly = true)
    public LegalDtos.ConsentStatus statusOf(UUID userId) {
        String terms = currentVersionOf(LegalDocument.TERMS_OF_USE);
        String privacy = currentVersionOf(LegalDocument.PRIVACY_POLICY);

        return new LegalDtos.ConsentStatus(terms, privacy,
                repository.hasAccepted(userId, LegalDocument.TERMS_OF_USE, terms),
                repository.hasAccepted(userId, LegalDocument.PRIVACY_POLICY, privacy));
    }

    /**
     * Registra o aceite.
     *
     * <p>A versão informada precisa ser a vigente: aceitar uma versão antiga
     * produziria um registro que não corresponde a nada que a pessoa tenha visto.
     */
    @Transactional
    public void accept(UUID userId, LegalDocument document, String version) {
        String current = currentVersionOf(document);
        if (!current.equals(version)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "A versão do documento mudou. Recarregue a página");
        }
        if (repository.hasAccepted(userId, document, version)) {
            return;
        }
        repository.save(new UserConsent(userId, document, version, currentIp()));
    }

    /** Aceite dos dois documentos no cadastro, quando o formulário os apresenta. */
    @Transactional
    public void acceptAllAtRegistration(UUID userId) {
        String ip = currentIp();
        repository.save(new UserConsent(userId, LegalDocument.TERMS_OF_USE,
                currentVersionOf(LegalDocument.TERMS_OF_USE), ip));
        repository.save(new UserConsent(userId, LegalDocument.PRIVACY_POLICY,
                currentVersionOf(LegalDocument.PRIVACY_POLICY), ip));
    }

    @Transactional(readOnly = true)
    public LegalDtos.ConsentHistory historyOf(UUID userId) {
        return new LegalDtos.ConsentHistory(
                repository.findByUserIdOrderByAcceptedAtDesc(userId).stream()
                        .map(consent -> new LegalDtos.ConsentRecord(consent.getDocument(),
                                consent.getVersion(), consent.getAcceptedAt()))
                        .toList());
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return ClientIp.of(request);
        }
        return null;
    }
}
