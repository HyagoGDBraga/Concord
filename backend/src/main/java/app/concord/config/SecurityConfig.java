package app.concord.config;

import app.concord.auth.ConcordUserDetailsService;
import app.concord.common.dto.ErrorResponse;
import app.concord.common.exception.ErrorCode;
import app.concord.common.ratelimit.RateLimitFilter;
import app.concord.common.request.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuração de segurança do Concord.
 *
 * <p>Modelo: sessão opaca em cookie (ADR-02), sem JWT, sem refresh token.
 * O detalhamento completo está em {@code docs/CONCORD-02-SESSAO-E-AUTENTICACAO.md}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final boolean productionProfile;

    public SecurityConfig(ObjectMapper objectMapper,
                          @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.objectMapper = objectMapper;
        this.productionProfile = "prod".equalsIgnoreCase(activeProfile);
    }

    /**
     * Argon2id com os parâmetros recomendados pelo OWASP: 19 MiB de memória,
     * 2 iterações, paralelismo 1, salt de 16 bytes e hash de 32 bytes.
     *
     * <p>O {@link DelegatingPasswordEncoder} grava o prefixo do algoritmo junto
     * com o hash, o que permite migrar de algoritmo no futuro sem invalidar as
     * senhas existentes. O bcrypt fica registrado apenas para leitura, caso
     * algum dia seja preciso importar hashes de outra origem.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2", argon2);
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        return new DelegatingPasswordEncoder("argon2", encoders);
    }

    @Bean
    public AuthenticationManager authenticationManager(ConcordUserDetailsService userDetailsService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Mesmo quando o usuário não existe, o provider executa uma verificação
        // de senha descartável. Isso mantém o tempo de resposta constante e
        // impede enumeração de contas por medição de latência.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Troca o id da sessão no login. Necessário porque o login é feito
     * manualmente (JSON) e não pelo {@code formLogin}, que aplicaria isto
     * automaticamente. Sem esta estratégia não há proteção contra session
     * fixation.
     */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            RateLimitFilter rateLimitFilter,
                                            SecurityContextRepository securityContextRepository)
            throws Exception {

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Desliga o encoding XOR do token. Ele protege contra BREACH quando o
        // token é renderizado no HTML pelo servidor; aqui o cliente lê o valor
        // do cookie e o devolve no header, então o XOR só quebraria a comparação.
        csrfHandler.setCsrfRequestAttributeName(null);

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // Path "/" explícito. Sem isto, o cookie herda o context-path da
        // aplicação e é gravado com Path=/api — invisível para o JavaScript da
        // página, que roda em "/". O cliente nunca conseguiria ler o token, e
        // toda mutação seria recusada com 403.
        //
        // O cookie de sessão não sofre disso porque tem o path definido em
        // server.servlet.session.cookie.path.
        csrfRepository.setCookieCustomizer(cookie -> cookie.path("/"));

        http
            .securityContext(context -> context
                    .securityContextRepository(securityContextRepository))

            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(csrfHandler))

            .authorizeHttpRequests(auth -> auth
                    // Endpoints públicos de autenticação.
                    .requestMatchers(HttpMethod.POST,
                            "/auth/register",
                            "/auth/login",
                            "/auth/verify-email",
                            "/auth/verify-email/resend",
                            "/auth/password/forgot",
                            "/auth/password/reset",
                            "/auth/email-change/confirm",
                            // Webhook de bounce. Publico por necessidade — o
                            // provedor de e-mail nao tem sessao aqui —, mas
                            // protegido por assinatura HMAC verificada no
                            // controller, e desligado quando nao ha segredo.
                            "/webhooks/email").permitAll()
                    .requestMatchers(HttpMethod.GET,
                            "/auth/username-available",
                            "/actuator/health",
                            "/actuator/health/**",
                            "/actuator/info").permitAll()
                    // Painel administrativo. Quem não é ADMIN recebe 404, não 403
                    // (ver GlobalExceptionHandler).
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())

            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint(this::writeUnauthorized)
                    .accessDeniedHandler(this::writeAccessDenied))

            .headers(headers -> headers
                    .contentSecurityPolicy(csp -> csp
                            // A API só devolve JSON: nada pode ser carregado a
                            // partir dela, e ela não pode ser enquadrada.
                            .policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .referrerPolicy(referrer -> referrer
                            .policy(org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .frameOptions(frame -> frame.deny())
                    .httpStrictTransportSecurity(hsts -> {
                        if (productionProfile) {
                            hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000);
                        } else {
                            hsts.disable();
                        }
                    }))

            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())

            .addFilterBefore(rateLimitFilter, CsrfFilter.class)
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            .addFilterAfter(new SessionPolicyFilter(), CsrfFilter.class);

        return http.build();
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                   org.springframework.security.core.AuthenticationException ex)
            throws IOException {
        writeError(request, response, ErrorCode.NOT_AUTHENTICATED);
    }

    private void writeAccessDenied(HttpServletRequest request, HttpServletResponse response,
                                   org.springframework.security.access.AccessDeniedException ex)
            throws IOException {
        boolean admin = request.getServletPath().startsWith("/admin");
        writeError(request, response, admin ? ErrorCode.NOT_FOUND : ErrorCode.ACCESS_DENIED);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of(code.name(), code.defaultMessage(),
                        RequestIdFilter.current(request))));
    }
}
