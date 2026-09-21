-- =====================================================================
-- V16 — Reconcilia status legado de Appointment com recibo canonico de booking.
--
-- Versoes anteriores concluíam o booking (recibo outcome_status=CONFIRMADO) mas deixavam
-- o aggregate Appointment em PENDENTE. Isso produzia a UX contraditoria "confirmado" /
-- "aguardando confirmacao".
--
-- SEGURANCA: somente promove linhas que possuem evidencia transacional explicita:
-- booking_idempotency.appointment_id -> appointment e outcome_status='CONFIRMADO'.
-- Appointment sem recibo confirmado NAO e tocado.
-- =====================================================================

UPDATE appointments a
   SET status = 'CONFIRMADO',
       atualizado_em = CURRENT_TIMESTAMP
 WHERE a.status = 'PENDENTE'
   AND EXISTS (
       SELECT 1
         FROM booking_idempotency bi
        WHERE bi.appointment_id = a.id
          AND bi.outcome_status = 'CONFIRMADO'
   );
