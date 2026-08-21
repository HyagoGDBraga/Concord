package app.concord.attachment;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.server.Server;
import app.concord.server.ServerMemberRepository;
import app.concord.server.ServerRepository;
import app.concord.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Ícone do servidor.
 *
 * <p>Mesma mecânica do avatar: cada servidor tem um só, e trocar precisa
 * liberar o anterior — senão cada troca deixaria um arquivo permanente em
 * disco, já que ícone não expira.
 */
@Service
public class ServerIconService {

    private static final Logger log = LoggerFactory.getLogger(ServerIconService.class);

    private final AttachmentService attachmentService;
    private final AttachmentRepository attachmentRepository;
    private final FileStorage storage;
    private final ServerRepository serverRepository;
    private final ServerMemberRepository memberRepository;

    public ServerIconService(AttachmentService attachmentService,
                             AttachmentRepository attachmentRepository,
                             FileStorage storage,
                             ServerRepository serverRepository,
                             ServerMemberRepository memberRepository) {
        this.attachmentService = attachmentService;
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.serverRepository = serverRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public AttachmentDtos.Response replace(User user, UUID serverId, MultipartFile file) {
        Server server = requireModerator(user, serverId);
        String anterior = server.getIconUrl();

        Attachment novo = attachmentService.upload(
                user, file, AttachmentPurpose.SERVER_ICON, null);

        server.setIconUrl("/api/attachments/" + novo.getId());
        serverRepository.save(server);

        liberarAnterior(anterior, novo.getId().toString());
        return AttachmentDtos.Response.from(novo);
    }

    @Transactional
    public void remove(User user, UUID serverId) {
        Server server = requireModerator(user, serverId);
        String anterior = server.getIconUrl();
        server.setIconUrl(null);
        serverRepository.save(server);
        liberarAnterior(anterior, null);
    }

    /**
     * Só dono e moderador trocam o ícone.
     *
     * <p>404 e não 403 para quem não é membro: um 403 confirmaria que o
     * servidor existe.
     */
    private Server requireModerator(User user, UUID serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        String papel = memberRepository.findByServerIdAndUserId(serverId, user.getId())
                .map(membro -> membro.getRole())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if ("MEMBER".equals(papel)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Só o dono e moderadores podem trocar o ícone");
        }
        return server;
    }

    private void liberarAnterior(String urlAnterior, String idNovo) {
        if (urlAnterior == null || !urlAnterior.startsWith("/api/attachments/")) {
            return;
        }
        String id = urlAnterior.substring("/api/attachments/".length());
        if (id.equals(idNovo)) {
            return;
        }
        try {
            attachmentRepository.findById(UUID.fromString(id)).ifPresent(antigo -> {
                String chave = antigo.getStorageKey();
                attachmentRepository.delete(antigo);
                attachmentRepository.flush();
                // A deduplicação faz anexos compartilharem o arquivo: só apaga
                // os bytes quando ninguém mais aponta para eles.
                if (attachmentRepository.countByStorageKey(chave) == 0) {
                    storage.delete(chave);
                }
            });
        } catch (IllegalArgumentException ex) {
            log.debug("Ícone anterior não era um anexo gerenciado");
        } catch (RuntimeException ex) {
            log.warn("Não foi possível liberar o ícone anterior", ex);
        }
    }
}
