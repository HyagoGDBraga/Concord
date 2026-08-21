package app.concord.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração da aplicação, vinda de {@code application.yml} e de variáveis de
 * ambiente. Nenhum segredo é declarado com valor padrão.
 */
@ConfigurationProperties(prefix = "concord")
public record AppProperties(
        /** URL pública do frontend. Base dos links enviados por e-mail. */
        String publicUrl,
        /** Valor inicial de registration.open, aplicado apenas na primeira subida. */
        boolean registrationOpen,
        /** E-mail que pode ser promovido a ADMIN uma única vez. Pode ser vazio. */
        String bootstrapAdminEmail,
        /** Prazo para expurgo de contas que nunca confirmaram o e-mail. */
        Duration unverifiedAccountTtl,
        Mail mail,
        Login login,
        Turn turn,
        Legal legal,
        Webhook webhook,
        Storage storage
) {

    public record Mail(String from, String fromName) {
    }

    /**
     * Política de bloqueio temporário por falhas de login.
     *
     * <p>O backoff é exponencial a partir de {@code lockBaseDuration}, limitado
     * por {@code lockMaxDuration}.
     */
    public record Login(int maxFailedAttempts, Duration lockBaseDuration, Duration lockMaxDuration) {
    }

    /**
     * Servidores ICE.
     *
     * @param enabled       liga o TURN. Em rede local o STUN costuma bastar;
     *                      pela internet, sem TURN as chamadas falham em NAT
     *                      simétrico e em redes corporativas
     * @param stunUrl       endereço do STUN, anônimo e sem credencial
     * @param urls          endereços do TURN, normalmente UDP e TCP
     * @param secret        segredo compartilhado com o coturn
     *                      ({@code static-auth-secret}). Nunca vai ao cliente
     * @param credentialTtl validade da credencial efêmera derivada do segredo
     */
    public record Turn(
            boolean enabled,
            String stunUrl,
            java.util.List<String> urls,
            String secret,
            Duration credentialTtl
    ) {
    }

    /**
     * Versões vigentes dos documentos legais.
     *
     * <p>Mudar a versão aqui invalida os aceites anteriores e faz o sistema
     * pedir um aceite novo — que é exatamente o comportamento desejado quando o
     * texto muda.
     */
    public record Legal(String termsVersion, String privacyVersion) {
    }

    /**
     * @param emailSecret segredo usado para verificar a assinatura do webhook de
     *                    bounce. Vazio desabilita o endpoint por completo
     */
    public record Webhook(String emailSecret) {
    }

    /**
     * @param path pasta onde os bytes dos anexos sao gravados. Em producao deve
     *             apontar para um volume persistente — dentro do container, os
     *             arquivos morreriam no proximo deploy
     */
    public record Storage(String path) {
    }
}
