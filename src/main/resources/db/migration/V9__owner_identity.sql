-- =====================================================================
-- V9 — Identidade do dono para a área privada /app.
--
-- owner_users: um dono pertence a EXATAMENTE um negócio (business_id NOT NULL, sem
-- default de piloto — diferente de V2/V8, esta tabela nasce vazia, não há dono legado
-- para herdar o negócio do piloto por backfill).
--
-- owner_sessions: token_hash é a chave primária — o valor em claro do token NUNCA é
-- persistido, só o SHA-256. Um vazamento do banco não entrega sessão utilizável.
-- business_id é DENORMALIZADO da owner (evita um JOIN em toda requisição autenticada)
-- e é ele — não um tenant fixo — que resolve o negócio de qualquer rota do /app.
-- =====================================================================

CREATE TABLE owner_users (
    id            UUID         NOT NULL,
    business_id   UUID         NOT NULL,
    email         VARCHAR(255) NOT NULL,
    senha_hash    VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    criado_em     TIMESTAMP    NOT NULL,
    atualizado_em TIMESTAMP    NOT NULL,
    CONSTRAINT pk_owner_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_owner_users_email ON owner_users (email);
CREATE INDEX idx_owner_users_business_id ON owner_users (business_id);

CREATE TABLE owner_sessions (
    token_hash  VARCHAR(64) NOT NULL,
    owner_id    UUID        NOT NULL,
    business_id UUID        NOT NULL,
    criada_em   TIMESTAMP   NOT NULL,
    expira_em   TIMESTAMP   NOT NULL,
    CONSTRAINT pk_owner_sessions PRIMARY KEY (token_hash)
);

CREATE INDEX idx_owner_sessions_owner_id ON owner_sessions (owner_id);
CREATE INDEX idx_owner_sessions_expira_em ON owner_sessions (expira_em);
