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
    /**
     * Pedido de contato.
     *
     * <p>O campo aceita nome de usuário <b>ou</b> e-mail. Depender só do
     * username obriga a saber a grafia exata e some quando a pessoa troca de
     * nome; o e-mail é único e as pessoas já o trocam entre si.
     *
     * <p>O padrão cobre os dois formatos numa expressão só, em vez de dois
     * campos — quem digita não deveria precisar declarar o que está digitando.
     */
    public record CreateContactRequest(
            @NotBlank(message = "Informe o nome de usuário ou o e-mail")
            @Pattern(
                    regexp = "^([A-Za-z0-9_]{3,20}|[^@\\s]+@[^@\\s]+\\.[^@\\s]+)$",
                    message = "Informe um nome de usuário ou e-mail válido")
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
