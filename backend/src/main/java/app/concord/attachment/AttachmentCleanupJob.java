package app.concord.attachment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Expurgo de anexos vencidos.
 *
 * <p>Anexo de mensagem vive 14 dias. Passado o prazo, o registro sai e o
 * arquivo também — mas só quando nenhum outro registro apontar para ele, já que
 * a deduplicação faz vários anexos compartilharem os mesmos bytes. Apagar sem
 * essa verificação quebraria os anexos que ainda estão dentro do prazo.
 */
@Component
public class AttachmentCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AttachmentCleanupJob.class);

    /** Lote por execução: um expurgo não deve prender a transação por minutos. */
    private static final int BATCH = 500;

    private final AttachmentRepository repository;
    private final FileStorage storage;

    public AttachmentCleanupJob(AttachmentRepository repository, FileStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Scheduled(cron = "0 20 4 * * *")
    @Transactional
    public void purgeExpired() {
        List<Attachment> vencidos =
                repository.findExpired(Instant.now(), PageRequest.of(0, BATCH));
        if (vencidos.isEmpty()) {
            return;
        }

        int arquivosRemovidos = 0;
        for (Attachment anexo : vencidos) {
            repository.delete(anexo);
            // flush implícito no fim da transação não serve aqui: a contagem
            // precisa refletir a remoção que acabou de acontecer.
            repository.flush();

            if (repository.countByStorageKey(anexo.getStorageKey()) == 0) {
                storage.delete(anexo.getStorageKey());
                arquivosRemovidos++;
            }
        }

        log.info("Expurgo de anexos: {} registros, {} arquivos removidos do disco",
                vencidos.size(), arquivosRemovidos);
    }

    /** Uso do disco, uma vez por dia, para dar aviso antes de encher. */
    @Scheduled(cron = "0 30 4 * * *")
    public void reportUsage() {
        long emDisco = storage.usedBytes();
        long noBanco = repository.totalBytes();
        if (emDisco < 0) {
            return;
        }
        log.info("Anexos: {} MB em disco, {} MB registrados",
                emDisco / 1_048_576, noBanco / 1_048_576);

        // Divergência grande indica arquivo órfão — gravado e nunca registrado,
        // ou registro apagado sem o arquivo.
        long diferenca = Math.abs(emDisco - noBanco);
        if (noBanco > 0 && diferenca > noBanco / 10) {
            log.warn("Disco e banco divergem em {} MB. Pode haver arquivo órfão.",
                    diferenca / 1_048_576);
        }
    }
}
