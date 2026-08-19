package app.concord.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariantes de arquitetura verificadas sobre o codigo-fonte.
 *
 * <p>Estas regras vinham sendo mantidas por comentario e disciplina desde a
 * Fase 2. Comentario nao impede ninguem de escrever o import; teste impede.
 *
 * <p>A verificacao le os arquivos .java diretamente, sem ArchUnit. Nao e purismo
 * contra dependencias: sao tres regras simples, e uma biblioteca de analise de
 * bytecode traria mais superficie do que resolve aqui. Se as regras crescerem em
 * numero ou sofisticacao, ArchUnit passa a valer a pena.
 */
class PrivacyBoundaryTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/app/concord");

    /**
     * A promessa central da decisao D-04: administradores nao tem acesso a
     * conteudo privado, e isso nao e uma configuracao desligada — e a ausencia
     * de qualquer caminho de codigo.
     *
     * <p>Se alguem um dia importar MessageRepository dentro de admin/, este
     * teste falha antes de o codigo chegar a producao. E o unico mecanismo que
     * torna a promessa verificavel.
     */
    @Test
    @DisplayName("o pacote admin nao alcanca mensagens, conversas nem chamadas")
    void adminNaoAlcancaConteudoPrivado() {
        List<String> proibidos = List.of(
                "app.concord.message",
                "app.concord.conversation",
                "app.concord.call");

        List<String> violacoes = violacoesDeImport(SOURCE_ROOT.resolve("admin"), proibidos);

        assertThat(violacoes)
                .as("O painel administrativo nao pode importar conteudo privado. "
                        + "Se um recurso novo precisa disso, a decisao D-04 precisa "
                        + "ser reaberta antes — nao contornada aqui.")
                .isEmpty();
    }

    /**
     * O audit_log tem retencao de 6 a 60 meses. Registrar ali quem conversou
     * com quem construiria o acervo de metadados que o produto existe para nao
     * ter (decisao da Fase 3).
     */
    @Test
    @DisplayName("mensagens e contatos nao escrevem no audit_log")
    void chatNaoEAuditado() {
        List<String> proibidos = List.of("app.concord.audit.AuditService");

        List<String> violacoes = new ArrayList<>();
        violacoes.addAll(violacoesDeImport(SOURCE_ROOT.resolve("message"), proibidos));
        violacoes.addAll(violacoesDeImport(SOURCE_ROOT.resolve("conversation"), proibidos));

        assertThat(violacoes)
                .as("Metadado de conversa costuma revelar mais que o conteudo. "
                        + "O que e auditavel continua sendo autenticacao e acao "
                        + "administrativa.")
                .isEmpty();
    }

    /**
     * Toda entidade JPA precisa de construtor sem argumentos acessivel ao
     * Hibernate. Esquecer disso produz um erro de inicializacao que so aparece
     * na primeira consulta — tarde demais.
     */
    @Test
    @DisplayName("toda entidade JPA tem construtor protegido sem argumentos")
    void entidadesTemConstrutorPadrao() {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosJava(SOURCE_ROOT)) {
            String conteudo = ler(arquivo);
            if (!conteudo.contains("@Entity")) {
                continue;
            }
            String nome = arquivo.getFileName().toString().replace(".java", "");
            boolean temConstrutorPadrao =
                    conteudo.contains("protected " + nome + "()")
                            || conteudo.contains("public " + nome + "()");

            if (!temConstrutorPadrao) {
                violacoes.add(arquivo.toString());
            }
        }

        assertThat(violacoes).isEmpty();
    }

    /**
     * Segredo em codigo versionado e segredo vazado. Toda credencial vem de
     * variavel de ambiente.
     */
    @Test
    @DisplayName("nenhum segredo literal no codigo-fonte")
    void semSegredoNoCodigo() {
        List<String> suspeitos = List.of(
                "static-auth-secret=",
                "password=\"",
                "secret=\"",
                "BEGIN RSA PRIVATE KEY",
                "BEGIN PRIVATE KEY");

        List<String> violacoes = new ArrayList<>();
        for (Path arquivo : arquivosJava(SOURCE_ROOT)) {
            String conteudo = ler(arquivo);
            for (String suspeito : suspeitos) {
                if (conteudo.contains(suspeito)) {
                    violacoes.add(arquivo + " contem '" + suspeito + "'");
                }
            }
        }

        assertThat(violacoes).isEmpty();
    }

    /* --------------------------------------------------------- utilitarios */

    private List<String> violacoesDeImport(Path pacote, List<String> proibidos) {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosJava(pacote)) {
            String conteudo = ler(arquivo);
            for (String proibido : proibidos) {
                if (conteudo.contains("import " + proibido)) {
                    violacoes.add(arquivo.getFileName() + " importa " + proibido);
                }
            }
        }
        return violacoes;
    }

    private List<Path> arquivosJava(Path raiz) {
        if (!Files.isDirectory(raiz)) {
            return List.of();
        }
        try (Stream<Path> caminhos = Files.walk(raiz)) {
            return caminhos
                    .filter(Files::isRegularFile)
                    .filter(caminho -> caminho.toString().endsWith(".java"))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao percorrer " + raiz, ex);
        }
    }

    private String ler(Path arquivo) {
        try {
            return Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao ler " + arquivo, ex);
        }
    }
}
