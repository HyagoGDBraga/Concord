package app.concord.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O extrator de mencoes. Testado a parte porque e a unica logica de texto do
 * sistema, e texto tem casos de borda que nao aparecem no uso normal.
 */
class MentionParserTest {

    @Test
    @DisplayName("extrai mencoes simples, sem repetir")
    void extraiMencoes() {
        assertThat(MentionParser.extract("oi @maria e @joao, tudo bem @maria?"))
                .containsExactly("maria", "joao");
    }

    @Test
    @DisplayName("e-mail no meio do texto nao vira mencao")
    void ignoraEmail() {
        // O @ de um e-mail vem precedido de letra; o padrao exige inicio de
        // texto ou espaco antes.
        assertThat(MentionParser.extract("escreve pra fulano@exemplo.com")).isEmpty();
    }

    @Test
    @DisplayName("aceita mencao no inicio, apos parentese e apos colchete")
    void aceitaDelimitadores() {
        assertThat(MentionParser.extract("@ana chegou")).containsExactly("ana");
        assertThat(MentionParser.extract("aviso (@bruno) e [@carla]"))
                .containsExactly("bruno", "carla");
    }

    @Test
    @DisplayName("nome curto demais nao e username valido")
    void ignoraNomeCurto() {
        // O cadastro exige 3 a 20 caracteres; o extrator segue a mesma regra.
        assertThat(MentionParser.extract("olha o @ab ali")).isEmpty();
    }

    @Test
    @DisplayName("normaliza para minusculas")
    void normalizaCaixa() {
        assertThat(MentionParser.extract("@Maria @MARIA")).containsExactly("maria");
    }

    @Test
    @DisplayName("respeita o teto por mensagem")
    void respeitaTeto() {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            texto.append("@usuario").append(i).append(' ');
        }
        assertThat(MentionParser.extract(texto.toString()))
                .hasSize(MentionParser.MAX_MENTIONS);
    }

    @Test
    @DisplayName("texto vazio ou nulo nao quebra")
    void toleraVazio() {
        assertThat(MentionParser.extract(null)).isEmpty();
        assertThat(MentionParser.extract("   ")).isEmpty();
    }
}
