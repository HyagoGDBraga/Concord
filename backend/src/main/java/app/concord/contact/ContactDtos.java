package app.concord.contact;

import app.concord.user.User;
import app.concord.user.UserDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

public final class ContactDtos {

    private ContactDtos() {
    }

    /** Contato aceito, como aparece na lista. */
    public record ContactResponse(
            UUID id,
            UserDtos.PublicUserResponse user,
            Instant since,
            boolean blockedByMe
    ) {
    }

    /** Pedido pendente, de entrada ou de saída. */
    public record ContactRequestResponse(
            UUID id,
            UserDtos.PublicUserResponse user,
            Instant createdAt
    ) {
    }

    public record ContactsOverview(
            java.util.List<ContactResponse> contacts,
            java.util.List<ContactRequestResponse> incoming,
            java.util.List<ContactRequestResponse> outgoing
    ) {
    }

    /**
     * Pedido por username exato.
     *
     * <p>Não existe busca por prefixo nem por e-mail: com cadastro aberto, uma
     * busca parcial permitiria varrer a base de usuários. Quem quer adicionar
     * alguém já sabe o username.
     */
    public record CreateContactRequest(
            @NotBlank(message = "Informe o nome de usuário")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "Nome de usuário inválido")
            String username
    ) {
    }

    static ContactResponse toContact(Contact contact, User other, boolean blockedByMe) {
        return new ContactResponse(contact.getId(),
                UserDtos.PublicUserResponse.from(other),
                contact.getRespondedAt(), blockedByMe);
    }

    static ContactRequestResponse toRequest(Contact contact, User other) {
        return new ContactRequestResponse(contact.getId(),
                UserDtos.PublicUserResponse.from(other), contact.getCreatedAt());
    }
}
