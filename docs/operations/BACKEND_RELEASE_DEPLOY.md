# Runbook — Deploy versionado do backend (troquim-bot)

Deploy **reproduzível, versionado e com rollback controlado** do backend em produção.

## Produção atual

A produção roda em **AWS EC2, região sa-east-1 (São Paulo)**. O nome
`docker-compose.droplet.yml` é legado histórico: o arquivo continua em uso por
compatibilidade, mas a infraestrutura canônica já não é DigitalOcean.

Última validação operacional registrada em **2026-09-18**:

- backend: `troquim-bot:a154e10`;
- Flyway: `V14`;
- PostgreSQL e Redis permanecem serviços independentes;
- imagem anterior `troquim-bot:dca43ea` preservada para rollback;
- health público: `https://api.troquim.app/actuator/health`.

## Princípios

- GitHub é a fonte canônica do código e dos artefatos versionados de release.
- Cada release usa imagem imutável `troquim-bot:<short-sha>`.
- Flyway é a única autoridade do schema em PostgreSQL.
- `TROQUIM_FLYWAY_BASELINE_ON_MIGRATE=false` em operação normal.
- O deploy recria **somente** `troquim-bot`; PostgreSQL e Redis não são reiniciados.
- Backup validado do banco é obrigatório antes de migration em produção.
- Nunca imprimir segredos no terminal ou versioná-los.

## Estrutura de release

Cada release fica em:

```
/opt/troquim/releases/<short-sha>
```

Esse diretório contém o checkout exato daquele commit e seu
`docker-compose.release.yml`.

A imagem é construída separadamente:

```bash
docker build \
  -t "troquim-bot:<short-sha>" \
  "/opt/troquim/releases/<short-sha>"
```

O Compose não deve fazer build durante o deploy.

## Compose: preservar o estado real da produção

A produção pode ter overlays operacionais adicionais, por exemplo os arquivos de
WhatsApp Flow. Portanto **não reconstrua manualmente a lista de `-f` a partir deste
documento**.

O estado corrente do projeto Compose é a fonte operacional. O script
`scripts/deploy-prod-release.sh` lê dos labels do container em execução:

- `com.docker.compose.project`;
- `com.docker.compose.project.working_dir`;
- `com.docker.compose.project.config_files`.

Ele preserva a ordem e todos os overlays correntes e substitui **apenas** o
`docker-compose.release.yml` pelo arquivo versionado do novo release.

Isso evita perder configuração runtime de Flow/WhatsApp ao seguir um runbook antigo.

## Preflight obrigatório para migrations

Antes de aplicar migrations novas em produção:

1. criar um dump `pg_dump -Fc`;
2. validar o dump com `pg_restore -l`;
3. restaurar o dump em um banco temporário;
4. subir a nova imagem apontando somente para essa cópia;
5. exigir `/actuator/health = UP`;
6. confirmar que todas as migrations esperadas foram aplicadas com `success=true`;
7. destruir container e banco temporários.

Nenhuma migration nova deve ser testada pela primeira vez no banco de produção.

## Deploy canônico

O script versionado é:

```
scripts/deploy-prod-release.sh
```

Uso:

```bash
sudo -E ./scripts/deploy-prod-release.sh \
  <release-tag> \
  <flyway-atual-esperado> \
  <flyway-final-esperado>
```

Exemplo do release validado em 2026-09-18:

```bash
sudo -E ./scripts/deploy-prod-release.sh a154e10 10 14
```

O script:

1. valida imagem atual, health e labels do Compose;
2. confirma a versão Flyway atual;
3. cria e valida backup fresco;
4. preserva os Compose overlays atualmente usados;
5. valida `docker compose config --quiet`;
6. recria apenas `troquim-bot` com `--no-deps --force-recreate --no-build`;
7. espera o health do container;
8. valida health local, Flyway e health público;
9. mantém a imagem anterior disponível.

## Rollback

A imagem anterior deve permanecer local e imutável.

Rollback de aplicação pode reapontar o release para a imagem anterior, mas **Flyway não
faz down migration automaticamente**. Após migrations forward, não assuma que o binário
antigo é compatível com o schema novo.

Se houver incompatibilidade de schema, use o backup pré-deploy e execute uma restauração
controlada conforme o runbook de banco. Não restaure automaticamente em caso de simples
falha de health: primeiro preserve logs e identifique a causa.

## Segredos

Segredos continuam fora do Git. O deploy deve reutilizar o ambiente operacional existente
sem imprimir valores de:

- senha PostgreSQL;
- `TROQUIM_ADMIN_API_KEY`;
- tokens Meta/WhatsApp;
- `WHATSAPP_FLOW_PRIVATE_KEY`;
- chaves de criptografia de credenciais de canal.

Nunca transmitir arquivos YAML de produção por heredoc remoto a partir do PowerShell.
Prefira checkout Git/versionado.

## Critério de sucesso

Um release só é considerado concluído quando, simultaneamente:

```
container troquim-bot = healthy
imagem em execução = release esperado
Flyway = versão final esperada
zero migrations com success=false
https://api.troquim.app/actuator/health = UP
postgres/redis = continuam healthy
```
