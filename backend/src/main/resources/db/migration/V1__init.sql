-- =============================================================================
-- Concord — V1__init.sql
-- Núcleo de identidade, tokens de ação, auditoria, configurações e sessão.
--
-- Contatos, conversas e mensagens entram em V2 (Fase 3).
-- Chamadas entram em V3 (Fase 5).
--
-- Nenhuma extensão é necessária: PostgreSQL 13+ traz gen_random_uuid() nativo,
-- e a comparação case-insensitive é feita por índice funcional em lower(),
-- evitando o tipo citext (que exige stringtype=unspecified no driver JDBC para
-- funcionar de forma confiável com o Hibernate).
-- =============================================================================

-- ----------------------------------------------------------------- users ----
CREATE TABLE users (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username           TEXT        NOT NULL,
    email              TEXT,                 -- NULL após anonimização
    password_hash      TEXT        NOT NULL,
    display_name       TEXT        NOT NULL,
    avatar_url         TEXT,
    bio                TEXT,
    role               TEXT        NOT NULL DEFAULT 'USER',
    status             TEXT        NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at  TIMESTAMPTZ,
    failed_login_count INTEGER     NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    disabled_at        TIMESTAMPTZ,
    disabled_reason    TEXT,
    anonymized_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT users_role_chk
        CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT users_status_chk
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED', 'DELETED')),
    CONSTRAINT users_username_chk
        CHECK (username ~ '^[A-Za-z0-9_]{3,20}$'),
    CONSTRAINT users_display_name_chk
        CHECK (char_length(display_name) BETWEEN 1 AND 50),
    CONSTRAINT users_bio_chk
        CHECK (bio IS NULL OR char_length(bio) <= 200),

    -- Invariantes de LGPD garantidas pelo banco, não apenas pelo serviço:
    -- uma conta excluída não pode conservar e-mail nem ficar sem marca de
    -- anonimização, mesmo que um bug futuro esqueça de limpar os campos.
    CONSTRAINT users_deleted_chk
        CHECK (status <> 'DELETED'
               OR (email IS NULL AND anonymized_at IS NOT NULL)),
    -- Conta só fica ativa depois que o e-mail foi verificado.
    CONSTRAINT users_active_chk
        CHECK (status <> 'ACTIVE' OR email_verified_at IS NOT NULL)
);

-- Unicidade case-insensitive por índice funcional. As consultas usam
-- lower(username) = lower(:valor) e portanto aproveitam estes índices.
CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));
CREATE UNIQUE INDEX users_email_lower_key    ON users (lower(email))
    WHERE email IS NOT NULL;
CREATE INDEX users_status_idx     ON users (status);
CREATE INDEX users_created_at_idx ON users (created_at DESC);
-- Suporta o job que expurga contas não verificadas.
CREATE INDEX users_pending_idx    ON users (created_at)
    WHERE status = 'PENDING_VERIFICATION';

COMMENT ON COLUMN users.email IS
    'Anulado na anonimização. O índice único parcial permite várias contas excluídas.';
COMMENT ON COLUMN users.locked_until IS
    'Bloqueio TEMPORÁRIO e automático por falhas de login. Não é ação administrativa; a ação administrativa é status = DISABLED.';

-- --------------------------------------------------- user_action_tokens ----
-- Tokens de uso único enviados por e-mail para AÇÕES pontuais do usuário.
-- Deliberadamente NÃO é uma tabela genérica de tokens: a autenticação é por
-- sessão, e aqui não entram tokens de acesso, refresh tokens ou JWT.
-- A constraint de 'action' fecha o conjunto de usos permitidos.
CREATE TABLE user_action_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action      TEXT        NOT NULL,
    -- Somente o SHA-256 do token, em hexadecimal. O valor em texto puro existe
    -- apenas em memória, no instante do envio do e-mail, e nunca é persistido
    -- nem registrado em log.
    token_hash  TEXT        NOT NULL,
    -- Carga auxiliar da ação. Hoje: o novo endereço em EMAIL_CHANGE.
    -- Nunca contém segredo.
    payload     TEXT,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT user_action_tokens_action_chk
        CHECK (action IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE')),
    CONSTRAINT user_action_tokens_hash_chk
        CHECK (char_length(token_hash) = 64)
);

