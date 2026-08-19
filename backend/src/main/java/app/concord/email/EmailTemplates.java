package app.concord.email;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carregador de templates de e-mail.
 *
 * <p>Substituição simples de {@code {{chave}}} sobre arquivos HTML em
 * {@code resources/email/}. Deliberadamente sem Thymeleaf: são quatro
 * mensagens sem lógica condicional, e uma engine de template completa seria
 * dependência sem contrapartida.
 *
 * <p>Todo valor interpolado passa por escape de HTML — nomes de exibição são
 * texto fornecido pelo usuário e não podem injetar marcação no e-mail.
 */
@Component
public class EmailTemplates {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String render(String templateName, Map<String, String> variables) {
        String template = cache.computeIfAbsent(templateName, this::load);
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", escape(entry.getValue()));
        }
        return result;
    }

    /** Versão em texto puro: remove as tags do HTML já renderizado. */
    public String toPlainText(String html) {
        return html.replaceAll("(?s)<style.*?</style>", "")
                .replaceAll("(?s)<head.*?</head>", "")
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</(p|div|h1|h2|tr)>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String load(String templateName) {
        ClassPathResource resource = new ClassPathResource("email/" + templateName + ".html");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Template de e-mail ausente: " + templateName, ex);
        }
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Atalho para montar o mapa de variáveis. */
    public static Map<String, String> vars(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Número ímpar de argumentos");
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }
}
