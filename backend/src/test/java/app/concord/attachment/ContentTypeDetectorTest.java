package app.concord.attachment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deteccao de tipo pelo conteudo.
 *
 * <p>E a defesa contra o ataque mais direto de upload: renomear um arquivo
 * executavel para .png e faze-lo ser servido na origem da aplicacao.
 */
class ContentTypeDetectorTest {

    private static byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    @Test
    @DisplayName("reconhece PNG, JPEG e GIF")
    void reconheceImagens() {
        assertThat(ContentTypeDetector.detect(bytes("89504E470D0A1A0A0000000D")).contentType())
                .isEqualTo("image/png");
        assertThat(ContentTypeDetector.detect(bytes("FFD8FFE000104A46494600")).contentType())
                .isEqualTo("image/jpeg");
        assertThat(ContentTypeDetector.detect(bytes("47494638396100000000")).contentType())
                .isEqualTo("image/gif");
    }

    @Test
    @DisplayName("distingue WebP de outros arquivos RIFF")
    void distingueWebp() {
        // RIFF + tamanho + "WEBP" no offset 8.
        byte[] webp = bytes("52494646" + "24000000" + "57454250" + "56503820");
        assertThat(ContentTypeDetector.detect(webp).contentType()).isEqualTo("image/webp");

        // RIFF + "WAVE": tambem comeca com RIFF, mas nao e imagem. Sem a
        // verificacao do offset 8, um audio passaria como foto de perfil.
        byte[] wave = bytes("52494646" + "24000000" + "57415645" + "666D7420");
        ContentTypeDetector.Detected detectado = ContentTypeDetector.detect(wave);
        assertThat(detectado.image()).isFalse();
    }

    @Test
    @DisplayName("HTML renomeado como imagem NAO e aceito como imagem")
    void htmlNaoViraImagem() {
        // "<html><script>" — o caso que produziria XSS armazenado se o tipo
        // viesse do nome do arquivo em vez do conteudo.
        byte[] html = "<html><script>alert(1)</script>".getBytes();
        ContentTypeDetector.Detected detectado = ContentTypeDetector.detect(html);

        assertThat(detectado.image()).isFalse();
        assertThat(detectado.contentType()).isEqualTo("application/octet-stream");
        // octet-stream forca download; o navegador nao interpreta.
        assertThat(detectado.extension()).isEqualTo("bin");
    }

    @Test
    @DisplayName("SVG nao e aceito como imagem")
    void svgNaoEImagem() {
        // SVG e XML e pode conter <script>. Ficar de fora da lista e
        // deliberado, nao esquecimento.
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\">".getBytes();
        assertThat(ContentTypeDetector.detect(svg).image()).isFalse();
    }

    @Test
    @DisplayName("PDF e zip sao reconhecidos, mas nao como imagem")
    void reconheceDocumentos() {
        assertThat(ContentTypeDetector.detect(bytes("255044462D312E37")).contentType())
                .isEqualTo("application/pdf");
        assertThat(ContentTypeDetector.detect(bytes("504B03040A000000")).image()).isFalse();
    }

    @Test
    @DisplayName("arquivo vazio ou curto nao quebra")
    void toleraArquivoCurto() {
        assertThat(ContentTypeDetector.detect(new byte[0]).contentType())
                .isEqualTo("application/octet-stream");
        assertThat(ContentTypeDetector.detect(bytes("89")).contentType())
                .isEqualTo("application/octet-stream");
    }
}
