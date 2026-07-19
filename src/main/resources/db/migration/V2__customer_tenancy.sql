-- =====================================================================
-- V2 — Tenancy do Customer (vertical slice 1).
--
-- Adiciona business_id e phone_e164 ao customers, faz backfill seguro dos
-- dados legados do negócio piloto, DETECTA telefones inválidos e duplicatas
-- ANTES de aplicar as constraints (falhando com diagnóstico claro, sem
-- descartar dados), e então torna as colunas NOT NULL e aplica a unicidade
-- lógica (business_id, phone_e164) + índice de tenant.
--
-- O BusinessId do piloto vem do placeholder ${pilot_business_id} do Flyway,
-- alimentado por spring.flyway.placeholders.pilot_business_id, que por sua vez
-- vem da propriedade tipada troquim.tenant.pilot-business-id. FONTE ÚNICA —
-- sem UUID literal aqui.
-- =====================================================================

-- 1. Colunas nullable primeiro (permite backfill dos dados existentes).
ALTER TABLE customers ADD COLUMN business_id UUID;
ALTER TABLE customers ADD COLUMN phone_e164  VARCHAR(20);

-- 2. Backfill do tenant piloto para todos os clientes existentes.
UPDATE customers
   SET business_id = '${pilot_business_id}'
 WHERE business_id IS NULL;

-- 3. Backfill do telefone canônico E.164: '+' seguido apenas dos dígitos.
--    (remove '+' pré-existente e separadores antes de reprefixar; sem duplo '+').
UPDATE customers
   SET phone_e164 = '+' || regexp_replace(phone, '\D', '', 'g')
 WHERE phone_e164 IS NULL;

-- 4. Validação ANTES das constraints: telefones inválidos e duplicatas.
--    Falha a migration transacionalmente com diagnóstico — nada é descartado.
DO $$
DECLARE
    invalid_count INTEGER;
    dup_count     INTEGER;
BEGIN
    SELECT count(*) INTO invalid_count
      FROM customers
     WHERE phone_e164 IS NULL
        OR phone_e164 !~ '^\+[1-9][0-9]{7,14}$';
    IF invalid_count > 0 THEN
        RAISE EXCEPTION
          'V2 abortada: % cliente(s) com telefone invalido para E.164 (esperado +<DDI><DDD><numero>). Corrija os telefones e reaplique. Nenhum dado foi descartado.',
          invalid_count;
    END IF;

    SELECT count(*) INTO dup_count
      FROM (
            SELECT business_id, phone_e164
              FROM customers
             GROUP BY business_id, phone_e164
            HAVING count(*) > 1
           ) d;
    IF dup_count > 0 THEN
        RAISE EXCEPTION
          'V2 abortada: % grupo(s) de clientes duplicados por (business_id, phone_e164). Consolide os duplicados manualmente e reaplique. Nenhum dado foi descartado.',
          dup_count;
    END IF;
END $$;

-- 5. Agora as colunas podem ser NOT NULL.
ALTER TABLE customers ALTER COLUMN business_id SET NOT NULL;
ALTER TABLE customers ALTER COLUMN phone_e164  SET NOT NULL;

-- 6. Unicidade lógica por tenant + índice para consultas por tenant.
ALTER TABLE customers
  ADD CONSTRAINT uq_customers_business_phone UNIQUE (business_id, phone_e164);

CREATE INDEX idx_customers_business_id ON customers (business_id);
