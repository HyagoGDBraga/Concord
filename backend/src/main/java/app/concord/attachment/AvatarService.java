package app.concord.attachment;

import app.concord.user.User;
import app.concord.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Troca da foto de perfil.
 *
 * <p>O avatar é o único anexo que substitui outro: cada pessoa tem um só. Por
 * isso a troca precisa liberar o anterior — senão cada troca deixaria um
 * arquivo permanente em disco, e avatar não expira.
 *
 * <p>A liberação passa pelo mesmo cuidado do expurgo: só apaga os bytes se
 * nenhum outro registro apontar para eles, já que a deduplicação faz anexos
 * compartilharem o mesmo arquivo.
 */
@Service
public class AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

    private final AttachmentService attachmentService;
    private final AttachmentRepository attachmentRepository;
    private final FileStorage storage;
    private final UserRepository userRepository;

    public AvatarService(AttachmentService attachmentService,
                         AttachmentRepository attachmentRepository,
                         FileStorage storage,
                         UserRepository userRepository) {
        this.attachmentService = attachmentService;
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.userRepository = userRepository;
    }

    @Transactional
    public AttachmentDtos.Response replace(User user, MultipartFile file) {
        String anterior = user.getAvatarUrl();

        Attachment novo = attachmentService.upload(user, file, AttachmentPurpose.AVATAR, null);

        user.setAvatarUrl("/api/attachments/" + novo.getId());
        userRepository.save(user);

        liberarAnterior(anterior, novo.getId().toString());
        return AttachmentDtos.Response.from(novo);
    }

    @Transactional
    public void remove(User user) {
        String anterior = user.getAvatarUrl();
        user.setAvatarUrl(null);
        userRepository.save(user);
        liberarAnterior(anterior, null);
    }

    /**
     * Remove o anexo do avatar antigo.
     *
     * <p>Falhas aqui são registradas e engolidas: o perfil novo já foi salvo, e
     * deixar um arquivo órfão é melhor que devolver erro para quem só queria
     * trocar a foto. O job de expurgo reporta a divergência.
     */
    private void liberarAnterior(String urlAnterior, String idNovo) {
        if (urlAnterior == null || !urlAnterior.startsWith("/api/attachments/")) {
            return;
        }
        String id = urlAnterior.substring("/api/attachments/".length());
        if (id.equals(idNovo)) {
            return;
        }
        try {
            attachmentRepository.findById(java.util.UUID.fromString(id)).ifPresent(antigo -> {
                String chave = antigo.getStorageKey();
                attachmentRepository.delete(antigo);
                attachmentRepository.flush();

                // Só apaga os bytes se ninguém mais apontar para eles: a
                // deduplicação faz anexos diferentes compartilharem o arquivo.
                if (attachmentRepository.countByStorageKey(chave) == 0) {
                    storage.delete(chave);
                }
            });
        } catch (IllegalArgumentException ex) {
            log.debug("Avatar anterior não era um anexo gerenciado; nada a liberar");
        } catch (RuntimeException ex) {
            log.warn("Não foi possível liberar o avatar anterior", ex);
        }
    }
}
