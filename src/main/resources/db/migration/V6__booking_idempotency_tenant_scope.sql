-- =====================================================================
-- V6 — Escopa a regra do MVP ("uma base conclui no máximo um agendamento") por TENANT.
--
-- A V5 introduziu command_base + o índice parcial uq_booking_idempotency_base_confirmada
-- sobre command_base SOZINHO. A idempotência de comando (command_key = base:fingerprint)
-- já é tenant-safe, pois o business_id entra no fingerprint. Mas a regra do MVP incidia
-- sobre a base isolada — e a base (flow_token) não carrega tenant. Dois negócios com a
-- mesma base (cenário adversário) teriam a regra de um bloqueando o outro: vazamento
-- entre tenants. Provado por BookingIdempotencyMultiTenantPostgresTest.
--
-- Correção mínima: adicionar business_id e re-escopar a regra para (business_id,
-- command_base). NÃO altera a V5 (que pode ter sido aplicada em banco persistente):
-- migration nova, checksum da V5 intacto. NÃO altera a V4.
--
-- SEGURO EM BANCO NÃO VAZIO: o business_id NÃO é derivado de hash irreversível nem
-- recebe tenant fictício. É preenchido pela RELAÇÃO CANÔNICA:
--   booking_idempotency.appointment_id -> appointments.customer_id -> customers.business_id.
-- Linhas sem appointment (ex.: outcome INDISPONIVEL de conflito, que persiste com
-- appointment_id NULL) NÃO têm relação canônica e permanecem com business_id NULL —
-- elas nunca são consultadas pela regra da base (que é CONFIRMADO-only) nem entram no
-- índice parcial. Por isso a coluna fica NULLABLE no banco; a invariante real é imposta
-- só onde importa, pela CHECK abaixo (CONFIRMADO exige tenant).
--
-- BLOQUEIO EXPLÍCITO (padrão da V2): se um registro CONFIRMADO não puder ser preenchido
-- (appointment ausente/dangling), a criação da CHECK falha e a migration ABORTA. Pare,
-- investigue e reaplique — nenhum dado é apagado ou inventado.
-- =====================================================================

ALTER TABLE booking_idempotency ADD COLUMN business_id UUID;

-- Backfill pela relação canônica (idempotente: só toca linhas ainda NULL).
UPDATE booking_idempotency bi
   SET business_id = c.business_id
  FROM appointments a
  JOIN customers c ON c.id = a.customer_id
 WHERE bi.appointment_id = a.id
   AND bi.business_id IS NULL;

-- Aborta explicitamente se algum CONFIRMADO ficou sem tenant (relação canônica quebrada).
DO $$
DECLARE
    orfaos INTEGER;
BEGIN
    SELECT count(*) INTO orfaos
      FROM booking_idempotency
     WHERE outcome_status = 'CONFIRMADO'
       AND business_id IS NULL;
    IF orfaos > 0 THEN
        RAISE EXCEPTION
          'V6 abortada: % registro(s) CONFIRMADO em booking_idempotency sem business_id derivável (appointment ausente). Investigue a relação com appointments/customers e reaplique. Nenhum dado foi descartado.',
          orfaos;
    END IF;
END $$;

-- Invariante REAL, imposta no banco: todo CONFIRMADO tem tenant. Não-confirmados podem
-- permanecer NULL (nunca consultados pela regra).
ALTER TABLE booking_idempotency
    ADD CONSTRAINT chk_booking_idem_confirmada_tenant
    CHECK (outcome_status IS DISTINCT FROM 'CONFIRMADO' OR business_id IS NOT NULL);

-- Re-escopa a regra do MVP por tenant.
DROP INDEX uq_booking_idempotency_base_confirmada;

CREATE UNIQUE INDEX uq_booking_idempotency_tenant_base_confirmada
    ON booking_idempotency (business_id, command_base)
    WHERE outcome_status = 'CONFIRMADO';
