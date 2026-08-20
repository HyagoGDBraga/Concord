-- Convites de servidor: token de uso único, expirável e armazenado somente
-- como hash. O convite é direcionado a um usuário já existente.

CREATE TABLE concord_server_invites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id       UUID NOT NULL REFERENCES concord_servers(id) ON DELETE CASCADE,
    inviter_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    accepted_at     TIMESTAMPTZ,
    declined_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT concord_server_invites_hash_chk CHECK (char_length(token_hash) = 64),
    CONSTRAINT concord_server_invites_target_chk CHECK (inviter_id <> invitee_id),
    CONSTRAINT concord_server_invites_terminal_chk
        CHECK (accepted_at IS NULL OR declined_at IS NULL)
);

CREATE UNIQUE INDEX concord_server_invites_hash_key
    ON concord_server_invites (token_hash);
CREATE INDEX concord_server_invites_pending_idx
    ON concord_server_invites (invitee_id, created_at DESC)
    WHERE accepted_at IS NULL AND declined_at IS NULL;