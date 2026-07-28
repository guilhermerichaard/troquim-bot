-- =====================================================================
-- V7 — Conexão de canal WhatsApp por tenant (Embedded Signup).
--
-- Tabela de INTEGRAÇÃO (não é entidade de negócio). Guarda o vínculo entre um
-- Business e a conta WhatsApp Business que ele conectou pela Meta, além da
-- credencial obtida na troca do OAuth code.
--
-- business_id é ÚNICO: um tenant tem no máximo uma conexão de canal. Reconectar
-- reaproveita a mesma linha (volta a PENDENTE), então não existe histórico de
-- credenciais antigas acumulando no banco.
--
-- state_token é o nonce anti-CSRF/replay do Embedded Signup: gerado no início,
-- consumido UMA vez na finalização. Único para que dois inícios simultâneos não
-- colidam, e anulado assim que usado — um code só pode ser trocado uma vez.
--
-- A credencial NUNCA é gravada em claro: credencial_cifrada é AES-256-GCM em
-- base64, com IV próprio por escrita e key_version para permitir rotação de chave
-- sem reescrever linhas antigas. A chave em si vive em variável de ambiente, nunca
-- no banco nem no repositório.
--
-- status: PENDENTE | CONECTADO | FALHOU (string, não ordinal, para que inserir um
-- estado novo no enum não reinterprete linhas antigas).
--
-- falha_motivo guarda um código curto e estável (ex.: TROCA_DE_TOKEN_RECUSADA),
-- nunca a resposta da Meta: mensagens de erro de OAuth podem ecoar parâmetros.
-- =====================================================================

CREATE TABLE whatsapp_channel_connections (
    id                  UUID         NOT NULL,
    business_id         UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    state_token         VARCHAR(120),
    state_expira_em     TIMESTAMP,
    waba_id             VARCHAR(60),
    phone_number_id     VARCHAR(60),
    credencial_cifrada  TEXT,
    credencial_iv       VARCHAR(64),
    key_version         INTEGER,
    falha_motivo        VARCHAR(120),
    criado_em           TIMESTAMP    NOT NULL,
    atualizado_em       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_whatsapp_channel_connections PRIMARY KEY (id)
);

-- Um tenant, no máximo uma conexão. É esta restrição que impede duas conexões
-- concorrentes disputarem o mesmo Business.
CREATE UNIQUE INDEX uq_whatsapp_channel_connections_business
    ON whatsapp_channel_connections (business_id);

-- O nonce é único enquanto existe; após o consumo vira NULL (vários NULL são
-- permitidos num índice único, tanto no PostgreSQL quanto no H2).
CREATE UNIQUE INDEX uq_whatsapp_channel_connections_state
    ON whatsapp_channel_connections (state_token);
