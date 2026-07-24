-- =====================================================================
-- V5 — Idempotência por COMANDO de confirmação de agendamento.
--
-- Fecha a janela em que o Appointment já estava commitado mas o recibo da confirmação
-- não: antes, o retry só não duplicava porque um laço varria os agendamentos do cliente
-- procurando o mesmo slot — restrição de unicidade de domínio, não idempotência.
--
-- Esta linha é escrita na MESMA transação de Customer/Reservation/Appointment. Commit:
-- agendamento e recibo aparecem juntos. Rollback: somem juntos, e o retry pode reivindicar
-- do zero. É isso que torna a proteção independente de o cliente ter (ou não) outros
-- agendamentos.
--
-- command_key = <base>:<sha256(payload canônico)>, onde a base é o flow_token da sessão
-- (ou o id da mensagem inbound). A base sozinha identificaria a SESSÃO, não o comando —
-- e "mesmo token com dados diferentes" devolveria silenciosamente o agendamento anterior.
-- Com o fingerprint embutido, dados diferentes são um comando diferente.
--
-- request_fingerprint é guardado à parte para CONFERÊNCIA: um acerto de chave com
-- fingerprint divergente falha alto, em vez de devolver o resultado de outro comando.
--
-- appointment_id fica nulo entre a reivindicação e a conclusão — estado visível apenas
-- dentro da transação que reivindicou.
--
-- Não guarda payload do cliente nem dado pessoal além do primeiro nome já exibido na
-- tela de sucesso.
-- =====================================================================

CREATE TABLE booking_idempotency (
    command_key         VARCHAR(160) NOT NULL,
    command_base        VARCHAR(80)  NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,
    appointment_id      UUID,
    outcome_status      VARCHAR(20),
    outcome_servico     VARCHAR(120),
    outcome_data        VARCHAR(10),
    outcome_horario     VARCHAR(5),
    outcome_nome        VARCHAR(120),
    created_at          TIMESTAMP    NOT NULL,
    completed_at        TIMESTAMP,
    CONSTRAINT pk_booking_idempotency PRIMARY KEY (command_key)
);

-- =====================================================================
-- REGRA DO MVP — um flow_token conclui no máximo UM agendamento.
--
-- command_base é o flow_token isolado. O índice parcial garante, no BANCO e dentro da
-- mesma transação do agendamento, que só existe uma linha CONFIRMADO por base.
--
-- Por que não confiar só na FlowSession: ela é atualizada em transação SEPARADA, depois
-- do commit do agendamento. Se aquela escrita falhasse, a sessão diria "não confirmada" e
-- o mesmo token poderia concluir um segundo agendamento. Aqui a regra vive junto com o
-- dado que ela protege.
--
-- Parcial em outcome_status = 'CONFIRMADO': um comando que terminou em conflito de agenda
-- NÃO consome o token — o cliente ainda não agendou nada, e obrigá-lo a reabrir o Flow
-- seria punição por um horário que outra pessoa pegou.
--
-- O caso sequencial é detectado por SELECT (resposta limpa: "peça a agenda novamente");
-- este índice é a rede para a corrida, em que dois comandos distintos da mesma base
-- passam pelo SELECT ao mesmo tempo. A perdedora aborta e sobe como falha técnica, sem
-- tentar se recuperar dentro de uma transação já invalidada.
-- =====================================================================

CREATE UNIQUE INDEX uq_booking_idempotency_base_confirmada
    ON booking_idempotency (command_base)
    WHERE outcome_status = 'CONFIRMADO';

-- Permite localizar o recibo a partir do agendamento (suporte/diagnóstico) e apoiar um
-- expurgo futuro por idade.
CREATE INDEX idx_booking_idempotency_appointment
    ON booking_idempotency (appointment_id);

CREATE INDEX idx_booking_idempotency_created_at
    ON booking_idempotency (created_at);

-- =====================================================================
-- INVARIANTE DE SLOT — agora garantida pelo BANCO.
--
-- A checagem de conflito no caso de uso é um SELECT seguido de INSERT. Sob READ
-- COMMITTED, duas transações simultâneas leem "sem conflito" e ambas gravam: dois
-- clientes diferentes saem com o MESMO horário. Um teste de concorrência contra
-- PostgreSQL real reproduziu exatamente isso.
--
-- O índice parcial fecha a corrida: o segundo INSERT viola a unicidade e sua transação
-- é abortada. A perdedora NÃO tenta se recuperar dentro da transação já invalidada —
-- a violação sobe como falha técnica e o cliente repete com segurança (a idempotência
-- por comando impede duplicação no retry).
--
-- Parcial (WHERE status <> 'CANCELADO') porque um horário cancelado precisa poder ser
-- reagendado. Isto é invariante de DOMÍNIO, não idempotência — os dois mecanismos
-- coexistem e protegem coisas diferentes.
--
-- ATENÇÃO NA APLICAÇÃO: se a base já contiver agendamentos ativos duplicados no mesmo
-- slot, a criação do índice FALHA e a migration não sobe. Verificar antes com:
--   SELECT professional_id, date, start_time, COUNT(*)
--     FROM appointments WHERE status <> 'CANCELADO'
--    GROUP BY 1,2,3 HAVING COUNT(*) > 1;
-- =====================================================================

CREATE UNIQUE INDEX uq_appointments_slot_ativo
    ON appointments (professional_id, date, start_time)
    WHERE status <> 'CANCELADO';
