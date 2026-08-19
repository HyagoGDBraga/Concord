package app.concord.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Força a materialização do token CSRF em toda requisição.
 *
 * <p>O {@code CookieCsrfTokenRepository} é preguiçoso: sem alguém chamar
 * {@code getToken()}, o cookie {@code XSRF-TOKEN} não é escrito. Sem esse
 * cookie, o primeiro POST do cliente — que costuma ser o login — falharia.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
        if (token != null) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
