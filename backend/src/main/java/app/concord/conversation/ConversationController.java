package app.concord.conversation;

import app.concord.auth.ConcordUserDetails;
import app.concord.message.MessageDtos;
import app.concord.message.MessageService;
import app.concord.user.AccountService;
import app.concord.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conversas e suas mensagens.
 *
 * <p>Toda rota verifica a participação antes de qualquer leitura. Quem não
 * participa recebe {@code 404} — o mesmo que receberia se a conversa não
 * existisse.
 */
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final AccountService accountService;

    public ConversationController(ConversationService conversationService,
                                  MessageService messageService,
                                  AccountService accountService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.accountService = accountService;
    }

    @GetMapping
    public List<ConversationDtos.ConversationResponse> list(
            @AuthenticationPrincipal ConcordUserDetails principal) {
        return conversationService.list(me(principal));
    }

    /** Abre (ou recupera) a conversa direta com um contato. */
    @PostMapping
    public Map<String, UUID> open(@AuthenticationPrincipal ConcordUserDetails principal,
                                  @Valid @RequestBody
                                  ConversationDtos.CreateConversationRequest request) {
        Conversation conversation =
                conversationService.openDirect(me(principal), request.userId());
        return Map.of("id", conversation.getId());
    }

    @GetMapping("/{id}/messages")
    public MessageDtos.MessagePage history(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return messageService.history(me(principal), id, cursor, size);
    }

    /**
     * Mensagens novas desde o cursor.
     *
     * <p>Consumido em intervalo curto pela tela de conversa enquanto o
     * WebSocket não existe (Fase 4).
     */
    @GetMapping("/{id}/messages/since")
    public MessageDtos.MessagePage since(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID id,
            @RequestParam String cursor) {
        return messageService.since(me(principal), id, cursor);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDtos.MessageResponse send(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody MessageDtos.SendMessageRequest request) {
        return MessageDtos.MessageResponse.from(
                messageService.send(me(principal), id, request));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal ConcordUserDetails principal,
                         @PathVariable UUID id,
                         @Valid @RequestBody ConversationDtos.MarkReadRequest request) {
        conversationService.markRead(me(principal), id, request.messageId());
    }

    private User me(ConcordUserDetails principal) {
        return accountService.requireById(principal.id());
    }
}
