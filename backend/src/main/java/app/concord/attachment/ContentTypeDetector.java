package app.concord.attachment;

import java.util.Map;

/**
 * Detecção de tipo pelo conteúdo, não pela extensão nem pelo cabeçalho.
 *
 * <p>O {@code Content-Type} do formulário e o nome do arquivo vêm do cliente, e
 * ambos são triviais de forjar. Renomear {@code payload.html} para
 * {@code foto.png} bastaria para fazer o servidor guardar — e depois servir —
 * uma página com script, na mesma origem da aplicação. Isso é XSS armazenado
 * com passo a passo.
 *
 * <p>Aqui os primeiros bytes são comparados com assinaturas conhecidas. Se
 * nenhuma casar, o arquivo é tratado como binário genérico e nunca como algo
 * que o navegador possa interpretar.
 */
public final class ContentTypeDetector {

    /**
     * Assinaturas, em hexadecimal, do início do arquivo.
     *
     * <p>RIFF (52494646) NÃO está aqui de propósito. Ele é um contêiner: WebP,
     * WAV e AVI começam igual. Mapeá-lo direto para image/webp fazia um arquivo
     * de áudio ser classificado como imagem — e portanto aceito como foto de
     * perfil. WebP é identificado abaixo, conferindo "WEBP" no offset 8.
     */
    private static final Map<String, String> MAGIC = Map.of(
            "89504E47", "image/png",
            "FFD8FF", "image/jpeg",
            "47494638", "image/gif",
            "25504446", "application/pdf",
            "504B0304", "application/zip",
            "1F8B", "application/gzip"
    );

    private ContentTypeDetector() {
    }

    public record Detected(String contentType, String extension, boolean image) {
    }

    public static Detected detect(byte[] head) {
        String hex = HEX.formatHex(head).toUpperCase();

        // WebP é RIFF com "WEBP" no offset 8; sem isso, um .wav seria aceito
        // como imagem, já que também começa com RIFF.
        if (hex.startsWith("52494646") && hex.length() >= 24
                && hex.substring(16, 24).equals("57454250")) {
            return new Detected("image/webp", "webp", true);
        }

        for (Map.Entry<String, String> entrada : MAGIC.entrySet()) {
            if (hex.startsWith(entrada.getKey())) {
                String tipo = entrada.getValue();
                return new Detected(tipo, extensionOf(tipo), tipo.startsWith("image/"));
            }
        }

        // Desconhecido: octet-stream força o download em vez da exibição.
        return new Detected("application/octet-stream", "bin", false);
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            case "application/zip" -> "zip";
            case "application/gzip" -> "gz";
            default -> "bin";
        };
    }

    private static final java.util.HexFormat HEX = java.util.HexFormat.of();
}
