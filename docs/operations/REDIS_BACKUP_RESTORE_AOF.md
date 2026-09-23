# Redis — backup, inspeção e restore com AOF

Atualizado em: 6 de agosto de 2026

## Aviso

Este é um procedimento operacional de emergência.

**Não executar restore durante revisão documental, auditoria ou teste casual.**

Restore só é permitido quando houver:

- perda ou corrupção comprovada;
- responsável definido;
- janela operacional acordada;
- backup identificado e íntegro;
- rollback preparado;
- impacto aceito;
- comandos revisados para o ambiente real.

Este documento não declara o estado atual do Redis de produção. Container, imagem, volumes, senha, AOF, `DBSIZE`, PID e caminhos devem ser reconfirmados no momento da operação.

## Princípio

Quando AOF está habilitado, não se deve presumir que copiar apenas `dump.rdb` restaurará o estado esperado.

A sequência de carregamento depende da configuração e dos arquivos efetivamente presentes. Antes de qualquer ação, identificar:

- valor de `appendonly`;
- `appenddirname`;
- `dir`;
- arquivos AOF manifest/base/incremental;
- existência e data de `dump.rdb`;
- volume montado no container;
- imagem em execução;
- logs do último startup.

## Responsabilidades

- Domain e Application não conhecem backup/restore.
- Infrastructure define Redis e persistência técnica.
- Operação executa inspeção, backup, restore e rollback.
- Documentação não substitui evidência do ambiente.

## 1. Coleta de evidência — somente leitura

Resolva primeiro os nomes reais do compose e do serviço. Os exemplos abaixo usam placeholders.

```bash
COMPOSE_FILE="docker-compose.droplet.yml"
REDIS_SERVICE="redis"

# Estado dos serviços
docker compose -f "$COMPOSE_FILE" ps

# Imagem, mounts e comando efetivos
docker inspect "$(docker compose -f "$COMPOSE_FILE" ps -q "$REDIS_SERVICE")" \
  --format '{{json .Config.Image}} {{json .Config.Cmd}} {{json .Mounts}}'

# Configuração relevante dentro do Redis
docker compose -f "$COMPOSE_FILE" exec -T "$REDIS_SERVICE" \
  redis-cli --no-auth-warning CONFIG GET appendonly appenddirname dir dbfilename

# Saúde e quantidade observada de chaves
docker compose -f "$COMPOSE_FILE" exec -T "$REDIS_SERVICE" \
  redis-cli --no-auth-warning PING

docker compose -f "$COMPOSE_FILE" exec -T "$REDIS_SERVICE" \
  redis-cli --no-auth-warning DBSIZE

# Logs de startup e persistência
docker compose -f "$COMPOSE_FILE" logs --no-color --tail=300 "$REDIS_SERVICE"
```

### Senha

Não escreva senha no shell history nem neste documento.

Use o mecanismo de segredo já adotado pelo ambiente. Caso `redis-cli` exija autenticação, injete a senha por variável temporária ou arquivo seguro aprovado pela operação. Nunca imprima o valor.

## 2. Identificar o diretório persistente

Não confunda caminho interno do container com caminho do host.

Registre:

```text
Container:
Imagem/digest:
Volume ou bind mount:
Caminho no container:
Caminho no host:
appendonly:
appenddirname:
dir:
dbfilename:
Data/hora UTC:
Responsável:
```

Confirme os arquivos sem alterá-los:

```bash
# Exemplo: adapte o caminho somente após CONFIG GET e docker inspect
docker compose -f "$COMPOSE_FILE" exec -T "$REDIS_SERVICE" \
  sh -lc 'find /data -maxdepth 3 -type f -printf "%p %s bytes %TY-%Tm-%TdT%TH:%TM:%TS\n" | sort'
```

## 3. Backup consistente

### 3.1 Pré-condições

- [ ] serviço saudável ou estado da falha documentado;
- [ ] volume correto identificado;
- [ ] espaço livre suficiente;
- [ ] destino fora do volume ativo;
- [ ] horário e responsável registrados;
- [ ] segredo não será incluído no arquivo;
- [ ] nenhum restart será feito nesta etapa.

### 3.2 Solicitar persistência, quando seguro

Antes de solicitar `BGSAVE` ou `BGREWRITEAOF`, avaliar carga e impacto. Não executar automaticamente em incidente sem saber o estado do processo.

Comandos de inspeção:

```bash
docker compose -f "$COMPOSE_FILE" exec -T "$REDIS_SERVICE" \
  redis-cli --no-auth-warning INFO persistence
```

A operação pode optar por:

- copiar o volume parado;
- usar snapshot do provedor/volume;
- solicitar persistência controlada e copiar os arquivos;
- combinar backup lógico e físico.

A escolha deve ser registrada. Não há uma opção universal neste documento.

### 3.3 Copiar e gerar hash

Exemplo conceitual, após resolver o caminho real:

```bash
BACKUP_DIR="/caminho/seguro/redis-backup-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"

# Copie a árvore persistente correta para o destino seguro.
# Não use este placeholder sem substituir pelo caminho verificado.
cp -a /CAMINHO_REAL_DO_VOLUME/. "$BACKUP_DIR/"

find "$BACKUP_DIR" -type f -print0 \
  | sort -z \
  | xargs -0 sha256sum > "$BACKUP_DIR/SHA256SUMS"

sha256sum -c "$BACKUP_DIR/SHA256SUMS"
```

Registre também:

- tamanho total;
- proprietário/permissões;
- imagem/digest do Redis;
- saída sanitizada de `INFO persistence`;
- commit e versão do compose usados.

