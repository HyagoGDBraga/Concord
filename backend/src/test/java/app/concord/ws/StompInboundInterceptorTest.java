package app.concord.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O interceptor e a fronteira que impede alguem de assinar a fila de outra
 * pessoa. Testado sem contexto Spring, porque a regra e puramente sobre o
 * conteudo do frame.
 */
class StompInboundInterceptorTest {

    private final StompInboundInterceptor interceptor = new StompInboundInterceptor();

    @Test
    @DisplayName("aceita subscricao em destino de usuario")
    void aceitaDestinoDeUsuario() {
        assertThatCode(() -> interceptor.preSend(
                subscribe("/user/queue/events", true), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("recusa subscricao no destino interno do broker")
    void recusaDestinoInterno() {
        // E o destino para onde o Spring reescreve as filas de usuario. Assinar
        // diretamente permitiria receber eventos alheios acertando o sufixo.
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/queue/events-user123", true), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recusa subscricao em topico")
    void recusaTopico() {
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/conversations/abc", true), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recusa qualquer frame sem principal")
    void recusaSemPrincipal() {
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/user/queue/events", false), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deixa passar o DISCONNECT")
    void permiteDisconnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    private Message<byte[]> subscribe(String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (authenticated) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    "maria", null, List.of()));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
