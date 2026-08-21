package app.concord.attachment;

import app.concord.common.exception.ApiException;
import app.concord.common.exception.ErrorCode;
import app.concord.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Gravação e leitura dos bytes em disco.
 *
 * <p>Três invariantes que este componente garante, e que são a diferença entre
 * upload de arquivo e vulnerabilidade:
 *
 * <ol>
 *   <li><b>O caminho nunca vem do usuário.</b> É gerado a partir de um UUID.
 *       Montar caminho com o nome enviado é como se faz travessia de diretório
 *       — um {@code ../../etc/passwd} bem colocado escreveria fora da pasta.</li>
 *   <li><b>Todo caminho é normalizado e conferido</b> contra a raiz antes de
 *       qualquer leitura, mesmo os que vieram do próprio banco.</li>
 *   <li><b>A extensão sai do tipo detectado</b>, não do nome enviado.</li>
 * </ol>
 */
@Component
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final Path root;

    public FileStorage(AppProperties properties) {
        this.root = Path.of(properties.storage().path()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Não foi possível criar a pasta de armazenamento: " + this.root, ex);
        }
        log.info("Armazenamento de anexos em {}", this.root);
    }

    /** Resultado da gravação. */
    public record Stored(String storageKey, String checksum, long size) {
    }

    /**
     * Grava o arquivo e devolve a chave.
     *
     * <p>O conteúdo é escrito num temporário e movido ao final. Se a gravação
     * falhar no meio, não sobra arquivo parcial que pareça válido.
     */
    public Stored store(MultipartFile file, String extension) {
        // Prefixo por data: mantém as pastas com tamanho administrável e
        // permite apagar um período inteiro sem varrer o resto.
        LocalDate hoje = LocalDate.now();
        String pasta = "%04d/%02d".formatted(hoje.getYear(), hoje.getMonthValue());
        String nome = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        String key = pasta + "/" + nome;

        Path destino = resolve(key);
        Path temporario = destino.resolveSibling(nome + ".part");

        try {
            Files.createDirectories(destino.getParent());

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long tamanho;
            try (InputStream in = file.getInputStream();
                 DigestInputStream digestIn = new DigestInputStream(in, digest)) {
                tamanho = Files.copy(digestIn, temporario, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE);
            return new Stored(key, HexFormat.of().formatHex(digest.digest()), tamanho);

        } catch (Exception ex) {
            try {
                Files.deleteIfExists(temporario);
            } catch (IOException ignored) {
                // Melhor esforço na limpeza do parcial.
            }
            log.error("Falha ao gravar anexo", ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Não foi possível salvar o arquivo");
        }
    }

    public Path pathOf(String storageKey) {
        Path caminho = resolve(storageKey);
        if (!Files.exists(caminho, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Arquivo não encontrado");
        }
        return caminho;
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            log.warn("Não foi possível remover o arquivo do disco", ex);
        }
    }

    public long usedBytes() {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).mapToLong(caminho -> {
                try {
                    return Files.size(caminho);
                } catch (IOException ex) {
                    return 0L;
                }
            }).sum();
        } catch (IOException ex) {
            return -1L;
        }
    }

    /**
     * Converte a chave em caminho absoluto, recusando qualquer coisa que escape
     * da raiz.
     *
     * <p>A verificação vale inclusive para chaves vindas do banco: se uma linha
     * for adulterada, o servidor recusa em vez de ler um arquivo do sistema.
     */
    private Path resolve(String storageKey) {
        Path caminho = root.resolve(storageKey).normalize();
        if (!caminho.startsWith(root)) {
            log.error("Tentativa de acesso fora da pasta de armazenamento");
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return caminho;
    }
}
