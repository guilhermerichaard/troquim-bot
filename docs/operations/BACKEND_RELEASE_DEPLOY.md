# Runbook — Deploy versionado do backend (troquim-bot)

Deploy **reproduzível e versionado** do backend na Droplet, **sem acoplar o Compose a um
release específico**. A imagem é selecionada por variável de ambiente; o build acontece
separadamente; nenhum segredo, UUID ou regra de negócio vive no Compose.

## Arquitetura do release

Três arquivos Compose são combinados por merge (ordem importa, o último vence):

| Arquivo | Responsabilidade |
| --- | --- |
| `/opt/troquim/docker-compose.yml` | Infra da Droplet (postgres, rede `troquim-internal`, etc.). Não versionado por este repo. |
| `/opt/troquim/src/troquim-bot/docker-compose.droplet.yml` | Serviço `troquim-bot`: datasource, profile `azure`, porta, healthcheck, `depends_on`. **Fonte única** dessas configs. |
| `${RELEASE_DIR}/docker-compose.release.yml` | **Só** seleciona a `image` versionada e injeta as variáveis do release. Sem SHA hardcoded, sem segredo. |

O `docker-compose.release.yml` **não conhece o SHA**: quem escolhe a imagem é
`TROQUIM_RELEASE_IMAGE`. Assim o mesmo arquivo serve a qualquer release e fica versionado
no Git, eliminando o arquivo manual/CRLF que hoje só existe na Droplet.

## Fluxo de deploy

### 1. Preparação

```
RELEASE_SHA=<short-sha>
RELEASE_DIR=/opt/troquim/releases/${RELEASE_SHA}
TROQUIM_RELEASE_IMAGE=troquim-bot:${RELEASE_SHA}
```

`${RELEASE_DIR}` contém o clone/checkout daquele commit (com `Dockerfile` e o
`docker-compose.release.yml` versionado deste repo).

### 2. Build (separado do Compose)

```
docker build \
  -t "${TROQUIM_RELEASE_IMAGE}" \
  "${RELEASE_DIR}"
```

O build produz a imagem `troquim-bot:${RELEASE_SHA}`. **O Compose não faz build** no
deploy — ele apenas aponta para esta imagem já construída.

### 3. Arquivos do Compose

Usar **exatamente** os três arquivos:

```
/opt/troquim/docker-compose.yml
/opt/troquim/src/troquim-bot/docker-compose.droplet.yml
${RELEASE_DIR}/docker-compose.release.yml
```

### 4. Validação (antes de aplicar)

```
export TROQUIM_RELEASE_IMAGE

docker compose \
  -f /opt/troquim/docker-compose.yml \
  -f /opt/troquim/src/troquim-bot/docker-compose.droplet.yml \
  -f "${RELEASE_DIR}/docker-compose.release.yml" \
  config --quiet
```

`config --quiet` valida o merge e a interpolação **sem** aplicar nada. Saída vazia +
código 0 = OK. As demais variáveis obrigatórias (`TROQUIM_PILOT_BUSINESS_ID`,
`TROQUIM_ADMIN_API_KEY`) vêm do `.env`/ambiente da Droplet — nunca do Git.

### 5. Deploy (recria SOMENTE o backend)

```
docker compose \
  -f /opt/troquim/docker-compose.yml \
  -f /opt/troquim/src/troquim-bot/docker-compose.droplet.yml \
  -f "${RELEASE_DIR}/docker-compose.release.yml" \
  up -d --no-deps --force-recreate troquim-bot
```

`--no-deps --force-recreate troquim-bot` recria **apenas** o `troquim-bot`. **postgres,
redis e landing NÃO são recriados.**

## Regras operacionais (invioláveis)

- **`TROQUIM_RELEASE_IMAGE` deve ser exportada** (`export`) antes de `docker compose
  config`/`up`. Sem ela, o `:?` falha o comando com mensagem clara (fail-fast) — nunca
  sobe uma imagem indefinida.
- **`TROQUIM_FLYWAY_BASELINE_ON_MIGRATE` permanece `false`** em regime permanente (default
  do arquivo). Só é `true` na primeira adoção do Flyway em banco legado — ver
  [FLYWAY_FIRST_PRODUCTION_MIGRATION.md](FLYWAY_FIRST_PRODUCTION_MIGRATION.md).
- **Nunca criar `docker-compose.release.yml` manualmente na Droplet.** Use o arquivo
  versionado do `${RELEASE_DIR}` (checkout do commit). O arquivo manual foi a causa do
  deploy quebrado (estado não reproduzível + CRLF).
- **Não transmitir YAML por heredoc remoto via PowerShell/SSH.** O PowerShell escapa
  `${VAR}` como `\${VAR}` e grava CRLF, quebrando a interpolação. Faça `git checkout`/
  `scp` do arquivo já versionado (LF garantido pelo `.gitattributes`).
- **Não alterar tags de releases anteriores.** Cada release usa sua própria
  `troquim-bot:<sha>`; imagens antigas ficam intactas para rollback.
- **O deploy recria somente `troquim-bot`** (`--no-deps`). postgres/redis/landing seguem
  no ar.

## Rollback

Reexecute o passo 5 apontando `TROQUIM_RELEASE_IMAGE=troquim-bot:<sha-anterior>` (a
imagem anterior não foi alterada). Nenhuma migração é revertida automaticamente pelo
Compose — ver o runbook do Flyway para o schema.

## Validação local (valores fictícios, sem segredos reais)

Com os arquivos compatíveis deste repositório (`docker-compose.yml` faz o papel da infra
que define `postgres`):

```
TROQUIM_RELEASE_IMAGE=troquim-bot:test \
TROQUIM_PILOT_BUSINESS_ID=11111111-1111-4111-8111-111111111111 \
TROQUIM_ADMIN_API_KEY=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
TROQUIM_FLYWAY_BASELINE_ON_MIGRATE=false \
docker compose \
  -f docker-compose.yml \
  -f docker-compose.droplet.yml \
  -f docker-compose.release.yml \
  config
```

Confirme na saída: `image: troquim-bot:test`, `TROQUIM_FLYWAY_BASELINE_ON_MIGRATE: "false"`,
e nenhum literal `\${VAR}`. Os valores acima são **fictícios** — nunca use segredos reais
em validação nem os comite.
