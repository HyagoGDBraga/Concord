package app.concord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do backend do Concord.
 *
 * <p>Arquitetura (ADR-01): monolito modular organizado por feature. Cada pacote
 * irmão deste — {@code user}, {@code auth}, {@code admin}, {@code audit},
 * {@code token}, {@code email}, {@code privacy}, {@code settings} — contém seu
 * próprio controller, service, repository, entidade e DTOs. Não existem pacotes
 * globais {@code controllers/} ou {@code services/}: agrupar por camada técnica
 * espalha uma única mudança funcional por todo o projeto.
 *
 * <p>{@code @EnableScheduling} habilita as tarefas de retenção de dados —
 * expurgo de tokens, de contas nunca verificadas e de registros de auditoria
 * vencidos. Sem elas, a política de retenção da LGPD seria só um texto no
 * documento.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ConcordApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConcordApplication.class, args);
    }
}
