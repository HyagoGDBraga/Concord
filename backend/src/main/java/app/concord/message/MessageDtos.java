package app.concord.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MessageDtos {

    private MessageDtos() {
    }

    public record MessageResponse(
            UUID id,
            UUID conversationId,
            UUID senderId,
            String body,
            UUID clientMessageId,
            Instant createdAt,
            Instant editedAt,
            boolean deleted
    ) {
        public static MessageResponse from(Message message) {
            return new MessageResponse(
                    message.getId(),
                    message.getConversationId(),
                    message.getSenderId(),
                    message.getBody(),
                    message.getClientMessageId(),
                    message.getCreatedAt(),
                    message.getEditedAt(),
                    message.isDeleted());
        }
    }

    /**
     * Página de mensagens.
     *
     * <p>Dois cursores porque a conversa cresce nas duas pontas: o histórico é
     * lido para trás e as mensagens novas chegam para frente. Sem
     * {@code latestCursor}, o cliente não teria como pedir "o que chegou depois
     * disto" — ele não consegue construir um cursor sozinho, e nem deve.
     *
     * @param items        ordem cronológica (mais antiga primeiro), como a tela exibe
     * @param cursor       aponta para a mensagem mais antiga desta página; use para
     *                     pedir a página anterior. {@code null} quando o histórico acabou
     * @param latestCursor aponta para a mensagem mais recente conhecida; use em
     *                     {@code /messages/since}. {@code null} se a conversa está vazia
     * @param hasMore      existe página anterior
     */
    public record MessagePage(List<MessageResponse> items, String cursor,
                              String latestCursor, boolean hasMore) {
    }

    public record SendMessageRequest(
            @NotBlank(message = "A mensagem não pode ser vazia")
            @Size(max = 4000, message = "A mensagem pode ter até 4000 caracteres")
            String body,

            /**
             * Identificador gerado pelo cliente, que torna o envio idempotente.
             * Reenviar a mesma requisição devolve a mensagem já criada em vez de
             * duplicá-la.
             */
            @NotNull(message = "clientMessageId é obrigatório")
            UUID clientMessageId
    ) {
    }

    public record EditMessageRequest(
            @NotBlank(message = "A mensagem não pode ser vazia")
            @Size(max = 4000)
            String body
    ) {
    }
}