CREATE UNIQUE INDEX user_action_tokens_hash_key ON user_action_tokens (token_hash);
CREATE INDEX user_action_tokens_user_idx        ON user_action_tokens (user_id, action);
CREATE INDEX user_action_tokens_expires_idx     ON user_action_tokens (expires_at);

-- ------------------------------------------------------------ audit_log ----
-- Tabela única para eventos de segurança, administrativos e de privacidade.
--
-- Minimização aplicada: não há user_agent, não há identificador de sessão e o
-- metadata é restrito a valores não pessoais (motivos, contadores, códigos de
-- erro). Conteúdo de mensagem, senha, token e e-mail em texto claro nunca
-- entram aqui.
--
-- Retenção (job diário AuditRetentionJob):
--   IP de qualquer categoria .......... anulado após 6 meses
--   category = SECURITY ............... linha removida após 6 meses
--   category = ADMIN .................. linha removida após 24 meses
--   category = PRIVACY ................ linha removida após 60 meses
-- A categoria PRIVACY vive mais porque é a prova de que um direito do titular
-- foi atendido; ela já nasce sem IP.
CREATE TABLE audit_log (
    id             BIGSERIAL   PRIMARY KEY,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    category       TEXT        NOT NULL,
    action         TEXT        NOT NULL,
    outcome        TEXT        NOT NULL,
    -- Nullable: eventos anônimos (tentativa de login em conta inexistente)
    -- não têm ator identificado.
    actor_user_id  UUID        REFERENCES users(id) ON DELETE SET NULL,
    -- Rótulo do ator no momento do evento. Na anonimização da conta ele é
    -- substituído pelo pseudônimo, enquanto actor_user_id é preservado — o que
    -- mantém a correlação entre eventos sem manter o identificador pessoal.
    actor_label    TEXT,
    target_user_id UUID        REFERENCES users(id) ON DELETE SET NULL,
    -- TEXT em vez de INET: o tipo inet exigiria um tipo Hibernate customizado
    -- e nenhuma consulta do Concord usa operadores de rede.
    ip_address     TEXT,
    metadata       JSONB       NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT audit_log_category_chk
        CHECK (category IN ('SECURITY', 'ADMIN', 'PRIVACY')),
    CONSTRAINT audit_log_outcome_chk
        CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX audit_log_created_idx  ON audit_log (created_at DESC);
CREATE INDEX audit_log_actor_idx    ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX audit_log_target_idx   ON audit_log (target_user_id, created_at DESC);
CREATE INDEX audit_log_action_idx   ON audit_log (action, created_at DESC);
CREATE INDEX audit_log_category_idx ON audit_log (category, created_at DESC);

-- --------------------------------------------------------- app_settings ----
-- Configuração alterável em tempo de execução, sem redeploy. Só entram aqui
-- chaves que precisam mudar com o sistema no ar; o resto continua em variável
-- de ambiente.
CREATE TABLE app_settings (
    key        TEXT        PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID        REFERENCES users(id) ON DELETE SET NULL
);

-- 'admin.bootstrap.completed' torna o bootstrap do primeiro administrador um
-- estado PERSISTENTE. Depois de concluído, alterar a variável de ambiente não
-- promove mais ninguém.
INSERT INTO app_settings (key, value) VALUES ('admin.bootstrap.completed', 'false');

-- ------------------------------------------------------- spring session ----
-- Schema oficial do Spring Session JDBC para PostgreSQL, versionado aqui
-- (spring.session.jdbc.initialize-schema = never).
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INTEGER  NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
-- Índice que torna barato "encerrar todas as sessões de um usuário".
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK
        PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK
        FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID)
        ON DELETE CASCADE
);
