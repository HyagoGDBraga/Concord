-- =============================================================================
-- Concord — V3__calls.sql
-- Registro de chamadas de voz e vídeo.
--
-- O que esta tabela NÃO guarda, e por quê:
--   * SDP — descreve codecs, IPs internos, portas e capacidades do dispositivo.
--     É impressão digital de máquina, e perde qualquer utilidade assim que a
--     negociação termina.
--   * Candidatos ICE — expõem IP local, IP público e topologia de rede.
--   * Mídia — o servidor não a processa nem a armazena; ela vai direto entre os
--     pares (D-06, P2P), ou pelo TURN, que só encaminha bytes cifrados.
--
-- O que guarda é o registro de chamada que o próprio usuário vê no histórico:
-- quem, quando, que tipo, quanto durou. Isso é dado de produto, visível aos
-- dois participantes — e a nenhum administrador.
-- =============================================================================

CREATE TABLE calls (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    -- RESTRICT pelo mesmo motivo de messages: a exclusão de conta anonimiza a
    -- linha de users, nunca a remove.
    caller_id       UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    callee_id       UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    type            TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'RINGING',
    end_reason      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at     TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,

    CONSTRAINT calls_type_chk
        CHECK (type IN ('AUDIO', 'VIDEO')),
    CONSTRAINT calls_status_chk
        CHECK (status IN ('RINGING', 'ACTIVE', 'ENDED')),
    CONSTRAINT calls_end_reason_chk
        CHECK (end_reason IS NULL OR end_reason IN
               ('HANGUP', 'REJECTED', 'MISSED', 'BUSY', 'FAILED', 'CANCELLED')),
    CONSTRAINT calls_not_self_chk
        CHECK (caller_id <> callee_id),
    -- Encerrada obrigatoriamente com instante e motivo: sem isso, uma chamada
    -- ficaria "no ar" para sempre depois de um bug.
    CONSTRAINT calls_ended_chk
        CHECK (status <> 'ENDED' OR (ended_at IS NOT NULL AND end_reason IS NOT NULL)),
    CONSTRAINT calls_active_chk
        CHECK (status <> 'ACTIVE' OR answered_at IS NOT NULL)
);

CREATE INDEX calls_conversation_idx ON calls (conversation_id, created_at DESC);
CREATE INDEX calls_caller_idx       ON calls (caller_id, created_at DESC);
CREATE INDEX calls_callee_idx       ON calls (callee_id, created_at DESC);

-- Índice parcial que sustenta duas perguntas frequentes e baratas: "esta pessoa
-- já está em chamada?" e "que chamadas ficaram penduradas?".
CREATE INDEX calls_open_idx ON calls (status, created_at)
    WHERE status IN ('RINGING', 'ACTIVE');

COMMENT ON TABLE calls IS
    'Metadado de chamada, visível aos dois participantes. Nenhum endpoint sob /api/admin lê esta tabela. Sem SDP, sem ICE, sem mídia.';
COMMENT ON COLUMN calls.end_reason IS
    'CANCELLED = quem ligou desistiu antes de atender; MISSED = ninguém atendeu no prazo.';
