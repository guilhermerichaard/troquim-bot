-- =====================================================================
-- V11 — Catálogo persistido: serviços, profissionais e o vínculo entre eles.
--
-- SOMENTE SCHEMA. Esta migration NÃO provisiona dados de nenhum negócio: o catálogo do
-- primeiro piloto entra pelo caso de uso ProvisionarNegocio, via bootstrap explícito,
-- idempotente e desligado por padrão. Semear um cliente específico numa migration
-- versionada tornaria dado de negócio parte irreversível do schema, replicado em todo
-- ambiente que rodasse o Flyway — inclusive nos de outro cliente.
--
-- ISOLAMENTO GARANTIDO PELO BANCO
--
-- professional_services carrega business_id e usa FKs COMPOSTAS:
--   (business_id, professional_id) -> professionals
--   (business_id, service_id)      -> services
-- Assim o próprio PostgreSQL recusa associar um profissional do negócio A a um serviço do
-- negócio B. O isolamento deixa de depender exclusivamente do código de aplicação: mesmo
-- um bug de tenant na camada Java não consegue gravar a associação cruzada.
--
-- Para que essas FKs compostas sejam possíveis, professionals e services precisam de
-- UNIQUE (business_id, id) — a PK sozinha (id) não serve como alvo de FK composta.
--
-- PREÇO: preco_valor/preco_moeda são anuláveis porque "não precificado" é estado legítimo
-- do MVP. A ausência é representada pelos DOIS nulos juntos; o CHECK impede o estado
-- inconsistente de ter valor sem moeda ou moeda sem valor.
-- =====================================================================

CREATE TABLE services (
    id              UUID         NOT NULL,
    business_id     UUID         NOT NULL,
    nome            VARCHAR(120) NOT NULL,
    descricao       VARCHAR(500),
    duracao_minutos INTEGER      NOT NULL,
    preco_valor     NUMERIC(12,2),
    preco_moeda     VARCHAR(3),
    status          VARCHAR(20)  NOT NULL,
    criado_em       TIMESTAMP    NOT NULL,
    atualizado_em   TIMESTAMP    NOT NULL,
    CONSTRAINT pk_services PRIMARY KEY (id),
    CONSTRAINT uq_services_business_id UNIQUE (business_id, id),
    CONSTRAINT ck_services_duracao_positiva CHECK (duracao_minutos > 0),
    CONSTRAINT ck_services_preco_completo CHECK (
        (preco_valor IS NULL AND preco_moeda IS NULL)
        OR (preco_valor IS NOT NULL AND preco_moeda IS NOT NULL)
    )
);

CREATE INDEX idx_services_business_status ON services (business_id, status);

CREATE TABLE professionals (
    id            UUID         NOT NULL,
    business_id   UUID         NOT NULL,
    nome          VARCHAR(120) NOT NULL,
    telefone      VARCHAR(30),
    status        VARCHAR(20)  NOT NULL,
    criado_em     TIMESTAMP    NOT NULL,
    atualizado_em TIMESTAMP    NOT NULL,
    CONSTRAINT pk_professionals PRIMARY KEY (id),
    CONSTRAINT uq_professionals_business_id UNIQUE (business_id, id)
);

CREATE INDEX idx_professionals_business_status ON professionals (business_id, status);

-- Vínculo OFICIAL profissional x serviço.
CREATE TABLE professional_services (
    business_id     UUID NOT NULL,
    professional_id UUID NOT NULL,
    service_id      UUID NOT NULL,
    CONSTRAINT pk_professional_services PRIMARY KEY (business_id, professional_id, service_id),
    CONSTRAINT fk_professional_services_professional
        FOREIGN KEY (business_id, professional_id)
        REFERENCES professionals (business_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_professional_services_service
        FOREIGN KEY (business_id, service_id)
        REFERENCES services (business_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_professional_services_service ON professional_services (business_id, service_id);

-- Metadado textual, sem papel em regra de negócio. Fica separado justamente para deixar
-- claro que não é o vínculo: quem decide quem atende o quê é professional_services.
CREATE TABLE professional_especialidades (
    professional_id UUID         NOT NULL,
    especialidade   VARCHAR(120) NOT NULL,
    CONSTRAINT pk_professional_especialidades PRIMARY KEY (professional_id, especialidade),
    CONSTRAINT fk_professional_especialidades_professional
        FOREIGN KEY (professional_id)
        REFERENCES professionals (id)
        ON DELETE CASCADE
);
