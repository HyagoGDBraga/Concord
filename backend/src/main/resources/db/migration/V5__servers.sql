-- Comunidades persistentes: o núcleo de servidores e canais do Concord.

CREATE TABLE concord_servers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    owner_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT concord_servers_name_chk
        CHECK (char_length(trim(name)) BETWEEN 2 AND 80)
);

CREATE INDEX concord_servers_owner_idx ON concord_servers (owner_id, created_at);

CREATE TABLE concord_server_members (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id  UUID NOT NULL REFERENCES concord_servers(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       TEXT NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT concord_server_members_role_chk
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT concord_server_members_unique
        UNIQUE (server_id, user_id)
);

CREATE INDEX concord_server_members_user_idx
    ON concord_server_members (user_id, created_at);

CREATE TABLE concord_channels (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id  UUID NOT NULL REFERENCES concord_servers(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    type       TEXT NOT NULL DEFAULT 'TEXT',
    position   INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT concord_channels_type_chk
        CHECK (type IN ('TEXT', 'VOICE')),
    CONSTRAINT concord_channels_name_chk
        CHECK (char_length(trim(name)) BETWEEN 1 AND 80),
    CONSTRAINT concord_channels_unique_name
        UNIQUE (server_id, name)
);

CREATE INDEX concord_channels_server_idx
    ON concord_channels (server_id, position, created_at);