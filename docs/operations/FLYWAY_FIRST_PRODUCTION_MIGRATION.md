# Runbook — Primeira migração Flyway em produção (PostgreSQL)

Aplica `V1__baseline_schema.sql` + `V2__customer_tenancy.sql` no PostgreSQL da
Droplet (profile `azure`). A partir daqui, **Flyway é a autoridade do schema** e o
Hibernate apenas valida (`ddl-auto=validate`).

> A base atual da Droplet foi criada por `ddl-auto=update` (sem Flyway). Por isso
> `spring.flyway.baseline-on-migrate=true` / `baseline-version=0` estão no profile
> `azure`: o Flyway marca o schema existente como baseline (v0) e aplica V1→V2 por
> cima, **sem recriar** as tabelas já existentes.

## 0. Pré-requisitos

- Variável de ambiente **`TROQUIM_PILOT_BUSINESS_ID`** definida (UUID do negócio piloto).
  Sem ela, o app (profile `azure`) **não sobe** (fail-fast). O mesmo valor alimenta o
  placeholder `${pilot_business_id}` da V2 — fonte única, sem UUID literal.
- Acesso ao Postgres (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`).
- Janela de manutenção curta (a V2 tranca a tabela `customers` brevemente).

## 1. Backup (obrigatório — a V2 altera `customers`)

```
pg_dump --format=custom --file=troquim_pre_v2_$(date +%F).dump "$SPRING_DATASOURCE_URL"
# valide o tamanho do arquivo antes de prosseguir
```

## 2. Pré-checagem de dados (evita falha da V2 no meio da janela)

A V2 **aborta com diagnóstico** (sem descartar dados) se houver telefone inválido
para E.164 ou duplicata `(business_id, phone_e164)`. Rode ANTES, em modo leitura:

```sql
-- telefones que não normalizam para E.164 (+DDI...):
SELECT id, phone FROM customers
WHERE ('+' || regexp_replace(phone, '\D', '', 'g')) !~ '^\+[1-9][0-9]{7,14}$';

-- futuros duplicados por (piloto, phone_e164):
SELECT ('+' || regexp_replace(phone, '\D', '', 'g')) AS e164, count(*)
FROM customers GROUP BY 1 HAVING count(*) > 1;
```

Se algo aparecer: **corrija/consolide manualmente** (não há correção automática — ver
§5) e repita a checagem até vir vazio.

## 3. Dry-run / validação do plano

```
# Confere migrations pendentes sem aplicar:
flyway -url="$SPRING_DATASOURCE_URL" -user="$SPRING_DATASOURCE_USERNAME" \
       -password="$SPRING_DATASOURCE_PASSWORD" -baselineOnMigrate=true -baselineVersion=0 \
       -placeholders.pilot_business_id="$TROQUIM_PILOT_BUSINESS_ID" info
```
Esperado: V1 e V2 como `Pending` (ou V1 já refletido pelo baseline, V2 `Pending`).

## 4. Aplicar

Opção A — deixar a aplicação aplicar no boot (recomendado): subir o serviço com
`SPRING_PROFILES_ACTIVE=azure` e `TROQUIM_PILOT_BUSINESS_ID` definido. O Flyway roda antes do
Hibernate validar.

Opção B — CLI, antes de subir a app:
```
flyway ... -placeholders.pilot_business_id="$TROQUIM_PILOT_BUSINESS_ID" migrate
```

## 5. Se a V2 abortar (telefone inválido / duplicata)

A transação da V2 faz **rollback** (nenhum dado perdido; colunas/constraints não
ficam pela metade). Ações:
1. Ler a mensagem (`V2 abortada: N cliente(s) com telefone invalido...` ou
   `...duplicados por (business_id, phone_e164)`).
2. Corrigir os telefones (formato `+<DDI><DDD><numero>`) ou consolidar duplicados
   diretamente na tabela `customers`.
3. `flyway repair` (limpa o registro de falha) e repetir a §4.

## 6. Verificação pós-migração

```sql
-- schema history:
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;

-- todo cliente recebeu o tenant piloto e phone_e164:
SELECT count(*) AS total,
       count(*) FILTER (WHERE business_id IS NULL)  AS sem_tenant,
       count(*) FILTER (WHERE phone_e164 IS NULL)   AS sem_e164
FROM customers;   -- esperado: sem_tenant = 0, sem_e164 = 0

-- constraint e índice existem:
SELECT conname FROM pg_constraint WHERE conname = 'uq_customers_business_phone';
SELECT indexname FROM pg_indexes WHERE indexname = 'idx_customers_business_id';
```
E, pela aplicação: `GET /customers` retorna os clientes do tenant; startup sem erro
de `validate`.

## 7. Rollback

Flyway não faz down-migration. Para reverter: **restaurar o dump** do §1
(`pg_restore`) e voltar a imagem anterior (que usava `ddl-auto=update`). Por isso o
backup do §1 é obrigatório e a pré-checagem do §2 evita descobrir problemas só na
janela.
