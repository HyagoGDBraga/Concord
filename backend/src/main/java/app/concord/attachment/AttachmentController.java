package app.concord.attachment;

import app.concord.auth.ConcordUserDetails;
import app.concord.server.ChannelRepository;
import app.concord.server.ServerMemberRepository;
import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.user.AccountService;
import app.concord.user.User;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/attachments")
public class AttachmentController {

    private final AttachmentService service;
    private final AttachmentRepository repository;
    private final FileStorage storage;
    private final AccountService accountService;
    private final ChannelRepository channelRepository;
    private final ServerMemberRepository memberRepository;

    public AttachmentController(AttachmentService service, AttachmentRepository repository,
                                FileStorage storage, AccountService accountService,
                                ChannelRepository channelRepository,
                                ServerMemberRepository memberRepository) {
        this.service = service;
        this.repository = repository;
        this.storage = storage;
        this.accountService = accountService;
        this.channelRepository = channelRepository;
        this.memberRepository = memberRepository;
    }

    @PostMapping
    public AttachmentDtos.Response upload(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @RequestParam MultipartFile file,
            @RequestParam AttachmentPurpose purpose,
            @RequestParam(required = false) UUID channelId) {

        User user = accountService.requireById(principal.id());

        // Anexo de canal exige participação — verificada aqui, no envio, e de
        // novo no download. As duas checagens são necessárias: o envio impede
        // encher o disco alheio, o download impede ler o que não é seu.
        if (purpose == AttachmentPurpose.MESSAGE) {
            if (channelId == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Informe o canal");
            }
            requireChannelMember(channelId, user.getId());
        }
        return AttachmentDtos.Response.from(service.upload(user, file, purpose, channelId));
    }

    /**
     * Entrega o arquivo.
     *
     * <p>Passa pela aplicação em vez de ser servido direto pelo Caddy: só a
     * aplicação sabe se quem pede participa do canal. Um arquivo estático
     * numa pasta pública seria acessível por quem descobrisse a URL — e URL de
     * anexo circula em log, histórico e cabeçalho.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal ConcordUserDetails principal,
            @PathVariable UUID id) {

        Attachment anexo = service.require(id);

        if (anexo.getPurpose() == AttachmentPurpose.MESSAGE) {
            requireChannelMember(anexo.getChannelId(), principal.id());
        }

        Resource recurso = new FileSystemResource(storage.pathOf(anexo.getStorageKey()));

        // Imagem é exibida; qualquer outra coisa é baixada. 'attachment' impede
        // que um arquivo desconhecido seja interpretado pelo navegador na
        // origem da aplicação.
        boolean exibir = anexo.getContentType().startsWith("image/");
        ContentDisposition disposition = (exibir
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(anexo.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // nosniff impede o navegador de adivinhar um tipo diferente do
                // declarado, que é como um .bin vira script.
                .header("X-Content-Type-Options", "nosniff")
                // CSP restritiva na própria resposta do arquivo: mesmo que algo
                // escape das verificações acima, não executa.
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .contentType(MediaType.parseMediaType(anexo.getContentType()))
                .contentLength(anexo.getSizeBytes())
                // Conteúdo imutável: a chave nunca é reaproveitada.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(recurso);
    }

    private void requireChannelMember(UUID channelId, UUID userId) {
        UUID serverId = channelRepository.findById(channelId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND))
                .getServerId();
        if (!memberRepository.existsByServerIdAndUserId(serverId, userId)) {
            // 404 e não 403: um 403 confirmaria que o anexo existe.
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
