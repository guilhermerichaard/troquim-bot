-- =====================================================================
-- V15 — Memoria confirmada de interpretacao de servicos.
--
-- Guarda SOMENTE a forma normalizada confirmada e o ServiceId canonico, por tenant.
-- Nao guarda mensagem completa, telefone, nome de cliente ou qualquer PII.
--
-- Esta tabela nao cria regra de negocio: o uso da memoria ainda precisa validar o
-- ServiceId contra o catalogo ofertavel atual antes de permitir agendamento.
-- =====================================================================

CREATE TABLE service_interpretation_aliases (
    id               UUID         NOT NULL,
    business_id      UUID         NOT NULL,
    input_normalized VARCHAR(160) NOT NULL,
    service_id       UUID         NOT NULL,
    confirmations    INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,

    CONSTRAINT pk_service_interpretation_aliases PRIMARY KEY (id),
    CONSTRAINT uq_service_interpretation_alias_input
        UNIQUE (business_id, input_normalized),
    CONSTRAINT ck_service_interpretation_confirmations
        CHECK (confirmations >= 0),
    CONSTRAINT fk_service_interpretation_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),
    CONSTRAINT fk_service_interpretation_service
        FOREIGN KEY (business_id, service_id)
        REFERENCES services(business_id, id)
);

CREATE INDEX idx_service_interpretation_service
    ON service_interpretation_aliases (business_id, service_id);
