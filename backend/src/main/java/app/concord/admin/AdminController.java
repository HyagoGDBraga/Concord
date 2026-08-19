package app.concord.admin;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditCategory;
import app.concord.auth.ConcordUserDetails;
import app.concord.common.dto.PageResponse;
import app.concord.config.AppProperties;
import app.concord.user.AccountService;
import app.concord.user.User;
import app.concord.user.UserStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Painel administrativo.
 *
 * <p>Escopo fechado por decisão D-04. Repare no que <b>não</b> existe aqui:
 * nenhuma rota de mensagem, de conversa, de mídia ou de lista de contatos de
 * terceiros. Essa ausência é o controle de privacidade — não há flag para
 * ligar, não há caminho de código a ser explorado se uma conta de administrador
 * for comprometida.
 *
 * <p>A autorização é dupla: o {@code SecurityConfig} exige {@code ROLE_ADMIN}
 * em {@code /admin/**} e cada método repete a exigência com
 * {@code @PreAuthorize}. A redundância é intencional — uma rota nova mal
 * mapeada no futuro continua protegida.
 *
 * <p>Quem não é administrador recebe {@code 404}, não {@code 403}: um 403
 * confirmaria a existência do painel.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminService adminService;
    private final AccountService accountService;
    private final AppProperties properties;

    public AdminController(AdminService adminService, AccountService accountService,
                           AppProperties properties) {
        this.adminService = adminService;
        this.accountService = accountService;
        this.properties = properties;
    }

    // ------------------------------------------------------------- usuários

    @GetMapping("/users")
    public PageResponse<AdminDtos.AdminUserResponse> listUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return PageResponse.from(adminService.searchUsers(query, status, pageable),
                AdminDtos.AdminUserResponse::from);
    }

    @GetMapping("/users/{id}")
    public AdminDtos.AdminUserResponse getUser(@PathVariable UUID id) {
        return AdminDtos.AdminUserResponse.from(adminService.requireUser(id));
    }

    @PostMapping("/users/{id}/disable")
    public AdminDtos.AdminUserResponse disableUser(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody AdminDtos.DisableUserRequest request) {
        User admin = accountService.requireById(principal.id());
        return AdminDtos.AdminUserResponse.from(
                adminService.disable(id, admin, request.reason().trim()));
    }

    @PostMapping("/users/{id}/enable")
    public AdminDtos.AdminUserResponse enableUser(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID id) {
        User admin = accountService.requireById(principal.id());
        return AdminDtos.AdminUserResponse.from(adminService.enable(id, admin));
    }

    @PostMapping("/users/{id}/sessions/revoke")
    public AdminDtos.RevokedSessionsResponse revokeSessions(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID id) {
        User admin = accountService.requireById(principal.id());
        return new AdminDtos.RevokedSessionsResponse(adminService.revokeSessions(id, admin));
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@AuthenticationPrincipal ConcordUserDetails principal,
                           @PathVariable UUID id,
                           @Valid @RequestBody AdminDtos.DeleteUserRequest request) {
        User admin = accountService.requireById(principal.id());
        adminService.deleteUser(id, admin, request.reason().trim());
    }

    // ------------------------------------------------------------ auditoria

    @GetMapping("/audit")
    public PageResponse<AdminDtos.AuditLogResponse> searchAudit(
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return PageResponse.from(
                adminService.searchAudit(category, action, userId, from, to, pageable),
                AdminDtos.AuditLogResponse::from);
    }

    // --------------------------------------------------------- configuração

    @GetMapping("/settings")
    public AdminDtos.SettingsResponse getSettings() {
        return adminService.getSettings(properties.registrationOpen());
    }

    @PatchMapping("/settings")
    public AdminDtos.SettingsResponse updateSettings(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @Valid @RequestBody AdminDtos.UpdateSettingsRequest request) {
        User admin = accountService.requireById(principal.id());
        adminService.setRegistrationOpen(request.registrationOpen(), admin);
        return adminService.getSettings(properties.registrationOpen());
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
