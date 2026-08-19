package app.concord.chat;

import app.concord.common.exception.ApiException;
import app.concord.message.MessageCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O cursor precisa sobreviver a uma ida e volta pelo cliente sem perder
 * precisao — se o instante ou o id se alterarem, a paginacao pula ou repete
 * mensagens.
 */
class MessageCursorTest {

    @Test
    @DisplayName("codificar e decodificar preserva instante e identificador")
    void idaEVolta() {
        Instant agora = Instant.ofEpochMilli(1_700_000_123_456L);
        UUID id = UUID.randomUUID();

        MessageCursor original = new MessageCursor(agora, id);
        MessageCursor decodificado = MessageCursor.decode(original.encode());

        assertThat(decodificado.createdAt()).isEqualTo(agora);
        assertThat(decodificado.id()).isEqualTo(id);
    }

    @Test
    @DisplayName("cursor e opaco: nao expoe o identificador em texto claro")
    void cursorOpaco() {
        UUID id = UUID.randomUUID();
        String encoded = new MessageCursor(Instant.now(), id).encode();

        assertThat(encoded).doesNotContain(id.toString());
    }

    @Test
    @DisplayName("cursor corrompido vira erro de validacao, nao erro interno")
    void cursorInvalido() {
        assertThatThrownBy(() -> MessageCursor.decode("nao-e-um-cursor"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MessageCursor.decode(""))
                .isInstanceOf(ApiException.class);
    }
}
