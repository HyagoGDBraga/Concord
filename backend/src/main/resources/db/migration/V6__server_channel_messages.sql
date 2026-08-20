-- Mensagens dos canais. Mantém o histórico de comunidades separado das
-- conversas diretas, que têm regras de privacidade e participantes diferentes.

CREATE TABLE concord_channel_messages (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id         UUID NOT NULL REFERENCES concord_channels(id) ON DELETE CASCADE,
    sender_id          UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    body               TEXT NOT NULL,
    client_message_id  UUID NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT concord_channel_messages_body_chk
        CHECK (char_length(trim(body)) BETWEEN 1 AND 4000),
    CONSTRAINT concord_channel_messages_client_key
        UNIQUE (channel_id, client_message_id)
);

CREATE INDEX concord_channel_messages_history_idx
    ON concord_channel_messages (channel_id, created_at ASC, id ASC);