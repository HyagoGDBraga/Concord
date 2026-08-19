package app.concord.email;

import app.concord.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Monta e despacha os e-mails transacionais do Concord.
 *
 * <p>Falha de envio nunca derruba a operação de negócio: se o e-mail de
 * verificação não sair, a conta continua criada e o usuário pode pedir reenvio.
 * O erro vai para o log com o destinatário mascarado.
 *
 * <p>Tokens são interpolados na URL e <b>nunca</b> registrados em log, em
 * nenhum nível.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailProvider provider;
    private final EmailTemplates templates;
    private final AppProperties properties;

    public EmailService(EmailProvider provider, EmailTemplates templates,
                        AppProperties properties) {
        this.provider = provider;
        this.templates = templates;
        this.properties = properties;
    }

    public void sendEmailVerification(String to, String displayName, String token) {
        String link = properties.publicUrl() + "/verify-email?token=" + encode(token);
        send(to, "Confirme seu e-mail no Concord", "verify-email",
                EmailTemplates.vars("displayName", displayName, "link", link));
    }

    public void sendPasswordReset(String to, String displayName, String token) {
        String link = properties.publicUrl() + "/reset-password?token=" + encode(token);
        send(to, "Redefinição de senha no Concord", "password-reset",
                EmailTemplates.vars("displayName", displayName, "link", link));
    }

    public void sendEmailChangeConfirmation(String to, String displayName, String token) {
        String link = properties.publicUrl() + "/confirm-email-change?token=" + encode(token);
        send(to, "Confirme seu novo e-mail no Concord", "email-change",
                EmailTemplates.vars("displayName", displayName, "link", link));
    }

    /** Aviso genérico ao titular: conta desativada, excluída, e-mail alterado. */
    public void sendNotice(String to, String displayName, String subject, String heading,
                           String body) {
        send(to, subject, "notice",
                EmailTemplates.vars("displayName", displayName, "heading", heading, "body", body));
    }

    /**
     * Enviado quando alguém tenta se cadastrar com um e-mail já existente.
     *
     * <p>É o que permite ao endpoint de cadastro responder sempre 202 sem
     * revelar se a conta existe: quem já tem conta descobre a tentativa pelo
     * próprio e-mail, e quem está sondando não descobre nada.
     */
    public void sendRegistrationAttempt(String to, String displayName) {
        sendNotice(to, displayName,
                "Tentativa de cadastro no Concord",
                "Alguém tentou criar uma conta com seu e-mail",
                "Se foi você, use a opção \"Esqueci minha senha\" para entrar na conta existente. "
                        + "Se não foi, nenhuma ação é necessária: nenhuma conta nova foi criada.");
    }

    private void send(String to, String subject, String template,
                      java.util.Map<String, String> variables) {
        try {
            String html = templates.render(template, variables);
            provider.send(new EmailMessage(to, subject, html, templates.toPlainText(html)));
        } catch (Exception ex) {
            log.error("Falha ao enviar e-mail '{}' para {} via {}",
                    template, SmtpEmailProvider.mask(to), provider.name(), ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
