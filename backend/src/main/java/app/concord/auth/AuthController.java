package app.concord.auth;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.user.AccountService;
import app.concord.user.User;
import app.concord.user.UserDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticação. Todos os caminhos são relativos ao context-path
 * {@code /api}.
 *
 * <p>Convenção de resposta: {@code 202 Accepted} nas operações cujo resultado
 * não pode ser revelado — cadastro, reenvio de verificação e "esqueci a senha".
 * Devolver {@code 404} ou {@code 409} nesses casos transformaria o endpoint em
 * um verificador de contas existentes.
 */
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final AccountService accountService;

    public AuthController(AuthService authService, RegistrationService registrationService,
                          PasswordResetService passwordResetService,
                          AccountService accountService) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.accountService = accountService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthDtos.AcceptedResponse register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        registrationService.register(request);
        return AuthDtos.AcceptedResponse.of(
                "Se o cadastro puder ser concluído, você receberá um e-mail de confirmação.");
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody AuthDtos.TokenRequest request) {
        registrationService.verifyEmail(request.token());
    }

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthDtos.AcceptedResponse resendVerification(
            @Valid @RequestBody AuthDtos.ResendVerificationRequest request) {
        registrationService.resendVerification(request.email());
        return AuthDtos.AcceptedResponse.of(
                "Se houver uma conta pendente com este e-mail, o link foi reenviado.");
    }

    @PostMapping("/login")
    public UserDtos.MeResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        return authService.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal ConcordUserDetails principal,
                       HttpServletRequest request) {
        User user = principal == null ? null : accountService.requireById(principal.id());
        authService.logout(request, user);
    }

    @GetMapping("/me")
    public UserDtos.MeResponse me(@AuthenticationPrincipal ConcordUserDetails principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.NOT_AUTHENTICATED);
        }
        return UserDtos.MeResponse.from(accountService.requireById(principal.id()));
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthDtos.AcceptedResponse forgotPassword(
            @Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return AuthDtos.AcceptedResponse.of(
                "Se houver uma conta com este e-mail, o link de redefinição foi enviado.");
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    @PostMapping("/email-change/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody AuthDtos.TokenRequest request) {
        accountService.confirmEmailChange(request.token());
    }

    @GetMapping("/username-available")
    public AuthDtos.UsernameAvailabilityResponse usernameAvailable(
            @RequestParam @NotBlank @Size(max = 20) String username) {
        return new AuthDtos.UsernameAvailabilityResponse(
                username, registrationService.isUsernameAvailable(username));
    }
}
