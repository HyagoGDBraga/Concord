-- =============================================================================
-- Concord — V13__message_body_optional.sql
-- Mensagem pode ser só um arquivo.
--
-- As constraints da V2 e da V8 exigiam corpo com pelo menos 1 caractere em toda
-- mensagem não apagada. Faziam sentido quando texto era a única coisa que uma
-- mensagem podia conter; com anexos, mandar uma foto sem legenda passa a ser o
-- caso mais comum — e era recusado pelo banco.
--
-- O corpo continua NÃO podendo ser espaço em branco: string vazia é a ausência
-- de texto, "   " é lixo. A diferença importa na hora de decidir se a mensagem
-- tem algo a exibir.
-- =============================================================================

-- --------------------------------------------------- conversas diretas ------
ALTER TABLE messages
    DROP CONSTRAINT IF EXISTS messages_body_chk,
    DROP CONSTRAINT IF EXISTS messages_not_deleted_chk;

ALTER TABLE messages
    ADD CONSTRAINT messages_body_chk
        CHECK (body IS NULL OR char_length(body) <= 4000),
    -- Mensagem viva precisa ter corpo (mesmo que vazio); apagada não tem.
    ADD CONSTRAINT messages_not_deleted_chk
        CHECK (deleted_at IS NOT NULL OR body IS NOT NULL),
    -- Nada de corpo só com espaço.
    ADD CONSTRAINT messages_body_trimmed_chk
        CHECK (body IS NULL OR body = trim(body) OR char_length(trim(body)) > 0);

-- ------------------------------------------------ mensagens de canal --------
ALTER TABLE concord_channel_messages
    DROP CONSTRAINT IF EXISTS concord_channel_messages_body_chk;

ALTER TABLE concord_channel_messages
    ADD CONSTRAINT concord_channel_messages_body_chk
        CHECK (body IS NULL OR char_length(body) <= 4000);

COMMENT ON COLUMN messages.body IS
    'Texto da mensagem. Vazio quando a mensagem contém apenas anexos.';
