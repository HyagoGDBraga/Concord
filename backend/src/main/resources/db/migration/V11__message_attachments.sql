-- =============================================================================
-- Concord — V11__message_attachments.sql
-- Anexos em mensagens de conversa direta e de canal.
--
-- A tabela attachments (V9) já suporta anexo de canal, mas não de conversa
-- direta: só existia channel_id. Aqui ela ganha conversation_id, e a coluna que
-- aponta para a mensagem passa a existir nos dois formatos, porque mensagem de
-- conversa e mensagem de canal vivem em TABELAS diferentes — messages e
-- concord_channel_messages.
--
-- Duas colunas em vez de uma tabela polimórfica: chave estrangeira de verdade
-- em cada uma, e o banco recusando anexo órfão. Uma coluna "tipo + id solto"
-- não teria integridade nenhuma.
-- =============================================================================

ALTER TABLE attachments
    ADD COLUMN conversation_id UUID REFERENCES conversations(id) ON DELETE CASCADE,
    -- Mensagem de conversa direta.
    ADD COLUMN dm_message_id   UUID REFERENCES messages(id) ON DELETE CASCADE,
    -- Mensagem de canal. A coluna message_id da V9 permanece sem FK por
    -- compatibilidade; esta é a versão com integridade.
    ADD COLUMN channel_message_id UUID
        REFERENCES concord_channel_messages(id) ON DELETE CASCADE;

-- A regra antiga da V9 exigia channel_id em TODO anexo de mensagem — escrita
-- quando conversa direta ainda não era um destino possível. Ela precisa sair
-- antes da nova, senão as duas se contradizem e nenhum anexo de conversa entra.
ALTER TABLE attachments
    DROP CONSTRAINT IF EXISTS attachments_message_chk;

-- Anexo de mensagem precisa apontar para exatamente um destino: canal OU
-- conversa. Sem isto não haveria como decidir quem tem permissão de baixar.
ALTER TABLE attachments
    ADD CONSTRAINT attachments_destino_chk
        CHECK (
            purpose <> 'MESSAGE'
            OR (channel_id IS NULL) <> (conversation_id IS NULL)
        );

CREATE INDEX attachments_conversation_idx
    ON attachments (conversation_id, created_at DESC)
    WHERE conversation_id IS NOT NULL;

CREATE INDEX attachments_dm_message_idx
    ON attachments (dm_message_id)
    WHERE dm_message_id IS NOT NULL;

CREATE INDEX attachments_channel_message_idx
    ON attachments (channel_message_id)
    WHERE channel_message_id IS NOT NULL;

COMMENT ON COLUMN attachments.conversation_id IS
    'Conversa direta a que o anexo pertence. Exclusivo com channel_id.';
