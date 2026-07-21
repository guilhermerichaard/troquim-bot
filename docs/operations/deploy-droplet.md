# Deploy na DigitalOcean Droplet

## Pré-requisitos

- DigitalOcean droplet com Ubuntu 22.04+
- Docker e Docker Compose instalados
- Domínio ou IP público (porta 8080 liberada no firewall)
- Repositório clonado em `/opt/troquim`

## 1. Setup inicial na droplet (uma vez)

```bash
# Instalar Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# Re-login após o comando acima

# Instalar Docker Compose
sudo apt-get update
sudo apt-get install -y docker-compose-plugin

# Criar diretório do projeto
sudo mkdir -p /opt/troquim
sudo chown $USER:$USER /opt/troquim
```

## 2. Primeiro deploy

```bash
cd /opt/troquim

# Clonar o repositório
git clone https://github.com/guilhermerichaard/troquim-bot.git .

# Criar .env com as variáveis obrigatórias
cp .env.example .env
# EDITAR .env com:
#   TROQUIM_PILOT_BUSINESS_ID  (gerar com: uuidgen)
#   TROQUIM_ADMIN_API_KEY      (gerar: openssl rand -hex 32)
#   POSTGRES_PASSWORD           (gerar: openssl rand -hex 16)

# Subir tudo
docker compose -f docker-compose.droplet.yml up -d

# Verificar se está saudável
curl -fsS http://localhost:8080/actuator/health
```

## 3. Atualizar (novo deploy)

```bash
cd /opt/troquim

# Puxar versão mais recente
git pull origin main

# Rebuild e restart sem downtime no PostgreSQL
docker compose -f docker-compose.droplet.yml up -d --build --no-deps troquim-bot

# Verificar health
curl -fsS http://localhost:8080/actuator/health
```

## 4. Rollback

```bash
cd /opt/troquim

# Voltar para o commit anterior
git log --oneline -5
git checkout <COMMIT_HASH_ANTERIOR>

# Rebuild e restart
docker compose -f docker-compose.droplet.yml up -d --build --no-deps troquim-bot

# Verificar health
curl -fsS http://localhost:8080/actuator/health
```

## 5. Ativar WhatsApp Cloud API

No `.env`, alterar:

```env
TROQUIM_WHATSAPP_CLOUD_ENABLED=true
TROQUIM_WHATSAPP_VERIFY_TOKEN=<seu-token-aleatorio>
TROQUIM_WHATSAPP_APP_SECRET=<app-secret-do-meta>
TROQUIM_WHATSAPP_ACCESS_TOKEN=<access-token>
TROQUIM_WHATSAPP_PHONE_NUMBER_ID=<id-do-telefone>
TROQUIM_WHATSAPP_WABA_ID=<waba-id>
TROQUIM_WHATSAPP_GRAPH_API_VERSION=v22.0
# Opcional — sobrescreve a base URL da Graph API (padrão: https://graph.facebook.com)
# TROQUIM_WHATSAPP_GRAPH_API_BASE_URL=https://graph.facebook.com
```

Depois restart:

```bash
docker compose -f docker-compose.droplet.yml up -d --no-deps troquim-bot
```

No Meta Developers, registrar webhook:

```
URL: https://SEU-DOMINIO/webhook/whatsapp/cloud
Verify Token: o mesmo que TROQUIM_WHATSAPP_VERIFY_TOKEN
```

## 6. Comandos úteis

```bash
# Logs
docker compose -f docker-compose.droplet.yml logs -f troquim-bot

# Testar conversa (requer TROQUIM_ADMIN_API_KEY)
curl -X POST http://localhost:8080/dev/conversation \
  -H "Authorization: Bearer SEU_ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"number":"5511999990000","message":"oi"}'

# Backup do banco
docker exec troquim-postgres pg_dump -U troquim troquim > backup_$(date +%Y%m%d).sql

# Restaurar backup
cat backup.sql | docker exec -i troquim-postgres psql -U troquim troquim

# Parar tudo (dados preservados no volume)
docker compose -f docker-compose.droplet.yml down

# Parar e apagar dados (CUIDADO)
docker compose -f docker-compose.droplet.yml down -v
```

## 7. Arquivos no servidor

```
/opt/troquim/
├── .env                    # Variáveis de ambiente (NUNCA commitar)
├── docker-compose.droplet.yml  # Orquestração completa
├── Dockerfile              # Build da imagem
├── src/                    # Código fonte
└── ...                     # Demais arquivos do repositório