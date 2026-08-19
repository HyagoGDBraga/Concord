-- =============================================================================
-- Concord — V4__privacy.sql
-- Consentimento com versionamento e supressão de e-mail.
-- =============================================================================

-- -------------------------------------------------------- user_consents ----
-- Registro de aceite dos termos e da política de privacidade.
--
-- Com versão, não apenas com data: "aceitou em 12/03" não prova nada se o texto
-- mudou em 15/03. O que sustenta a base legal é saber QUAL texto a pessoa leu.
--
-- Linhas são acrescentadas, nunca atualizadas. O histórico de aceites é parte da
-- prova; sobrescrever destruiria o registro do consentimento anterior.
CREATE TABLE user_consents (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document     TEXT        NOT NULL,
    version      TEXT        NOT NULL,
    accepted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- IP do aceite. É dado pessoal, mas é também o que dá valor probatório ao
    -- registro; expurgado pelo mesmo job de retenção do audit_log.
    ip_address   TEXT,

    CONSTRAINT user_consents_document_chk
        CHECK (document IN ('TERMS_OF_USE', 'PRIVACY_POLICY'))
);

CREATE INDEX user_consents_user_idx
    ON user_consents (user_id, document, accepted_at DESC);

-- --------------------------------------------------- email_suppressions ----
-- Endereços para os quais o sistema deixou de enviar.
--
-- Fecha a lacuna registrada na D-07: SMTP entrega a mensagem mas não devolve
-- status. Sem esta lista, um endereço inválido continuaria recebendo tentativas
-- indefinidamente — o que destrói a reputação do domínio e faz o provedor
-- transacional bloquear a conta.
--
-- Guarda o HASH do endereço, não o endereço. A lista precisa responder "este
-- e-mail está suprimido?", e um hash responde isso sem manter um cadastro de
-- endereços de gente que nem tem conta aqui.
CREATE TABLE email_suppressions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email_hash   TEXT        NOT NULL,
    reason       TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Detalhe técnico devolvido pelo provedor, sem dado pessoal.
    provider_code TEXT,

    CONSTRAINT email_suppressions_reason_chk
        CHECK (reason IN ('HARD_BOUNCE', 'SOFT_BOUNCE', 'COMPLAINT', 'MANUAL')),
    CONSTRAINT email_suppressions_hash_chk
        CHECK (char_length(email_hash) = 64)
);

CREATE UNIQUE INDEX email_suppressions_hash_key ON email_suppressions (email_hash);
CREATE INDEX email_suppressions_created_idx     ON email_suppressions (created_at);

COMMENT ON COLUMN email_suppressions.email_hash IS
    'SHA-256 do endereço normalizado. O endereço em si nunca é armazenado aqui.';
