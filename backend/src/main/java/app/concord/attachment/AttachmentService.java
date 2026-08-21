package app.concord.attachment;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.common.ratelimit.RateLimiter;
import app.concord.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Recebimento de arquivos.
 *
 * <p>A validação acontece em quatro camadas, e a ordem importa: tamanho antes
 * de ler, conteúdo antes de gravar, tipo antes de aceitar, cota antes de tudo.
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    /** 5 MiB, o mesmo limite declarado no banco e no Spring. */
    public static final long MAX_SIZE = 5L * 1024 * 1024;

    /** Bytes lidos para identificar o tipo. */
    private static final int MAGIC_BYTES = 16;

    private final AttachmentRepository repository;
    private final FileStorage storage;
    private final RateLimiter rateLimiter;

    public AttachmentService(AttachmentRepository repository, FileStorage storage,
                             RateLimiter rateLimiter) {
        this.repository = repository;
        this.storage = storage;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public Attachment upload(User user, MultipartFile file, AttachmentPurpose purpose,
                             UUID channelId) {
        return upload(user, file, purpose, channelId, null);
    }

    @Transactional
    public Attachment upload(User user, MultipartFile file, AttachmentPurpose purpose,
                             UUID channelId, UUID conversationId) {

        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Arquivo vazio");
        }
        // Antes de qualquer leitura: o tamanho declarado já basta para recusar.
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "O arquivo excede 5 MB");
        }
        // 20 envios por hora. Sem isso, uma conta comprometida encheria o disco
        // da máquina em minutos — e disco cheio derruba o PostgreSQL junto.
        if (!rateLimiter.tryConsume("upload:" + user.getId(), 20, Duration.ofHours(1))) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Muitos envios. Tente novamente em uma hora");
        }

        ContentTypeDetector.Detected detectado = detect(file);

        // Avatares e ícones só aceitam imagem. Sem esta regra, um PDF viraria
        // foto de perfil e quebraria toda tela que espera <img>.
        if (purpose.isImageOnly() && !detectado.image()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Envie uma imagem (PNG, JPEG, GIF ou WebP)");
        }

        FileStorage.Stored gravado = storage.store(file, detectado.extension());

        // Confere o tamanho real após a gravação: getSize() vem do cliente, e o
        // que importa é quanto de fato foi escrito em disco.
        if (gravado.size() > MAX_SIZE) {
            storage.delete(gravado.storageKey());
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "O arquivo excede 5 MB");
        }

        // Deduplicação: se os bytes já existem, aponta para o mesmo arquivo e
        // apaga a cópia recém-gravada.
        String storageKey = gravado.storageKey();
        List<Attachment> iguais =
                repository.findByChecksum(gravado.checksum(), gravado.size());
        if (!iguais.isEmpty()) {
            storage.delete(storageKey);
            storageKey = iguais.get(0).getStorageKey();
        }

        Attachment anexo = new Attachment(user.getId(), sanitizeName(file.getOriginalFilename()),
                storageKey, detectado.contentType(), gravado.size(), gravado.checksum(),
                purpose, channelId, conversationId);

        log.debug("Anexo recebido: {} bytes, tipo {}", gravado.size(), detectado.contentType());
        return repository.save(anexo);
    }

    private ContentTypeDetector.Detected detect(MultipartFile file) {
        try (var in = file.getInputStream()) {
            byte[] head = in.readNBytes(MAGIC_BYTES);
            return ContentTypeDetector.detect(head);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Não foi possível ler o arquivo");
        }
    }

    /**
     * Limpa o nome para exibição.
     *
     * <p>O nome não é usado para montar caminho — isso é responsabilidade do
     * {@code FileStorage}, que gera o próprio. Ainda assim ele é exibido e
     * oferecido no download, então separadores e caracteres de controle saem.
     */
    private String sanitizeName(String original) {
        if (original == null || original.isBlank()) {
            return "arquivo";
        }
        String limpo = original
                .replaceAll("[\\\\/\\p{Cntrl}]", "")
                .trim();
        if (limpo.isBlank()) {
            return "arquivo";
        }
        return limpo.length() > 255 ? limpo.substring(0, 255) : limpo;
    }

    @Transactional(readOnly = true)
    public Attachment require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Anexo não encontrado"));
    }
}
