# Runbook — Primeira migração Flyway em produção (PostgreSQL)

Aplica `V1__baseline_schema.sql` + `V2__customer_tenancy.sql` no PostgreSQL da
Droplet (profile `azure`). A partir daqui, **Flyway é a autoridade do schema** e o
Hibernate apenas valida (`ddl-auto=validate`).

> **Causa raiz corrigida (baseline v1, não v0).** A base atual da Droplet foi criada
> por `ddl-auto=update` (sem Flyway): já tem as tabelas, mas **não** tem
> `flyway_schema_history`. Com `baseline-version=0`, o Flyway marcava o baseline em v0
> e aplicava tudo `> 0` — inclusive a **V1**, que faz `CREATE TABLE customers` e
> quebrava com `relation "customers" already exists`.
>
> Correção: **`baseline-version=1`**. O baseline passa a marcar a **V1** (o schema já
> existente) como aplicada; o Flyway aplica então **apenas migrations `> 1` (a V2)**,
> **sem** reexecutar a V1 sobre as tabelas legadas e **sem** `IF NOT EXISTS`
> mascarando divergências. Em banco **vazio** (Testcontainers) o baseline **não
> dispara** (schema vazio) e V1→V2 rodam normalmente.

> **A adoção do Flyway no legado é EXPLÍCITA.** `spring.flyway.baseline-on-migrate`
> vem de `${TROQUIM_FLYWAY_BASELINE_ON_MIGRATE:false}` — no funcionamento normal fica
> **`false`**. Só na **primeira** migração do banco legado ela é temporariamente
> **`true`**. Com `false`, um banco legado sem `flyway_schema_history` **falha no
> boot sem alterar o schema** (fail-fast), em vez de mascarar silenciosamente a
> primeira adoção.

## 0. Pré-requisitos

- Variável de ambiente **`TROQUIM_PILOT_BUSINESS_ID`** definida (UUID do negócio piloto).
  Sem ela, o app (profile `azure`) **não sobe** (fail-fast). O mesmo valor alimenta o
  placeholder `${pilot_business_id}` da V2 — fonte única, sem UUID literal.
- **`TROQUIM_FLYWAY_BASELINE_ON_MIGRATE=true`** definida **apenas nesta primeira
  execução** contra o legado (ver §4). No funcionamento normal ela fica ausente/`false`.
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
       -password="$SPRING_DATASOURCE_PASSWORD" -baselineOnMigrate=true -baselineVersion=1 \
       -placeholders.pilot_business_id="$TROQUIM_PILOT_BUSINESS_ID" info
```
Esperado: **V1 já refletida pelo baseline (v1)** e **V2 `Pending`** — a V1 **não**
aparece como pendente (o schema legado já a satisfaz).

## 4. Aplicar (habilitação EXPLÍCITA do baseline)

> A adoção do Flyway no legado exige ligar o baseline **só nesta execução**.

Opção A — deixar a aplicação aplicar no boot (recomendado): subir o serviço com
`SPRING_PROFILES_ACTIVE=azure`, `TROQUIM_PILOT_BUSINESS_ID` definido e, **nesta primeira
execução**, `TROQUIM_FLYWAY_BASELINE_ON_MIGRATE=true`. O Flyway marca o baseline em v1 e
aplica a V2 antes do Hibernate validar.

**Após o sucesso**, remova/deixe `TROQUIM_FLYWAY_BASELINE_ON_MIGRATE=false` no próximo
deploy. Daqui em diante o `flyway_schema_history` já existe e o baseline não é mais
necessário (nem desejado).

Opção B — CLI, antes de subir a app:
```
flyway ... -baselineOnMigrate=true -baselineVersion=1 \
       -placeholders.pilot_business_id="$TROQUIM_PILOT_BUSINESS_ID" migrate
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
-- schema history (esperado no legado: baseline v1 + V2; V1 NÃO reexecutada):
--   installed_rank | version | type     | description
--   1              | 1       | BASELINE | << Flyway Baseline >>
--   2              | 2       | SQL      | customer tenancy
SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;

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

## 8. Segurança operacional — regras invioláveis

- **`TROQUIM_FLYWAY_BASELINE_ON_MIGRATE=true` só na primeira execução do legado.**
  Depois do sucesso, volte para `false` (ou remova a variável).
- **Nunca reutilizar uma cópia onde a tentativa com `baseline-version=0` (ou qualquer
  tentativa que falhou no meio) já rodou.** O baseline v0 grava um
  `flyway_schema_history` inconsistente. **Restaure o backup novamente** e recomece de
  um estado limpo.
- **Não executar diretamente na produção antes da validação completa numa cópia
  restaurada.** Valide o boot com o baseline v1 na cópia (V1 pulada, V2 aplicada,
  dados intactos, `validate` do Hibernate passando) e só então aplique na Droplet.

### Resumo das variáveis

| Momento | `TROQUIM_FLYWAY_BASELINE_ON_MIGRATE` | Comportamento |
| --- | --- | --- |
| Primeira migração do legado | `true` | Baseline v1 gravado; **V1 pulada**, **V2 aplicada**. |
| Após o sucesso (todo deploy seguinte) | `false` (ou ausente) | `flyway_schema_history` já existe; apenas migrations futuras. |
| Banco legado com a flag `false` (por engano) | `false` | **Falha no boot sem alterar o schema** — prova de que a adoção precisa ser explícita. |
