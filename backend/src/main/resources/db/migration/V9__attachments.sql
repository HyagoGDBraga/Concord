-- =============================================================================
-- Concord — V9__attachments.sql
-- Anexos: avatares, ícones de servidor e arquivos em mensagens.
--
-- O BINÁRIO NÃO FICA NO BANCO. A tabela guarda metadado e um caminho; os bytes
-- vão para o disco. Duas razões:
--
--   * bytea infla o WAL e o dump. Um pg_dump que hoje tem megabytes passaria a
--     ter dezenas de gigabytes, e o backup diário deixaria de caber na janela.
--   * servir arquivo direto do disco é ordens de grandeza mais barato que
--     carregá-lo para a memória da JVM a cada requisição.
--
-- A contrapartida honesta: banco e disco podem divergir. O job de expurgo
-- reconcilia nos dois sentidos.
--
-- NOTA: as tabelas de servidor usam o prefixo concord_ (concord_servers,
-- concord_channels). As tabelas originais das Fases 1-4 (users, messages,
-- calls) não usam. Referências abaixo seguem o nome real de cada uma.
-- =============================================================================

CREATE TABLE attachments (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Quem enviou. RESTRICT: exclusão de conta anonimiza, nunca remove a linha.
    uploader_id   UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,

    -- Nome que o usuário deu. Guardado só para exibir e para o download; nunca
    -- usado para montar caminho em disco — é entrada do usuário, e caminho
    -- montado com entrada do usuário é como se faz travessia de diretório.
    original_name TEXT        NOT NULL,

    -- Caminho relativo dentro da pasta de armazenamento, gerado pelo servidor.
    storage_key   TEXT        NOT NULL,

    content_type  TEXT        NOT NULL,
    size_bytes    BIGINT      NOT NULL,

    -- SHA-256 do conteúdo. Permite deduplicar e detectar corrupção.
    checksum      CHAR(64)    NOT NULL,

    -- A que o anexo pertence. Define a retenção: avatar e ícone de servidor
    -- ficam enquanto forem usados; anexo de mensagem expira.
    purpose       TEXT        NOT NULL,

    -- Preenchido quando o anexo é de mensagem.
    message_id    UUID,
    channel_id    UUID        REFERENCES concord_channels(id) ON DELETE CASCADE,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Quando o expurgo pode levar. NULL = permanente (avatar, ícone).
    expires_at    TIMESTAMPTZ,

    CONSTRAINT attachments_purpose_chk
        CHECK (purpose IN ('AVATAR', 'SERVER_ICON', 'SERVER_BANNER', 'MESSAGE')),
    -- 5 MiB. O limite existe no cliente, no Spring e aqui: as três camadas,
    -- porque as duas primeiras podem ser contornadas.
    CONSTRAINT attachments_size_chk
        CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT attachments_name_chk
        CHECK (char_length(original_name) BETWEEN 1 AND 255),
    -- Anexo de mensagem precisa saber a que canal pertence, senão não há como
    -- verificar quem pode baixá-lo.
    CONSTRAINT attachments_message_chk
        CHECK (purpose <> 'MESSAGE' OR channel_id IS NOT NULL)
);

CREATE INDEX attachments_uploader_idx ON attachments (uploader_id, created_at DESC);
CREATE INDEX attachments_message_idx  ON attachments (message_id)
    WHERE message_id IS NOT NULL;
CREATE INDEX attachments_channel_idx  ON attachments (channel_id, created_at DESC)
    WHERE channel_id IS NOT NULL;

-- Índice parcial que sustenta o expurgo: percorre só o que pode expirar.
CREATE INDEX attachments_expiry_idx   ON attachments (expires_at)
    WHERE expires_at IS NOT NULL;

-- Deduplicação: o mesmo arquivo enviado duas vezes reaproveita os bytes.
CREATE INDEX attachments_checksum_idx ON attachments (checksum);

-- ------------------------------------------------- referência nas entidades --
ALTER TABLE users
    ADD COLUMN avatar_attachment_id UUID REFERENCES attachments(id) ON DELETE SET NULL;

ALTER TABLE concord_servers
    ADD COLUMN icon_attachment_id   UUID REFERENCES attachments(id) ON DELETE SET NULL,
    ADD COLUMN banner_attachment_id UUID REFERENCES attachments(id) ON DELETE SET NULL;

COMMENT ON COLUMN attachments.storage_key IS
    'Caminho relativo gerado pelo servidor. Nunca derivado de original_name.';
COMMENT ON COLUMN attachments.expires_at IS
    'NULL = permanente. Anexo de mensagem nasce com 14 dias.';
