-- =============================================================================
-- Concord — V2__chat.sql
-- Contatos, bloqueio, conversas e mensagens.
--
-- Chamadas de voz/video entram em V3 (Fase 5). O WebSocket da Fase 4 nao
-- acrescenta tabela: ele transporta o que ja esta modelado aqui.
-- =============================================================================

-- -------------------------------------------------------------- contacts ----
-- Relacao de contato entre duas pessoas. UMA linha por par, nao duas: um
-- contato aceito e simetrico por natureza, e duplicar a linha criaria a
-- possibilidade de os dois lados divergirem.
--
-- Bloqueio NAO mora aqui (ver tabela 'blocks'): bloquear e uma acao
-- unidirecional e nao deve destruir o registro de que as duas pessoas eram
-- contatos.
CREATE TABLE contacts (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       TEXT        NOT NULL DEFAULT 'PENDING',
    -- Chave canonica do par, sempre com o menor UUID primeiro. E o que impede
    -- que A->B e B->A coexistam como dois pedidos abertos.
    pair_key     TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,

    CONSTRAINT contacts_status_chk
        CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT contacts_not_self_chk
        CHECK (requester_id <> addressee_id),
    CONSTRAINT contacts_accepted_chk
        CHECK (status <> 'ACCEPTED' OR responded_at IS NOT NULL)
);

CREATE UNIQUE INDEX contacts_pair_key      ON contacts (pair_key);
CREATE INDEX contacts_requester_idx        ON contacts (requester_id, status);
CREATE INDEX contacts_addressee_idx        ON contacts (addressee_id, status);

COMMENT ON COLUMN contacts.pair_key IS
    'least(requester,addressee) || '':'' || greatest(...). Calculado na aplicacao.';

-- ---------------------------------------------------------------- blocks ----
-- Bloqueio unidirecional. Se A bloqueia B, nenhum dos dois consegue enviar
-- mensagem ao outro — o efeito no envio e reciproco de proposito, senao o
-- bloqueio nao protegeria quem o acionou.
CREATE TABLE blocks (
    blocker_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT blocks_not_self_chk CHECK (blocker_id <> blocked_id)
);

CREATE INDEX blocks_blocked_idx ON blocks (blocked_id);

-- --------------------------------------------------------- conversations ----
CREATE TABLE conversations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    type            TEXT        NOT NULL DEFAULT 'DIRECT',
    -- Mesma ideia do pair_key: garante uma unica conversa direta por par, sem
    -- depender de trava na aplicacao. NULL quando o tipo nao for DIRECT, o que
    -- deixa a coluna pronta para grupos sem exigir migration destrutiva.
    direct_key      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Desnormalizacao consciente: ordenar a lista de conversas por "mensagem
    -- mais recente" sem esta coluna exigiria um LATERAL JOIN em messages a cada
    -- carregamento da tela inicial.
    last_message_at TIMESTAMPTZ,

    CONSTRAINT conversations_type_chk
        CHECK (type IN ('DIRECT')),
    CONSTRAINT conversations_direct_key_chk
        CHECK (type <> 'DIRECT' OR direct_key IS NOT NULL)
);

CREATE UNIQUE INDEX conversations_direct_key ON conversations (direct_key)
    WHERE direct_key IS NOT NULL;
CREATE INDEX conversations_last_message_idx ON conversations (last_message_at DESC NULLS LAST);

-- ---------------------------------------------- conversation_participants ----
CREATE TABLE conversation_participants (
    conversation_id      UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id              UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Marcador de leitura. Guardado por participante, nunca por mensagem: uma
    -- tabela message_reads cresceria como o produto das mensagens pelos
    -- participantes, para responder a mesma pergunta.
    last_read_at         TIMESTAMPTZ,
    last_read_message_id UUID,

    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX conversation_participants_user_idx
    ON conversation_participants (user_id);

-- -------------------------------------------------------------- messages ----
CREATE TABLE messages (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    -- ON DELETE RESTRICT e proposital: a exclusao de conta anonimiza a linha de
    -- users, nunca a remove (D-05). Se algum codigo futuro tentar um DELETE
    -- real, o banco recusa em vez de destruir o historico do interlocutor.
    sender_id         UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    body              TEXT,
    -- Identificador gerado pelo cliente. Torna o envio idempotente: um retry
    -- por conexao instavel nao duplica a mensagem. Passa a valer ainda mais na
    -- Fase 4, quando o mesmo envio pode chegar por HTTP e por WebSocket.
    client_message_id UUID        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at         TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ,

    CONSTRAINT messages_body_chk
        CHECK (body IS NULL OR char_length(body) BETWEEN 1 AND 4000),
    -- Mensagem apagada nao conserva o texto. A linha permanece para nao abrir
    -- buraco na ordenacao da conversa do interlocutor.
    CONSTRAINT messages_deleted_chk
        CHECK (deleted_at IS NULL OR body IS NULL),
    CONSTRAINT messages_present_chk
        CHECK (deleted_at IS NOT NULL OR body IS NOT NULL)
);

-- Indice que sustenta a paginacao por keyset (created_at, id) DESC.
CREATE INDEX messages_conversation_idx
    ON messages (conversation_id, created_at DESC, id DESC);
CREATE UNIQUE INDEX messages_client_id_key
    ON messages (conversation_id, client_message_id);
CREATE INDEX messages_sender_idx ON messages (sender_id);

COMMENT ON TABLE messages IS
    'Conteudo privado. Nenhum endpoint sob /api/admin le esta tabela.';
