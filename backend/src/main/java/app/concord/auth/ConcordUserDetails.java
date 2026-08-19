package app.concord.auth;

import app.concord.user.User;
import app.concord.user.UserRole;
import app.concord.user.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Principal autenticado.
 *
 * <p>{@code isEnabled()} e {@code isAccountNonLocked()} devolvem sempre
 * {@code true} de propósito. O {@code DaoAuthenticationProvider} avalia esses
 * predicados <b>antes</b> de conferir a senha, então usá-los revelaria a
 * existência de uma conta desativada a quem apenas chutou o nome de usuário. O
 * estado da conta é verificado no {@code AuthService}, depois da senha correta;
 * o bloqueio temporário é verificado antes, no {@code LoginAttemptService},
 * onde precisa estar para conter força bruta.
 */
public record ConcordUserDetails(
        UUID id,
        String username,
        String passwordHash,
        UserRole role,
        UserStatus status
) implements UserDetails {

    public static ConcordUserDetails from(User user) {
        return new ConcordUserDetails(user.getId(), user.getUsername(),
                user.getPasswordHash(), user.getRole(), user.getStatus());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
