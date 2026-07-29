-- =====================================================================
-- V8 — Tenancy da agenda: business_id em appointments e reservations.
--
-- POR QUE ISTO É UMA CORREÇÃO DE ISOLAMENTO, NÃO UMA FEATURE
--
-- O catálogo do Flow é fixo em código (FlowCatalogProvider): todos os negócios
-- compartilham o MESMO professional_id sintético. Como a checagem de horário livre
-- cruzava agendamentos apenas por (professional_id, date), um agendamento do negócio
-- A ocupava o horário do negócio B — e a checagem de conflito do booking varria a
-- tabela inteira, sem escopo. Com um único tenant em produção isso era latente;
-- bastava o segundo negócio para virar vazamento entre clientes.
--
-- business_id passa a fazer parte da identidade do agendamento, e as consultas de
-- disponibilidade e conflito são reescopadas para (business_id, professional_id, date).
--
-- BACKFILL SEGURO EM BANCO NÃO VAZIO: as linhas existentes são, por definição, do
-- piloto — é o único negócio que existiu até aqui. O valor vem do placeholder do
-- Flyway (mesma técnica de V2), então nenhum UUID literal fica versionado.
--
-- NÃO tenantiza professionals/services/availability de propósito: esses agregados não
-- alimentam o Flow hoje (o catálogo é fixo em código), então dar tenancy a eles agora
-- seria escrever migration para um caminho que ninguém percorre. Quando o catálogo
-- virar dado, entram na mesma disciplina.
-- =====================================================================

ALTER TABLE appointments ADD COLUMN business_id UUID;
ALTER TABLE reservations ADD COLUMN business_id UUID;

UPDATE appointments
   SET business_id = '${pilot_business_id}'
 WHERE business_id IS NULL;

UPDATE reservations
   SET business_id = '${pilot_business_id}'
 WHERE business_id IS NULL;

ALTER TABLE appointments ALTER COLUMN business_id SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN business_id SET NOT NULL;

-- Índices no formato exato das consultas reescopadas: disponibilidade e conflito
-- filtram por (business_id, professional_id, date).
CREATE INDEX idx_appointments_tenant_professional_date
    ON appointments (business_id, professional_id, date);

CREATE INDEX idx_reservations_tenant_professional_date
    ON reservations (business_id, professional_id, date);

-- Suporta a agenda do dono em /app: "os próximos agendamentos deste negócio".
CREATE INDEX idx_appointments_tenant_date
    ON appointments (business_id, date);
