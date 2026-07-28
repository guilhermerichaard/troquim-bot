# Runbook — Relançar o backend em outra VPS (disaster recovery)

Recriar backend + Postgres + Redis em uma VPS nova, restaurar o banco a partir de um
backup e voltar ao ar com **imagem versionada por SHA**. Cobre também o rollback.

Não cobre landing, Nginx, DNS e SSL — esses têm ciclo próprio e não são tocados aqui.

## 0. O que você precisa ter em mãos

| Item | Origem | Contém segredo? |
| --- | --- | --- |
| `troquim-<TS>.dump` | backup (`/opt/troquim/backups/<id>/`) | dados de negócio |
| `config-<TS>.tar.gz` | backup — composes + `.env.example` | **não** |
| `.env` real | cofre / gestor de segredos — **nunca** do Git nem do backup | **sim** |
| SHA do release | `git log` do repositório | não |

O pacote de configuração é deliberadamente livre de segredos: ele traz os composes e o
`.env.example`, e o `.env` real é reconstruído a partir do cofre. Restaurar backup nunca
restaura credencial.

## 1. Preparar a VPS

```bash
# Docker Engine + plugin compose
curl -fsSL https://get.docker.com | sh

mkdir -p /opt/troquim/{src,releases,backups}
docker network create troquim-internal
```

## 2. Recriar configuração

```bash
cd /opt/troquim
tar -xzf config-<TS>.tar.gz --strip-components=1   # composes + .env.example

cp .env.example .env
# Preencher .env a partir do COFRE. Mínimo obrigatório:
#   POSTGRES_USER, POSTGRES_PASSWORD
#   TROQUIM_PILOT_BUSINESS_ID, TROQUIM_ADMIN_API_KEY
#   TROQUIM_WHATSAPP_* (se for reativar WhatsApp)
#   WHATSAPP_FLOW_PRIVATE_KEY / _PASSWORD (se for reativar o Flow)
chmod 600 .env
```

`TROQUIM_WHATSAPP_FLOW_DRAFT=false` e `TROQUIM_WHATSAPP_FLOW_PREVIEW_TOKEN=` (vazio) são
os valores de produção: com eles o preview do editor da Meta fica inteiramente inerte.
Só ligue os dois juntos enquanto estiver de fato editando o Flow no painel.

## 3. Subir Postgres e Redis (antes do backend)

```bash
cd /opt/troquim
docker compose -f docker-compose.yml up -d postgres redis

# esperar o banco aceitar conexão
until docker exec troquim-postgres pg_isready -U "$POSTGRES_USER"; do sleep 2; done
```

## 4. Restaurar o banco

```bash
docker cp troquim-<TS>.dump troquim-postgres:/tmp/d.dump

docker exec troquim-postgres sh -c \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-privileges /tmp/d.dump'

docker exec troquim-postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select version, description, success from flyway_schema_history order by installed_rank"'
```

O restore precisa terminar com todas as migrations `success = t`. O backend valida o
schema no startup (`Successfully validated N migrations`) e recusa subir se divergir —
não force `flyway.repair` sem entender a causa.

## 5. Construir a imagem do release (por SHA)

```bash
RELEASE_SHA=84f2073          # <- SHA curto do commit a publicar
RELEASE_DIR=/opt/troquim/releases/${RELEASE_SHA}

git clone https://github.com/guilhermerichaard/troquim-bot "${RELEASE_DIR}"
git -C "${RELEASE_DIR}" checkout "${RELEASE_SHA}"

docker build -t "troquim-bot:${RELEASE_SHA}" "${RELEASE_DIR}"
```

A imagem sai do commit, então `troquim-bot:<sha>` é sempre reconstruível a partir do Git.
Nunca publique `:latest`: é ele que impede saber o que está no ar.

## 6. Subir o backend

