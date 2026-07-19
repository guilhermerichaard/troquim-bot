# Tenancy — Vertical Slice 1: Customer

> Escopo desta entrega: a **primeira fundação** de multi-tenancy do MVP,
> aplicada **somente ao agregado Customer**. Implementa ARCHITECTURE_V2_1 §C8
> (identidade do Customer) e a parte de §7/§C7 relativa a Customer.

## O que entrou

- **`BusinessId`** (VO já existente) é a identidade de tenant; nunca `String` solta.
- **Customer pertence a um Business**: `BusinessId` obrigatório no aggregate, na
  entidade JPA (`business_id NOT NULL`) e em `phone_e164 NOT NULL`.
- **Identidade surrogate**: `CustomerId` é um UUID gerado; **não** é derivado do
  telefone. A identidade lógica do cliente é `(BusinessId, phone E.164)`.
- **Unicidade por tenant**: `UNIQUE (business_id, phone_e164)` + índice
  `idx_customers_business_id`.
- **Queries por tenant**: `CustomerRepository` não tem mais `findAll()` global;
  toda listagem/busca-por-telefone recebe `BusinessId`.
- **Resolução de tenant centralizada e tipada**: `TenantProperties`
  (`@ConfigurationProperties`, campo `UUID pilotBusinessId`) é a **fonte única**;
  `PilotTenantProvider` (porta `TenantProvider`) apenas a expõe. Controller e serviços
  resolvem o tenant pela porta — **sem UUID literal em código produtivo**.
  - Default (`11111111-…`) só em `application.properties` para dev/test.
  - `azure` (único profile produtivo do MVP): `troquim.tenant.pilot-business-id=${TROQUIM_PILOT_BUSINESS_ID}`
    **sem default** → variável ausente ou UUID inválido faz o startup falhar (fail-fast).
  - `dev`/`test`/`pilot`: valor de fixture local explícito (a base não tem default).
- **Migrations versionadas (Flyway)**: `V1__baseline_schema.sql` (baseline de todo o
  schema) e `V2__customer_tenancy.sql` (colunas → backfill do piloto → detecção de
  inválidos/duplicatas com falha diagnóstica → NOT NULL → UNIQUE + índice).
  - O backfill usa o **placeholder** `${pilot_business_id}` do Flyway, alimentado por
    `spring.flyway.placeholders.pilot_business_id=${troquim.tenant.pilot-business-id}`
    — **sem UUID literal no SQL**. Mesma fonte única da Application.
  - PostgreSQL (`azure`): **Flyway é a autoridade**; Hibernate `ddl-auto=validate`.
  - H2 (`test`): Hibernate gera o schema (create-drop); Flyway desligado. A entidade
    declara a mesma UNIQUE/índice para não haver schema divergente.
- Runbook da primeira migração em produção:
  `docs/operations/FLYWAY_FIRST_PRODUCTION_MIGRATION.md`.

## O que NÃO entrou (e por quê o MVP ainda não é totalmente tenant-safe)

Esta é uma entrega **vertical e incremental**. Os demais agregados **ainda não têm
`BusinessId`** e continuam usando `CustomerId.fromPhone(...)` (marcado `@Deprecated`)
como agrupador opaco:

- `Reservation`, `Appointment` (e `schedule.Appointment` legado)
- o fluxo de Conversation (`ConversationService`, `BookingQueryResponder`) que consulta
  agendamentos por esse id derivado

Consequência conhecida e aceita nesta fase: o `customer_id` gravado em
`reservations`/`appointments` (derivado do telefone) **não** coincide com o
`CustomerId` surrogate do agregado Customer. Não há integridade referencial entre eles
hoje; é um agrupador por telefone dentro do subsistema de agendamento.

**O MVP só estará totalmente tenant-safe quando `BusinessId` for propagado para
Availability/Scheduling/Reservation/Appointment** (fases posteriores). Até lá, o
isolamento por tenant vale para o read/write-path de **Customer**.

## Fora do escopo desta tarefa (inalterados)

Appointment, Reservation, Availability, WorkingSchedule, ServiceDuration, concorrência
de agendamento, idempotência, Meta/WhatsApp, Conversation, `schedule/Appointment`,
`SecurityConfig`, landing e os demais InMemory repositories.
