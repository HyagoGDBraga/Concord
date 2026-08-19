package app.concord.common.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de taxa por janela fixa, em memória.
 *
 * <p>Decisão: implementação própria em vez de Bucket4j. Para a escala do Concord
 * (dezenas de usuários) uma janela fixa resolve o problema real — conter
 * automação em endpoints públicos — em cinquenta linhas, sem dependência
 * adicional nem risco de incompatibilidade de versão.
 *
 * <p>Limitação aceita conscientemente: o estado vive na JVM e é perdido a cada
 * reinício, e não seria compartilhado entre instâncias. Ambas as coisas só
 * passam a importar se o projeto crescer, e nesse momento a substituição é
 * local a esta classe.
 */
@Component
public class RateLimiter {

    private record Window(Instant expiresAt, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Consome uma unidade da cota.
     *
     * @return {@code true} se a requisição está dentro do limite.
     */
    public boolean tryConsume(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Window updated = windows.compute(key, (k, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new Window(now.plus(window), 1);
            }
            return new Window(current.expiresAt(), current.count() + 1);
        });
        return updated.count() <= limit;
    }

    /** Zera a cota de uma chave. Usado após uma ação bem-sucedida. */
    public void reset(String key) {
        windows.remove(key);
    }

    /** Remove janelas expiradas. Chamado pelo job de limpeza. */
    public int purgeExpired() {
        Instant now = Instant.now();
        int before = windows.size();
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        return before - windows.size();
    }

    int size() {
        return windows.size();
    }
}
