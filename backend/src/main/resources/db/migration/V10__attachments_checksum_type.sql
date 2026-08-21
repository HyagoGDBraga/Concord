-- =============================================================================
-- Concord — V10__attachments_checksum_type.sql
--
-- Corrige o tipo de attachments.checksum: CHAR(64) para TEXT.
--
-- Por que era um problema: a entidade mapeia o campo como String, e o Hibernate
-- espera VARCHAR. CHAR e VARCHAR são tipos JDBC distintos, então a validação de
-- schema (ddl-auto: validate) recusava e a aplicação não subia:
--
--   wrong column type encountered in column [checksum] in table [attachments];
--   found [bpchar (Types#CHAR)], but expecting [varchar(255) (Types#VARCHAR)]
--
-- TEXT + CHECK é o padrão do resto do projeto — user_action_tokens.token_hash
-- guarda um SHA-256 exatamente assim desde a V1. A V9 fugiu do padrão sem
-- motivo.
--
-- Migration nova em vez de editar a V9: o Flyway guarda o checksum de cada
-- arquivo já aplicado, e alterar um deles faria a validação falhar em qualquer
-- banco que já esteja na versão 9.
-- =============================================================================

ALTER TABLE attachments
    ALTER COLUMN checksum TYPE TEXT;

-- O comprimento continua garantido, agora por constraint em vez de pelo tipo.
ALTER TABLE attachments
    ADD CONSTRAINT attachments_checksum_chk
        CHECK (char_length(checksum) = 64);
