package app.concord.auth;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditOutcome;
import app.concord.audit.AuditService;
import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.request.ClientIp;
import app.concord.user.User;
import app.concord.user.UserDtos;
import app.concord.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Login, logout e identidade do usuário autenticado.
 *
 * <p>O login é feito manualmente, com resposta JSON, em vez de {@code formLogin}.
 * Isso obriga a executar explicitamente três passos que o filtro padrão faria
 * sozinho — e que estão marcados no código abaixo: estratégia anti-fixation,
 * gravação do {@code SecurityContext} no repositório e registro dos metadados
 * da sessão.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;
    private final AuditService auditService;

    public AuthService(AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository,
                       SessionAuthenticationStrategy sessionAuthenticationStrategy,
                       UserRepository userRepository,
                       LoginAttemptService loginAttemptService,
                       SessionService sessionService,
                       AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
        this.sessionService = sessionService;
        this.auditService = auditService;
    }

    @Transactional
    public UserDtos.MeResponse login(AuthDtos.LoginRequest request,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        String identifier = request.usernameOrEmail().trim();
        Optional<User> candidate = userRepository.findByUsernameOrEmail(identifier);

        // 1. Bloqueio temporário é avaliado ANTES da senha. Verificá-lo depois
        //    tornaria o mecanismo inútil contra força bruta.
        if (candidate.isPresent() && candidate.get().isTemporarilyLocked()) {
            auditService.security(AuditAction.LOGIN_FAILURE, AuditOutcome.DENIED,
                    candidate.get().getId(), candidate.get().getUsername(),
                    Map.of("reason", "temporarily_locked"));
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }

        // 2. Autenticação. Quando o usuário não existe, o username informado é
        //    repassado assim mesmo: o DaoAuthenticationProvider executa uma
        //    verificação descartável de senha, mantendo o tempo de resposta
        //    constante e impedindo enumeração por latência.
        String principalName = candidate.map(User::getUsername).orElse(identifier);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            principalName, request.password()));
        } catch (AuthenticationException ex) {
            candidate.ifPresent(loginAttemptService::registerFailure);
            auditService.security(AuditAction.LOGIN_FAILURE, AuditOutcome.FAILURE,
                    candidate.map(User::getId).orElse(null),
                    candidate.map(User::getUsername).orElse(null),
                    Map.of("reason", "bad_credentials"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = candidate.orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        // 3. Estado da conta, avaliado só depois da senha correta. Antes disso,
        //    responder "conta desativada" confirmaria a existência da conta a
        //    quem apenas chutou o nome de usuário.
        checkAccountState(user);

        // 4. Anti session fixation: novo id de sessão, atributos preservados.
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        sessionService.recordMetadata(httpRequest, user.getId(), ClientIp.of(httpRequest));
        loginAttemptService.registerSuccess(user);

        auditService.security(AuditAction.LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                user.getId(), user.getUsername(), Map.of());

        return UserDtos.MeResponse.from(user);
    }

    public void logout(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        if (user != null) {
            auditService.security(AuditAction.LOGOUT, AuditOutcome.SUCCESS,
                    user.getId(), user.getUsername(), Map.of());
        }
    }

    private void checkAccountState(User user) {
        switch (user.getStatus()) {
            case ACTIVE -> {
                // segue
            }
            case PENDING_VERIFICATION -> {
                auditService.security(AuditAction.LOGIN_FAILURE, AuditOutcome.DENIED,
                        user.getId(), user.getUsername(), Map.of("reason", "email_not_verified"));
                throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
            }
            case DISABLED -> {
                auditService.security(AuditAction.LOGIN_FAILURE, AuditOutcome.DENIED,
                        user.getId(), user.getUsername(), Map.of("reason", "account_disabled"));
                throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
            }
            case DELETED -> {
                log.warn("Tentativa de login em conta excluída");
                throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
            }
        }
    }
}
