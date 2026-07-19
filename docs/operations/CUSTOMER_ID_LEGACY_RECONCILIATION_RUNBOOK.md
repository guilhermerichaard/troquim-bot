# Runbook — Reconciliação de `customer_id` legado (identidade consolidada)

> **Somente leitura.** Este runbook contém apenas consultas `SELECT` de diagnóstico.
> Não altera dados, schema, segurança nem as migrations V1/V2. Executar em réplica
> ou janela de manutenção, **com autorização explícita**. Não copiar telefones,
> nomes ou qualquer dado pessoal para relatórios — usar apenas as contagens.

## Situação e decisão de deploy

- **Dados novos: atomicamente consistentes.** A confirmação de booking
  (`BookingApplicationService.confirmar`) agora persiste Reservation, Appointment e
  Customer numa única transação Spring/JPA (`@Transactional`, ARCHITECTURE_V2_1 §C10).
  Falha ao persistir o Customer faz rollback de tudo — sem Reservation/Appointment
  órfãos. Provado por `BookingConfirmationRollbackPostgresTest` (PostgreSQL/Testcontainers).
- **Dados históricos: ainda são bloqueador de deploy.** Registros criados antes da
  consolidação podem conter `customer_id = fromPhone(...)` incompatível com o Customer
  persistido. A atomicidade **não** os corrige.
- **Antes de aprovar deploy: executar este runbook** contra o banco real (réplica/
  manutenção, com autorização).
  - **Q3 = 0** (nenhum órfão ativo) → **libera o deploy**.
  - **Q3 > 0** → **exige** reconciliação por **migration V3 em Java** (reusa
    `CustomerId.fromPhone`) **ou** limpeza operacional explicitamente aprovada, antes
    do deploy. Não implementado agora — depende desta evidência.

## Contexto

Antes da consolidação de identidade, `appointments.customer_id` e
`reservations.customer_id` eram gravados como `CustomerId.fromPhone(telefone)` =
`UUID.nameUUIDFromBytes("customer:" + telefone_cru)` — um hash determinístico do
telefone, **sem** relação com o `customers.id` (surrogate). Não há FK entre essas
tabelas e `customers` (confirmado em `V1__baseline_schema.sql`). Após a mudança, o
caminho de consulta/cancelamento resolve o `customer_id` **oficial** pelo Customer
persistido; registros antigos com o hash legado ficam **invisíveis** para consulta
e cancelamento.

As tabelas `appointments`/`reservations` **não** têm coluna de telefone: a única
ponte de reconciliação é recalcular `fromPhone(customers.phone)` e casar com o
`customer_id` legado (ver estratégia A no relatório). Estas queries **não**
recalculam o hash — detectam estruturalmente os órfãos (o sinal confiável de "linha
que exige migração").

---

## Q1 — Volume base

```sql
SELECT
  (SELECT count(*) FROM customers)    AS total_customers,
  (SELECT count(*) FROM appointments) AS total_appointments,
  (SELECT count(*) FROM reservations) AS total_reservations;
```

## Q2 — `customer_id` sem Customer correspondente (órfãos = exigem migração)

```sql
-- Appointments cujo customer_id não existe em customers.id
SELECT count(*) AS appointments_orfaos
FROM appointments a
WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = a.customer_id);

-- Reservations cujo customer_id não existe em customers.id
SELECT count(*) AS reservations_orfaas
FROM reservations r
WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = r.customer_id);
```

## Q3 — Órfãos que impactam o cliente AGORA (ativos)

Somente estes quebram consulta/cancelamento de agendamentos reais. Cancelados/
concluídos são inertes.

```sql
SELECT a.status, count(*) AS qtd
FROM appointments a
WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = a.customer_id)
  AND a.status IN ('PENDENTE', 'CONFIRMADO')
GROUP BY a.status;

SELECT r.status, count(*) AS qtd
FROM reservations r
WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = r.customer_id)
  AND r.status IN ('ATIVO', 'ATIVA', 'PENDENTE')  -- ajustar aos valores reais de ReservationStatus
GROUP BY r.status;
```

## Q4 — Distintos `customer_id` legados (dimensiona o esforço de reconciliação)

```sql
SELECT count(DISTINCT customer_id) AS clientes_legados_distintos
FROM (
  SELECT customer_id FROM appointments a
    WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = a.customer_id)
  UNION
  SELECT customer_id FROM reservations r
    WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = r.customer_id)
) t;
```

## Q5 — Consistência interna já correta (linhas que NÃO precisam migração)

```sql
SELECT count(*) AS appointments_ja_oficiais
FROM appointments a
WHERE EXISTS (SELECT 1 FROM customers c WHERE c.id = a.customer_id);
```

## Q6 — Colisão cross-tenant potencial (por que o telefone não pode voltar a ser identidade)

Telefones repetidos em tenants diferentes: cada um DEVE ter um `customers.id`
distinto (a UNIQUE `(business_id, phone_e164)` garante). Diagnóstico:

```sql
SELECT phone_e164, count(*) AS tenants
FROM customers
GROUP BY phone_e164
HAVING count(*) > 1;
```

---

## Interpretação

- **Q2 = 0 e Q3 = 0** → sem dados históricos incompatíveis: a mudança é segura para
  deploy sem migração (apenas confirmar que a base é nova/limpa).
- **Q3 > 0** → existem agendamentos/reservas ativos invisíveis ao cliente:
  **bloquear deploy** até reconciliar (estratégia A) ou descartar comprovadamente
  (estratégia B).
- **Q4** dimensiona o custo; **Q6** deve retornar vazio (senão há bug de tenancy anterior).
