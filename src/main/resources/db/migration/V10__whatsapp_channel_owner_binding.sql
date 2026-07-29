-- =====================================================================
-- V10 — Vínculo opcional de dono na conexão de canal WhatsApp.
--
-- owner_user_id amarra o nonce do Embedded Signup a QUEM iniciou, não só a QUAL
-- negócio: a finalização passa a exigir o mesmo dono, quando o início teve um.
-- NULLABLE porque o caminho administrativo (Bearer, sem identidade de dono) continua
-- válido e não tem dono para amarrar.
--
-- Sem backfill: nenhuma linha real de produção depende disso (Meta ainda não está
-- configurada), e a coluna nasce nula em qualquer linha pré-existente.
-- =====================================================================

ALTER TABLE whatsapp_channel_connections ADD COLUMN owner_user_id UUID;
