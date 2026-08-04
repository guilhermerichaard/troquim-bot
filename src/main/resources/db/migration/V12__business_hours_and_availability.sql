-- =====================================================================
-- V12 — Expediente do negócio e disponibilidade dos profissionais.
--
-- SOMENTE SCHEMA. Nenhum negócio, profissional ou horário de cliente é inserido aqui: o
-- expediente do piloto entra pelo caso de uso ProvisionarNegocio, explícito e idempotente.
-- Semear dado de cliente numa migration versionada o tornaria parte irreversível do schema,
-- replicado em todo ambiente que rodasse o Flyway — inclusive no de outro cliente.
--
-- MODELO POR PERÍODOS, NÃO POR ABERTURA/FECHAMENTO
--
-- Cada linha é UM período de UM dia. Um dia com almoço tem duas linhas (09:00-12:00 e
-- 13:00-18:00). Sábado com horário diferente é só outra linha. Dia fechado é a AUSÊNCIA de
-- linhas — não existe coluna "aberto", justamente para não haver dois jeitos de dizer a
-- mesma coisa (linha ausente vs. linha com aberto=false) que possam divergir.
--
-- ISOLAMENTO GARANTIDO PELO BANCO
--
-- professional_availability carrega business_id e referencia professionals por FK COMPOSTA
-- (business_id, professional_id). O PostgreSQL recusa, por si só, cadastrar disponibilidade
-- do profissional do negócio A sob o negócio B: mesmo um bug de tenant na camada Java não
-- consegue gravar a associação cruzada. A UNIQUE (business_id, id) em professionals já foi
-- criada na V11 e é o alvo dessa FK.
--
-- SEM PERÍODO ATRAVESSANDO MEIA-NOITE: o CHECK hora_inicio < hora_fim é a mesma invariante
-- do Value Object IntervaloDeHorario, aplicada também no banco. Não é redundância inútil —
-- é a garantia de que uma carga feita fora da aplicação não introduza período invertido, que
-- quebraria silenciosamente toda a aritmética de slots.
-- =====================================================================

CREATE TABLE business_hours (
    id            UUID        NOT NULL,
    business_id   UUID        NOT NULL,
    dia_semana    VARCHAR(10) NOT NULL,
    hora_inicio   TIME        NOT NULL,
    hora_fim      TIME        NOT NULL,
    criado_em     TIMESTAMP   NOT NULL,
    atualizado_em TIMESTAMP   NOT NULL,
    CONSTRAINT pk_business_hours PRIMARY KEY (id),
    CONSTRAINT ck_business_hours_periodo_valido CHECK (hora_inicio < hora_fim),
    -- O mesmo período exato não pode ser cadastrado duas vezes no mesmo dia. A recusa de
    -- períodos apenas SOBREPOSTOS (09-13 junto de 12-18) fica no agregado BusinessHours:
    -- exige comparar intervalos, que o SQL padrão não expressa como constraint simples.
    CONSTRAINT uq_business_hours_periodo UNIQUE (business_id, dia_semana, hora_inicio, hora_fim)
);

CREATE INDEX idx_business_hours_business_dia ON business_hours (business_id, dia_semana);

CREATE TABLE professional_availability (
    id              UUID        NOT NULL,
    business_id     UUID        NOT NULL,
    professional_id UUID        NOT NULL,
    dia_semana      VARCHAR(10) NOT NULL,
    hora_inicio     TIME        NOT NULL,
    hora_fim        TIME        NOT NULL,
    status          VARCHAR(20) NOT NULL,
    criado_em       TIMESTAMP   NOT NULL,
    atualizado_em   TIMESTAMP   NOT NULL,
    CONSTRAINT pk_professional_availability PRIMARY KEY (id),
    CONSTRAINT ck_professional_availability_periodo_valido CHECK (hora_inicio < hora_fim),
    -- Cross-tenant barrado pelo banco: o par (negócio, profissional) precisa existir junto.
    CONSTRAINT fk_professional_availability_professional
        FOREIGN KEY (business_id, professional_id)
        REFERENCES professionals (business_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_professional_availability_busca
    ON professional_availability (business_id, professional_id, dia_semana, status);
