package app.concord.common.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Executa uma ação somente depois que a transação corrente for confirmada.
 *
 * <p>Notificação em tempo real precisa disso. Emitir o evento dentro da
 * transação abre a janela em que o destinatário recebe "chegou mensagem",
 * consulta o servidor e não encontra nada — porque o commit ainda não ocorreu,
 * ou porque houve rollback logo depois e a mensagem nunca existiu.
 *
 * <p>Fora de transação, executa imediatamente.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }
}
