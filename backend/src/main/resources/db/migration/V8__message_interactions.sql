-- =============================================================================
-- Concord — V8__message_interactions.sql
-- Respostas, menções, reações, fixação, edição e exclusão em canais.
--
-- Tudo se apoia na tabela que já existe. Nenhuma das cinco funcionalidades
-- precisa de uma tabela de mensagem nova — o que muda é o que orbita cada
-- mensagem.
-- =============================================================================

-- ------------------------------------------- respostas, edição e fixação ----
ALTER TABLE concord_channel_messages
    -- SET NULL: apagar a mensagem original não apaga quem respondeu a ela,
    -- apenas desfaz o vínculo. A resposta continua fazendo sentido no fio.
    ADD COLUMN reply_to_id UUID REFERENCES concord_channel_messages(id) ON DELETE SET NULL,
    ADD COLUMN edited_at   TIMESTAMPTZ,
    ADD COLUMN deleted_at  TIMESTAMPTZ,
    ADD COLUMN pinned_at   TIMESTAMPTZ,
    ADD COLUMN pinned_by   UUID REFERENCES users(id) ON DELETE SET NULL;

-- O corpo passa a aceitar nulo: apagar zera o texto e mantém a linha, para não
-- abrir buraco na ordenação do canal nem quebrar as respostas que apontam para
-- ela. Mesma decisão das conversas diretas (Fase 3).
ALTER TABLE concord_channel_messages
    ALTER COLUMN body DROP NOT NULL;

ALTER TABLE concord_channel_messages
    DROP CONSTRAINT IF EXISTS concord_channel_messages_body_chk;

ALTER TABLE concord_channel_messages
    ADD CONSTRAINT concord_channel_messages_body_chk
        CHECK (body IS NULL OR char_length(trim(body)) BETWEEN 1 AND 4000),
    -- Apagada não conserva texto; não apagada precisa ter texto. Sem isto,
    -- um bug futuro poderia deixar mensagem vazia e visível, ou apagada com o
    -- conteúdo ainda no banco.
    ADD CONSTRAINT concord_channel_messages_deleted_chk
        CHECK ((deleted_at IS NULL) = (body IS NOT NULL)),
    ADD CONSTRAINT concord_channel_messages_pinned_chk
        CHECK ((pinned_at IS NULL) = (pinned_by IS NULL));

-- Uma mensagem não pode responder a si mesma.
ALTER TABLE concord_channel_messages
    ADD CONSTRAINT concord_channel_messages_self_reply_chk
        CHECK (reply_to_id IS NULL OR reply_to_id <> id);

CREATE INDEX concord_channel_messages_reply_idx
    ON concord_channel_messages (reply_to_id)
    WHERE reply_to_id IS NOT NULL;

-- Índice parcial: a lista de fixadas é aberta com frequência e são poucas
-- linhas entre milhares.
CREATE INDEX concord_channel_messages_pinned_idx
    ON concord_channel_messages (channel_id, pinned_at DESC)
    WHERE pinned_at IS NOT NULL;

-- ------------------------------------------------------------- menções ------
-- Persistida, e não deduzida do texto na hora de exibir.
--
-- Duas razões: responder "o que mencionou você" sem varrer todas as mensagens
-- de todos os canais com LIKE; e congelar quem foi mencionado no momento do
-- envio — se a pessoa trocar de nome depois, a menção antiga continua
-- apontando para ela.
CREATE TABLE concord_message_mentions (
    message_id UUID NOT NULL REFERENCES concord_channel_messages(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (message_id, user_id)
);

-- "Minhas menções", da mais recente para a mais antiga.
CREATE INDEX concord_message_mentions_user_idx
    ON concord_message_mentions (user_id, created_at DESC);

-- ------------------------------------------------------------- reações ------
CREATE TABLE concord_message_reactions (
    message_id UUID        NOT NULL REFERENCES concord_channel_messages(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Uma reação por pessoa por emoji. A chave primária é a regra: reagir duas
    -- vezes com o mesmo emoji é a mesma reação, não duas.
    PRIMARY KEY (message_id, user_id, emoji),

    -- Limite de tamanho, não de conteúdo. Um emoji pode ter vários pontos de
    -- código (tom de pele, ZWJ de família), então contar caracteres não serve;
    -- 32 bytes acomoda os compostos e barra alguém enviando um texto inteiro
    -- como "emoji".
    CONSTRAINT concord_message_reactions_emoji_chk
        CHECK (char_length(emoji) BETWEEN 1 AND 32)
);

CREATE INDEX concord_message_reactions_message_idx
    ON concord_message_reactions (message_id);

COMMENT ON TABLE concord_message_mentions IS
    'Quem foi mencionado em cada mensagem, congelado no envio.';
COMMENT ON TABLE concord_message_reactions IS
    'Reações por emoji. A PK impede reação duplicada da mesma pessoa.';
