package app.concord.email;

/**
 * Fronteira com o fornecedor de e-mail.
 *
 * <p>Decisão D-07: o backend nunca importa SDK de fornecedor. Trocar de provedor
 * é trocar a implementação desta interface e as variáveis de ambiente — nenhum
 * serviço de negócio muda.
 *
 * <p>Limite conhecido: SMTP entrega a mensagem, mas não devolve status de
 * entrega nem bounce. Isso exige webhook de entrada do provedor, que é um
 * endpoint público e precisa de verificação de assinatura — previsto para a
 * Fase 7. Na Fase 2 o bounce é tratado indiretamente: conta não verificada em 7
 * dias é expurgada.
 */
public interface EmailProvider {

    /**
     * Envia a mensagem.
     *
     * @throws EmailDeliveryException se a entrega ao provedor falhar
     */
    void send(EmailMessage message);

    /** Nome do provedor, para log e diagnóstico. */
    String name();
}
