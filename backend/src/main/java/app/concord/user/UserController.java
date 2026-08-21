package app.concord.user;

import app.concord.auth.ConcordUserDetails;
import app.concord.auth.SessionService;
import app.concord.audit.AuditAction;
import app.concord.audit.AuditOutcome;
import app.concord.audit.AuditService;
import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.attachment.AttachmentDtos;
import app.concord.attachment.AvatarService;
import app.concord.common.ratelimit.RateLimiter;
import app.concord.privacy.DataExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Conta e sessões do próprio usuário.
 *
 * <p>Todas as rotas operam sobre {@code /users/me}. Não existe
 * {@code /users/{id}} para alteração: o alvo é sempre o principal autenticado, o
 * que elimina por construção a classe inteira de falhas de IDOR neste
 * controller.
 */
@RestController
@RequestMapping("/users/me")
public class UserController {

    private final AccountService accountService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final DataExportService dataExportService;
    private final RateLimiter rateLimiter;
    private final AvatarService avatarService;

    public UserController(AccountService accountService, SessionService sessionService,
                          AuditService auditService, DataExportService dataExportService,
                          RateLimiter rateLimiter, AvatarService avatarService) {
        this.accountService = accountService;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.dataExportService = dataExportService;
        this.rateLimiter = rateLimiter;
        this.avatarService = avatarService;
    }

    /**
     * Troca a foto de perfil.
     *
     * <p>Aceita só imagem — a validação é por conteúdo, não por extensão nem
     * pelo Content-Type enviado, que vêm do cliente e são forjáveis.
     */
    @PostMapping("/avatar")
    public AttachmentDtos.Response uploadAvatar(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return avatarService.replace(accountService.requireById(principal.id()), file);
    }

    @DeleteMapping("/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvatar(@AuthenticationPrincipal ConcordUserDetails principal) {
        avatarService.remove(accountService.requireById(principal.id()));
    }

    /**
     * Exportação dos dados do titular (Art. 18 da LGPD).
     *
     * <p>Limitado a um pedido por dia. Não é economia de recurso: uma exportação
     * é o arquivo mais sensível que o sistema produz, e uma conta comprometida
     * poderia usá-la para exfiltrar tudo de uma vez. O limite também deixa
     * rastro no audit_log se alguém tentar.
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportData(
            @AuthenticationPrincipal ConcordUserDetails principal) {

        if (!rateLimiter.tryConsume("export:" + principal.id(), 1, Duration.ofDays(1))) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Você já solicitou uma exportação nas últimas 24 horas");
        }

        User user = accountService.requireById(principal.id());
        Map<String, Object> data = dataExportService.export(user);

        String filename = "concord-meus-dados-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }

    @PatchMapping
    public UserDtos.MeResponse updateProfile(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        User user = accountService.requireById(principal.id());
        return UserDtos.MeResponse.from(accountService.updateProfile(user, request));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal ConcordUserDetails principal,
                               @Valid @RequestBody UserDtos.ChangePasswordRequest request,
                               HttpServletRequest httpRequest) {
        User user = accountService.requireById(principal.id());
        accountService.changePassword(user, request, currentSessionId(httpRequest));
    }

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> changeEmail(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @Valid @RequestBody UserDtos.ChangeEmailRequest request) {
        User user = accountService.requireById(principal.id());
        accountService.requestEmailChange(user, request);
        return Map.of("message",
                "Se o endereço puder ser usado, um link de confirmação foi enviado a ele.");
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal ConcordUserDetails principal,
                              @Valid @RequestBody UserDtos.DeleteAccountRequest request) {
        User user = accountService.requireById(principal.id());
        accountService.deleteOwnAccount(user, request);
    }

    @GetMapping("/sessions")
    public List<UserDtos.SessionResponse> listSessions(
            @AuthenticationPrincipal ConcordUserDetails principal,
            HttpServletRequest request) {
        return sessionService.listFor(principal.getUsername(), currentSessionId(request));
    }

    /**
     * Encerra uma sessão específica. A posse é verificada no serviço: um id de
     * sessão de outra pessoa nunca é apagado, mesmo que seja conhecido.
     */
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@AuthenticationPrincipal ConcordUserDetails principal,
                              @PathVariable String sessionId) {
        if (!sessionService.revoke(principal.getUsername(), sessionId)) {
            throw new ApiException(ErrorCode.SESSION_NOT_FOUND);
        }
        auditService.security(AuditAction.SESSION_REVOKED, AuditOutcome.SUCCESS,
                principal.id(), principal.getUsername(), Map.of());
    }

    /** Encerra todas as outras sessões, mantendo a atual. */
    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOtherSessions(@AuthenticationPrincipal ConcordUserDetails principal,
                                    HttpServletRequest request) {
        int revoked = sessionService.revokeAllExcept(
                principal.getUsername(), currentSessionId(request));
        auditService.security(AuditAction.ALL_SESSIONS_REVOKED, AuditOutcome.SUCCESS,
                principal.id(), principal.getUsername(), Map.of("sessionsRevoked", revoked));
    }

    private String currentSessionId(HttpServletRequest request) {
        var session = request.getSession(false);
        return session == null ? null : session.getId();
    }
}
