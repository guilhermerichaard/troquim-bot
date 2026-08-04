-- =====================================================================
-- V13 — Persiste a raiz de identidade do negócio (businesses) e fecha o isolamento de
-- tenant com FK em TODA tabela que carrega business_id.
--
-- POR QUE ISTO ERA UMA LACUNA
--
-- Desde a V2, dezenas de tabelas carregam business_id como coluna de tenancy, mas nenhuma
-- delas referenciava uma raiz: o BusinessId era só um UUID solto, validado (quando muito)
-- pela camada Java. Um bug de tenant, ou uma carga direta no banco, podia gravar dado sob
-- um negócio que nunca existiu. Esta migration materializa essa raiz e fecha a porta.
--
-- INVENTÁRIO DAS TABELAS COM business_id (conferido no schema V1–V12, não presumido):
-- customers, booking_idempotency, whatsapp_flow_sessions, appointments, reservations,
-- whatsapp_channel_connections, owner_users, owner_sessions, services, professionals,
-- professional_services, business_hours, professional_availability.
--
-- MATERIALIZAÇÃO SEM DADO DE CLIENTE: cada BusinessId já em uso em alguma dessas tabelas
-- vira uma linha TÉCNICA em businesses — nome neutro, sem telefone/endereço inventado,
-- status ATIVO porque a própria existência dos dados prova que é uma operação em curso.
-- Nenhum nome de cliente (Gizelle, Unhas Divas, Dayana, Malu) entra aqui: são dados de
-- negócio, e dado de negócio não vive em migration versionada — mesma disciplina de V11/V12.
--
-- ${pilot_business_id} SEMPRE EXISTE, mesmo em banco vazio: é o único BusinessId que a
-- aplicação conhece de configuração (troquim.tenant.pilot-business-id), então precisa de
-- linha própria mesmo quando nenhuma tabela tenant-scoped ainda tem dado.
--
-- FK DEPOIS DA CARGA: as tabelas já existentes ganham a constraint só depois que toda
-- linha técnica foi inserida — nesta ordem, o ALTER TABLE ... ADD CONSTRAINT nunca encontra
-- um business_id órfão. Dali em diante, nem a aplicação nem uma carga direta via JDBC
-- conseguem gravar tenant inexistente: o PRÓPRIO PostgreSQL recusa.
-- =====================================================================

CREATE TABLE businesses (
    id            UUID         NOT NULL,
    nome          VARCHAR(120) NOT NULL,
    telefone      VARCHAR(30),
    endereco      VARCHAR(255),
    status        VARCHAR(20)  NOT NULL,
    criado_em     TIMESTAMP    NOT NULL,
    atualizado_em TIMESTAMP    NOT NULL,
    CONSTRAINT pk_businesses PRIMARY KEY (id)
);

-- Uma linha técnica para cada BusinessId já referenciado por dado tenant-scoped existente.
INSERT INTO businesses (id, nome, telefone, endereco, status, criado_em, atualizado_em)
SELECT DISTINCT t.business_id,
       'Negocio migrado ' || substr(CAST(t.business_id AS VARCHAR(36)), 1, 8),
       NULL, NULL, 'ATIVO', now(), now()
  FROM (
        SELECT business_id FROM customers
        UNION SELECT business_id FROM whatsapp_flow_sessions
        UNION SELECT business_id FROM appointments
        UNION SELECT business_id FROM reservations
        UNION SELECT business_id FROM whatsapp_channel_connections
        UNION SELECT business_id FROM owner_users
        UNION SELECT business_id FROM owner_sessions
        UNION SELECT business_id FROM services
        UNION SELECT business_id FROM professionals
        UNION SELECT business_id FROM professional_services
        UNION SELECT business_id FROM business_hours
        UNION SELECT business_id FROM professional_availability
        UNION SELECT business_id FROM booking_idempotency WHERE business_id IS NOT NULL
       ) t
 WHERE t.business_id IS NOT NULL
ON CONFLICT (id) DO NOTHING;

-- O piloto existe SEMPRE, mesmo em banco vazio (nenhuma tabela tenant-scoped populada
-- ainda) — é ele que ProvisionarNegocio vai exigir antes de aceitar o primeiro catálogo.
INSERT INTO businesses (id, nome, telefone, endereco, status, criado_em, atualizado_em)
VALUES ('${pilot_business_id}', 'Negocio piloto', NULL, NULL, 'ATIVO', now(), now())
ON CONFLICT (id) DO NOTHING;

-- FK em TODA tabela com business_id: nenhuma delas aceita, a partir daqui, um BusinessId
-- que não exista em businesses — nem pela aplicação, nem por carga direta via JDBC.
ALTER TABLE customers
    ADD CONSTRAINT fk_customers_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE booking_idempotency
    ADD CONSTRAINT fk_booking_idempotency_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE whatsapp_flow_sessions
    ADD CONSTRAINT fk_whatsapp_flow_sessions_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE appointments
    ADD CONSTRAINT fk_appointments_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE whatsapp_channel_connections
    ADD CONSTRAINT fk_whatsapp_channel_connections_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE owner_users
    ADD CONSTRAINT fk_owner_users_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE owner_sessions
    ADD CONSTRAINT fk_owner_sessions_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE services
    ADD CONSTRAINT fk_services_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE professionals
    ADD CONSTRAINT fk_professionals_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE professional_services
    ADD CONSTRAINT fk_professional_services_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE business_hours
    ADD CONSTRAINT fk_business_hours_business FOREIGN KEY (business_id) REFERENCES businesses (id);

ALTER TABLE professional_availability
    ADD CONSTRAINT fk_professional_availability_business FOREIGN KEY (business_id) REFERENCES businesses (id);
