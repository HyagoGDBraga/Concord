package app.concord.server;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai menções {@code @usuario} do texto.
 *
 * <p>O padrão casa exatamente o formato de username aceito no cadastro: de 3 a
 * 20 caracteres entre letras, números e sublinhado. Assim um endereço de e-mail
 * no meio da frase não vira menção — o {@code @} de "fulano@exemplo.com" é
 * precedido de letra, e a expressão exige início de texto ou espaço antes.
 */
public final class MentionParser {

    private static final Pattern MENTION =
            Pattern.compile("(?<=^|[\\s(\\[])@([A-Za-z0-9_]{3,20})\\b");

    /** Teto por mensagem: evita que uma mensagem notifique o servidor inteiro. */
    public static final int MAX_MENTIONS = 20;

    private MentionParser() {
    }

    /** Usernames citados, sem repetição, na ordem em que aparecem. */
    public static Set<String> extract(String body) {
        Set<String> encontrados = new LinkedHashSet<>();
        if (body == null || body.isBlank()) {
            return encontrados;
        }
        Matcher matcher = MENTION.matcher(body);
        while (matcher.find() && encontrados.size() < MAX_MENTIONS) {
            encontrados.add(matcher.group(1).toLowerCase());
        }
        return encontrados;
    }
}