```bash
cd /opt/troquim
export TROQUIM_RELEASE_IMAGE="troquim-bot:${RELEASE_SHA}"

CF="-f /opt/troquim/docker-compose.yml \
    -f /opt/troquim/src/troquim-bot/docker-compose.droplet.yml \
    -f ${RELEASE_DIR}/docker-compose.release.yml"

docker compose $CF config --quiet        # valida o merge SEM aplicar
docker compose $CF config --images       # confere a imagem resolvida

docker compose $CF up -d --no-deps troquim-bot
```

`--no-deps` é o que torna o deploy incremental: recria só o backend e deixa Postgres e
Redis de pé. **Nunca** use `docker compose down` num deploy — ele derruba a stack inteira
e, com `-v`, apaga o volume do banco.

## 7. Validar

```bash
until [ "$(docker inspect troquim-bot --format '{{.State.Health.Status}}')" = healthy ]; do sleep 5; done

docker inspect troquim-bot --format 'image={{.Config.Image}} health={{.State.Health.Status}} restarts={{.RestartCount}}'
curl -fsS http://localhost:8080/actuator/health

docker logs troquim-bot --since 5m 2>&1 | grep -iE 'ERROR|Exception' || echo 'sem erros'
docker logs troquim-bot --since 5m 2>&1 | grep -iE 'flyway|Started TroquimBotApplication'
```

Sinais de sucesso: `status: UP`, `restarts=0`, Flyway validando as migrations e nenhum
`ERROR`. O endpoint do Flow responde **400** a um envelope inválido (e não 404) — é a
prova barata de que ele está roteado sem precisar da chave privada.

## 8. Rollback

Todo deploy preserva a imagem anterior antes de trocar:

```bash
# ANTES de publicar (guardar o que está no ar)
docker tag "$(docker inspect troquim-bot --format '{{.Image}}')" troquim-bot:rollback-pre-${RELEASE_SHA}
```

Voltar:

```bash
cd /opt/troquim
export TROQUIM_RELEASE_IMAGE=troquim-bot:rollback-pre-84f2073   # ou troquim-bot:<sha-anterior>
docker compose $CF up -d --no-deps troquim-bot
```

Rollback de imagem **não** desfaz migration. Um release que aplica migration só é
reversível pelo backup (seção 4) — por isso vale checar, antes de publicar, se o release
traz algo em `src/main/resources/db/migration/`. O release `84f2073` não traz: é
stateless e volta apenas trocando a imagem.

## 9. Backup recorrente

```bash
TS=$(date -u +%Y%m%dT%H%M%SZ)
BK=/opt/troquim/backups/${TS}; mkdir -p "$BK"

docker exec troquim-postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-privileges' > "$BK/troquim-${TS}.dump"

tar -czf "$BK/config-${TS}.tar.gz" \
  -C /opt/troquim docker-compose.yml \
  -C /opt/troquim/src/troquim-bot docker-compose.droplet.yml .env.example

cd "$BK" && sha256sum *.dump *.tar.gz > SHA256SUMS
```

Depois **baixe para fora da VPS** e confira o SHA-256 na cópia local — backup que só
existe na máquina que ele deveria salvar não é backup:

```bash
scp root@<vps>:${BK}/'*' ./backup-local/
cd backup-local && sha256sum -c SHA256SUMS
```

Um dump só conta como restaurável depois de ter sido restaurado. Teste periodicamente num
Postgres descartável e isolado, e remova o ambiente ao terminar:

```bash
docker run -d --name restore-check --network none \
  -e POSTGRES_PASSWORD=throwaway -e POSTGRES_DB=restore_check postgres:16-alpine
docker cp troquim-<TS>.dump restore-check:/tmp/d.dump
docker exec restore-check pg_restore -U postgres -d restore_check --no-owner --no-privileges /tmp/d.dump
docker exec restore-check psql -U postgres -d restore_check -c \
  'select version, success from flyway_schema_history order by installed_rank'
docker rm -f restore-check
```

`--network none` mantém o teste isolado: a cópia restaurada não alcança nada e nada
alcança ela.
