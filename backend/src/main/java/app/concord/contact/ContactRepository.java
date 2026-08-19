package app.concord.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByPairKey(String pairKey);

    @Query("""
            SELECT c FROM Contact c
            WHERE c.status = app.concord.contact.ContactStatus.ACCEPTED
              AND (c.requesterId = :userId OR c.addresseeId = :userId)
            ORDER BY c.respondedAt DESC
            """)
    List<Contact> findAcceptedOf(@Param("userId") UUID userId);

    /**
     * Ids das pessoas com quem o usuário tem contato aceito.
     *
     * <p>É a lista de quem pode saber que ele ficou online. Presença nunca é
     * pública no Concord: quem não é contato não recebe o evento.
     */
    @Query("""
            SELECT CASE WHEN c.requesterId = :userId THEN c.addresseeId ELSE c.requesterId END
            FROM Contact c
            WHERE c.status = app.concord.contact.ContactStatus.ACCEPTED
              AND (c.requesterId = :userId OR c.addresseeId = :userId)
            """)
    List<UUID> findAcceptedContactIds(@Param("userId") UUID userId);

    /** Pedidos que o usuário recebeu e ainda não respondeu. */
    @Query("""
            SELECT c FROM Contact c
            WHERE c.addresseeId = :userId
              AND c.status = app.concord.contact.ContactStatus.PENDING
            ORDER BY c.createdAt DESC
            """)
    List<Contact> findIncomingPending(@Param("userId") UUID userId);

    /** Pedidos que o usuário enviou e aguardam resposta. */
    @Query("""
            SELECT c FROM Contact c
            WHERE c.requesterId = :userId
              AND c.status = app.concord.contact.ContactStatus.PENDING
            ORDER BY c.createdAt DESC
            """)
    List<Contact> findOutgoingPending(@Param("userId") UUID userId);
}