## 4. Inspeção não destrutiva do backup

Antes de considerar o backup restaurável:

- [ ] `SHA256SUMS` válido;
- [ ] arquivos AOF referenciados pelo manifest presentes;
- [ ] nenhum arquivo vazio inesperado;
- [ ] permissões compatíveis;
- [ ] versão de Redis identificada;
- [ ] estrutura preservada;
- [ ] backup armazenado fora do volume ativo.

Quando houver AOF multipart, valide o manifest e os arquivos associados. Não mova somente um `.rdb` para dentro do diretório ativo.

## 5. Restore — autorização obrigatória

### 5.1 Gate

Preencher antes:

```text
Incidente:
Perda comprovada por:
Último estado bom conhecido:
Backup escolhido:
SHA-256 verificado:
Data/hora do backup:
Janela aprovada:
Responsável pela execução:
Responsável pela decisão:
Plano de rollback:
Serviços que serão interrompidos:
Critério de sucesso:
Critério de aborto:
```

Sem todos os campos, **não executar**.

### 5.2 Preservar o estado quebrado

Antes de substituir arquivos, faça cópia forense do volume atual, mesmo que pareça corrompido.

```bash
BROKEN_COPY="/caminho/seguro/redis-pre-restore-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BROKEN_COPY"
cp -a /CAMINHO_REAL_DO_VOLUME/. "$BROKEN_COPY/"
find "$BROKEN_COPY" -type f -print0 | sort -z | xargs -0 sha256sum \
  > "$BROKEN_COPY/SHA256SUMS"
```

## 6. Restore — sequência controlada

Os comandos exatos dependem do compose, volume, usuário e AOF atuais. A sequência normativa é:

1. interromper produtores/consumidores que possam gravar no Redis;
2. parar o serviço Redis;
3. confirmar que o processo terminou;
4. preservar o volume atual;
5. limpar somente o diretório persistente confirmado;
6. copiar a árvore completa do backup;
7. corrigir proprietário e permissões conforme a imagem real;
8. validar AOF/manifest antes do startup;
9. iniciar apenas Redis;
10. validar logs, `PING`, persistência e dados;
11. iniciar consumidores gradualmente;
12. observar erros e duplicidade;
13. registrar resultado.

### Proibição

Não use `docker compose restart` como substituto para parada, cópia e validação. Restart rápido pode iniciar o Redis com conjunto parcial de arquivos e destruir evidência do incidente.

## 7. Validação do AOF antes do startup

Use a ferramenta correspondente à versão da imagem em execução. Não execute correção automática sem cópia preservada.

Exemplo conceitual:

```bash
# Resolver arquivos reais pelo manifest.
# Primeiro executar modo de verificação, sem --fix.
redis-check-aof /CAMINHO/ARQUIVO_AOF
```

Para AOF multipart, validar conforme o formato suportado pela versão instalada. Caso a ferramenta proponha truncamento/correção:

- pare;
- registre o diagnóstico;
- compare com a cópia preservada;
- obtenha autorização específica;
- nunca aplique `--fix` como etapa automática.

## 8. Startup e validação

Inicie somente Redis e acompanhe os logs:

```bash
docker compose -f "$COMPOSE_FILE" up -d "$REDIS_SERVICE"
docker compose -f "$COMPOSE_FILE" logs --no-color --tail=300 "$REDIS_SERVICE"
```

Verifique:

```bash
docker compose -f "$COMPOSE_FILE" exec -T "$REDIS_SERVICE" \
  redis-cli --no-auth-warning PING

docker compose -f "$COMPOSE_FILE" exec -T "$REDIS_SERVICE" \
  redis-cli --no-auth-warning INFO persistence

docker compose -f "$COMPOSE_FILE" exec -T "$REDIS_SERVICE" \
  redis-cli --no-auth-warning DBSIZE
```

`PONG` e ausência de erro de startup são necessários, mas não suficientes. Valide chaves/estruturas esperadas por meio de consultas sanitizadas ou fluxo funcional controlado.

## 9. Rollback

Acione rollback quando:

- Redis não inicia;
- AOF/manifest é recusado;
- contagem/estruturas são incompatíveis;
- aplicação apresenta erro novo;
- dados restaurados não correspondem ao ponto aprovado;
- há risco de escrita destrutiva.

Sequência:

1. parar consumidores;
2. parar Redis;
3. preservar o volume que falhou no restore;
4. restaurar a cópia pré-restore;
5. validar permissões e arquivos;
6. iniciar somente Redis;
7. validar;
8. reabrir consumidores gradualmente.

Rollback não significa “tentar outro backup” sem nova decisão.

## 10. Evidência final

Registrar sem segredos:

```text
Incidente:
Baseline de código:
Compose:
Imagem/digest Redis:
Volume:
Backup:
SHA-256:
AOF habilitado:
Validação pré-startup:
Horário de parada:
Horário de startup:
Resultado do PING:
Resumo de INFO persistence:
Validação funcional:
Consumidores reabertos:
Erros observados:
Rollback usado:
Responsáveis:
Próxima ação:
```

## 11. Revisão periódica

Um runbook não prova que o backup funciona. Programar separadamente:

- inspeção periódica de hashes;
- teste de restore em ambiente isolado;
- revisão de RPO/RTO;
- retenção e criptografia;
- capacidade de armazenamento;
- alerta de falha de persistência;
- atualização após mudança de imagem, compose ou volume.

O teste isolado nunca deve reutilizar o volume de produção.
