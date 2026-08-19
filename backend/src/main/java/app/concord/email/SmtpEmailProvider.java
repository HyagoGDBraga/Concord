package app.concord.email;

import app.concord.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Provedor SMTP — atende tanto o Mailpit em desenvolvimento quanto um provedor
 * transacional em produção, sem diferença de código.
 *
 * <p>Não existe servidor de e-mail próprio no projeto: um MTA autogerido em VPS
 * cai em blocklist e exige manutenção de reputação, com entrega pior.
 */
@Component
public class SmtpEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public SmtpEmailProvider(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().from(), properties.mail().fromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);

            // O destinatário é mascarado no log: o endereço completo é dado
            // pessoal e não precisa estar no log de operação.
            log.info("E-mail enviado via SMTP para {}", mask(message.to()));
        } catch (Exception ex) {
            throw new EmailDeliveryException("Falha ao enviar e-mail via SMTP", ex);
        }
    }

    @Override
    public String name() {
        return "smtp";
    }

    static String mask(String email) {
        if (email == null) {
            return "-";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
