-- =====================================================================
-- V4 — Sessões de WhatsApp Flow.
--
-- Tabela de INTEGRAÇÃO (não é entidade de negócio). Amarra o flow_token opaco ao
-- cliente e ao tenant para quem o Flow foi enviado: o payload de data_exchange não
-- carrega telefone nem businessId, e aceitá-los do cliente permitiria agendar em nome
-- de terceiros ou atravessar tenants.
--
-- A mesma linha é o recibo de idempotência do CONFIRM: flow_token é a chave primária,
-- então um token só produz um agendamento mesmo sob reentregas concorrentes da Meta.
-- Por isso a sessão concluída NÃO é apagada — sem ela não há como reconhecer a
-- repetição. O agendamento em si vive em reservations/appointments.
--
-- status: ABERTA | CONCLUIDA | EXPIRADA | INVALIDADA (string, não ordinal, para que
-- inserir um estado novo no enum não reinterprete linhas antigas). O vencimento também
-- é avaliado na leitura via expira_em: nenhuma sessão depende de rotina de limpeza para
-- deixar de valer.
--
-- Não guarda payload decifrado nem material criptográfico.
-- =====================================================================

CREATE TABLE whatsapp_flow_sessions (
    flow_token          VARCHAR(100) NOT NULL,
    telefone            VARCHAR(30)  NOT NULL,
    business_id         UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    expira_em           TIMESTAMP    NOT NULL,
    confirmado_servico  VARCHAR(120),
    confirmado_data     VARCHAR(10),
    confirmado_horario  VARCHAR(5),
    criado_em           TIMESTAMP    NOT NULL,
    atualizado_em       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_whatsapp_flow_sessions PRIMARY KEY (flow_token)
);

CREATE INDEX idx_whatsapp_flow_sessions_tenant_telefone
    ON whatsapp_flow_sessions (business_id, telefone);

-- Suporta a limpeza futura das sessões vencidas sem varrer a tabela inteira.
CREATE INDEX idx_whatsapp_flow_sessions_expira_em
    ON whatsapp_flow_sessions (expira_em);
