package app.concord.call;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Encerra chamadas que ficaram penduradas.
 *
 * <p>Existe porque nem toda chamada termina com alguém clicando em desligar:
 * bateria acabando, aba fechada à força, queda de rede. Sem esta varredura, uma
 * dessas ficaria {@code RINGING} ou {@code ACTIVE} para sempre, e a regra de
 * "uma chamada aberta por pessoa" impediria a pessoa de ligar de novo.
 */
@Component
public class CallReaper {

    private static final Logger log = LoggerFactory.getLogger(CallReaper.class);

    /** Quanto o telefone toca antes de virar chamada perdida. */
    private static final Duration RINGING_TIMEOUT = Duration.ofSeconds(45);

    /**
     * Teto de duração de uma chamada.
     *
     * <p>Generoso de propósito: só existe para capturar chamada esquecida. A
     * queda de conexão já é tratada de imediato pelo {@code PresenceService}.
     */
    private static final Duration ACTIVE_TIMEOUT = Duration.ofHours(6);

    /** Retenção do histórico de chamadas. */
    private static final Duration HISTORY_RETENTION = Duration.ofDays(180);

    private final CallRepository callRepository;
    private final CallService callService;

    public CallReaper(CallRepository callRepository, CallService callService) {
        this.callRepository = callRepository;
        this.callService = callService;
    }

    /** Roda com frequência porque "chamada perdida" precisa aparecer rápido. */
    @Scheduled(fixedDelay = 15_000L, initialDelay = 30_000L)
    @Transactional
    public void closeStaleCalls() {
        Instant now = Instant.now();

        for (Call call : callRepository.findStaleRinging(now.minus(RINGING_TIMEOUT))) {
            callService.endByReaper(call, CallEndReason.MISSED);
        }
        for (Call call : callRepository.findStaleActive(now.minus(ACTIVE_TIMEOUT))) {
            callService.endByReaper(call, CallEndReason.FAILED);
            log.info("Chamada encerrada por exceder a duração máxima");
        }
    }

    /**
     * Aplica a retenção do histórico.
     *
     * <p>Registro de chamada é metadado: diz quem falou com quem, quando e por
     * quanto tempo. Guardar para sempre não serve ao usuário e serve a quem
     * quiser reconstruir sua rotina.
     */
    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void purgeOldHistory() {
        int removed = callRepository.deleteBefore(Instant.now().minus(HISTORY_RETENTION));
        if (removed > 0) {
            log.info("Registros de chamada removidos por retenção: {}", removed);
        }
    }
}
