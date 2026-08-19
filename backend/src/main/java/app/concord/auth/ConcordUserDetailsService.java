package app.concord.auth;

import app.concord.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega o principal a partir do username.
 *
 * <p>Usa apenas o username, nunca o e-mail: o {@code PRINCIPAL_NAME} do Spring
 * Session é derivado daqui, e é a chave usada para localizar e revogar as
 * sessões de um usuário. Login por e-mail é resolvido antes, no
 * {@code AuthService}, que traduz o identificador informado para o username.
 */
@Service
public class ConcordUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public ConcordUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(ConcordUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
