-- =====================================================================
-- V3 — Idempotência durável de eventos externos de mensageria.
--
-- Tabela de INTEGRAÇÃO (não é entidade de negócio). Registra, por provedor, os
-- ids de mensagens externas já processadas, garantindo processamento único e
-- serialização de entregas concorrentes via UNIQUE(provider, external_message_id).
--
-- Usada pela fundação da integração WhatsApp Cloud API. Não guarda o payload
-- bruto nem dado pessoal — apenas o id externo (opaco) e o estado de processamento.
-- =====================================================================

-- status: PENDING = negócio processado e resposta persistida, outbound ainda não
-- confirmado; SENT = resposta entregue. response_text preserva a resposta para permitir
-- reenvio (retry) do outbound numa re-entrega, SEM reprocessar a conversa.
CREATE TABLE inbound_message_receipts (
    id                   UUID         NOT NULL,
    provider             VARCHAR(40)  NOT NULL,
    external_message_id  VARCHAR(255) NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    response_text        TEXT,
    outbound_message_id  VARCHAR(255),
    criado_em            TIMESTAMP    NOT NULL,
    atualizado_em        TIMESTAMP    NOT NULL,
    CONSTRAINT pk_inbound_message_receipts PRIMARY KEY (id),
    CONSTRAINT uq_inbound_receipt_provider_external_id UNIQUE (provider, external_message_id)
);
