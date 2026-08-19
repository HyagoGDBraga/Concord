package app.concord.contact;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.tx.AfterCommit;
import app.concord.user.User;
import app.concord.user.UserRepository;
import app.concord.user.UserDtos;
import app.concord.user.UserStatus;
import app.concord.ws.RealtimeEvent;
import app.concord.ws.RealtimeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Contatos e bloqueio.
 *
 * <p>Nada do que acontece aqui vai para o {@code audit_log}. O grafo social é
 * dado sensível e a auditoria tem retenção de meses a anos; registrar quem
 * adicionou quem construiria justamente o acervo de metadados que o Concord
 * existe para não ter. O que é auditável continua sendo autenticação e ação
 * administrativa.
 */
@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final ContactRepository contactRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final RealtimeNotifier notifier;

    public ContactService(ContactRepository contactRepository, BlockRepository blockRepository,
                          UserRepository userRepository, RealtimeNotifier notifier) {
        this.contactRepository = contactRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
    }

    /**
     * Envia um pedido de contato.
     *
     * <p>Se já existir um pedido do outro lado, a relação é aceita direto — os
     * dois se procuraram, não faz sentido exigir uma confirmação a mais.
     */
    @Transactional
    public Contact request(User me, String username) {
        User target = userRepository.findByUsernameIgnoreCase(username.trim())
                // Mesma resposta para "não existe" e "não está ativo": um usuário
                // desativado ou excluído não deve ser distinguível de um
                // inexistente.
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (target.getId().equals(me.getId())) {
            throw new ApiException(ErrorCode.CANNOT_TARGET_SELF_CONTACT);
        }
        if (blockRepository.existsBetween(me.getId(), target.getId())) {
            // Resposta genérica: revelar "você foi bloqueado" entrega ao
            // remetente uma informação que o bloqueio pretende esconder.
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }

        String pairKey = Contact.pairKeyFor(me.getId(), target.getId());
        Optional<Contact> existing = contactRepository.findByPairKey(pairKey);

        if (existing.isPresent()) {
            Contact contact = existing.get();
            if (contact.isAccepted()) {
                throw new ApiException(ErrorCode.CONTACT_ALREADY_EXISTS,
                        "Vocês já são contatos");
            }
            if (contact.getAddresseeId().equals(me.getId())) {
                contact.accept();
                Contact accepted = contactRepository.save(contact);
                notifyAccepted(accepted, me, target);
                return accepted;
            }
            throw new ApiException(ErrorCode.CONTACT_ALREADY_EXISTS,
                    "O pedido já foi enviado e aguarda resposta");
        }

        Contact created = contactRepository.save(new Contact(me.getId(), target.getId()));

        // O destinatário vê o pedido aparecer sem recarregar a página.
        UUID addresseeId = target.getId();
        UserDtos.PublicUserResponse from = UserDtos.PublicUserResponse.from(me);
        AfterCommit.run(() -> notifier.sendToUser(addresseeId,
                RealtimeEvent.of(RealtimeEvent.CONTACT_REQUEST, from)));
        return created;
    }

    /** Aceita um pedido recebido. Só o destinatário pode aceitar. */
    @Transactional
    public Contact accept(User me, UUID contactId) {
        Contact contact = requirePending(contactId);
        if (!contact.getAddresseeId().equals(me.getId())) {
            throw new ApiException(ErrorCode.CONTACT_NOT_FOUND);
        }
        if (blockRepository.existsBetween(contact.getRequesterId(), me.getId())) {
            throw new ApiException(ErrorCode.BLOCKED);
        }
        contact.accept();
        Contact accepted = contactRepository.save(contact);

        userRepository.findById(contact.getRequesterId())
                .ifPresent(requester -> notifyAccepted(accepted, me, requester));
        return accepted;
    }

    /** Avisa o outro lado de que a relação passou a valer. */
    private void notifyAccepted(Contact contact, User me, User other) {
        UUID otherId = contact.otherSide(me.getId());
        UserDtos.PublicUserResponse payload = UserDtos.PublicUserResponse.from(me);
        AfterCommit.run(() -> notifier.sendToUser(otherId,
                RealtimeEvent.of(RealtimeEvent.CONTACT_ACCEPTED, payload)));
    }

    /**
     * Recusa um pedido recebido, ou cancela um pedido enviado.
     *
     * <p>Nos dois casos a linha é removida, e não marcada como recusada.
     * Guardar "B recusou A" seria registro sobre uma relação que a pessoa
     * escolheu não ter.
     */
    @Transactional
    public void declineOrCancel(User me, UUID contactId) {
        Contact contact = requirePending(contactId);
        if (!contact.involves(me.getId())) {
            throw new ApiException(ErrorCode.CONTACT_NOT_FOUND);
        }
        contactRepository.delete(contact);
    }

    /** Desfaz um contato aceito. A conversa e o histórico continuam existindo. */
    @Transactional
    public void remove(User me, UUID otherUserId) {
        Contact contact = contactRepository
                .findByPairKey(Contact.pairKeyFor(me.getId(), otherUserId))
                .filter(c -> c.involves(me.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.CONTACT_NOT_FOUND));
        contactRepository.delete(contact);
    }

    @Transactional
    public void block(User me, UUID otherUserId) {
        if (otherUserId.equals(me.getId())) {
            throw new ApiException(ErrorCode.CANNOT_TARGET_SELF_CONTACT);
        }
        userRepository.findById(otherUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        Block.BlockId id = new Block.BlockId(me.getId(), otherUserId);
        if (!blockRepository.existsById(id)) {
            blockRepository.save(new Block(me.getId(), otherUserId));
            log.info("Bloqueio registrado");
        }
    }

    @Transactional
    public void unblock(User me, UUID otherUserId) {
        blockRepository.deleteById(new Block.BlockId(me.getId(), otherUserId));
    }

    @Transactional(readOnly = true)
    public boolean isBlockedBetween(UUID a, UUID b) {
        return blockRepository.existsBetween(a, b);
    }

    /** As duas pessoas são contatos aceitos? Pré-condição para conversar. */
    @Transactional(readOnly = true)
    public boolean areContacts(UUID a, UUID b) {
        return contactRepository.findByPairKey(Contact.pairKeyFor(a, b))
                .map(Contact::isAccepted)
                .orElse(false);
    }

    /** Panorama completo: contatos, pedidos recebidos e pedidos enviados. */
    @Transactional(readOnly = true)
    public ContactDtos.ContactsOverview overview(User me) {
        List<Contact> accepted = contactRepository.findAcceptedOf(me.getId());
        List<Contact> incoming = contactRepository.findIncomingPending(me.getId());
        List<Contact> outgoing = contactRepository.findOutgoingPending(me.getId());

        Map<UUID, User> users = loadOtherSides(me.getId(), accepted, incoming, outgoing);
        Set<UUID> blocked = new HashSet<>(blockRepository.findBlockedIdsBy(me.getId()));

        return new ContactDtos.ContactsOverview(
                accepted.stream()
                        .map(c -> {
                            UUID other = c.otherSide(me.getId());
                            return ContactDtos.toContact(c, users.get(other),
                                    blocked.contains(other));
                        })
                        .filter(response -> response.user() != null)
                        .toList(),
                incoming.stream()
                        .map(c -> ContactDtos.toRequest(c, users.get(c.getRequesterId())))
                        .filter(response -> response.user() != null)
                        .toList(),
                outgoing.stream()
                        .map(c -> ContactDtos.toRequest(c, users.get(c.getAddresseeId())))
                        .filter(response -> response.user() != null)
                        .toList());
    }

    /** Carrega em um único SELECT todos os interlocutores citados. */
    @SafeVarargs
    private Map<UUID, User> loadOtherSides(UUID meId, List<Contact>... groups) {
        Set<UUID> ids = new HashSet<>();
        for (List<Contact> group : groups) {
            for (Contact contact : group) {
                ids.add(contact.otherSide(meId));
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, User> byId = new LinkedHashMap<>();
        userRepository.findAllById(ids).forEach(user -> byId.put(user.getId(), user));
        return byId;
    }

    private Contact requirePending(UUID contactId) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONTACT_NOT_FOUND));
        if (contact.isAccepted()) {
            throw new ApiException(ErrorCode.CONTACT_NOT_FOUND);
        }
        return contact;
    }
}
