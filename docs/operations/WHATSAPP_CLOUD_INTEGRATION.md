# Runbook — Integração oficial WhatsApp Cloud API (Meta)

Fundação da integração com a **WhatsApp Cloud API** da Meta para o **TroQuim**
(copiloto empresarial de atendimento/agenda — não é assistente de IA de propósito
geral). Convive temporariamente com a integração anterior (Evolution) e permite
cutover controlado por provider.

> **Sem valores reais neste documento.** Todos os exemplos usam placeholders.

## 1. Arquitetura (resumo)

```
Interface        WhatsAppCloudWebhookController  (GET verify + POST raw bytes)
   │
Application      InboundMessageIngestionService  (orquestra: assina→parse→idempotência→conversa→outbound)
   │             InboundReceiptProcessor         (@Transactional: claim + Conversation)
   │             ports: InboundMessageParser, WebhookSignatureVerifier, SubscriptionVerifier,
   │                    OutboundMessageGateway, InboundReceiptStore  (provider-neutral)
   │
Domain           (intocado — nenhuma dependência da Meta)
   │
Infrastructure   WhatsAppCloud{SignatureVerifier,SubscriptionVerifier,MessageParser,OutboundGateway},
                 JpaInboundReceiptStore + tabela inbound_message_receipts (V3)
```

A resposta ao cliente vem do **fluxo real de Conversation** já existente
(`ConversationApplicationService.processarMensagem`). A Meta é apenas mais um adapter.

## 2. Callback URL e verificação (GET)

- **Callback URL** (registrar no painel Meta):
  `https://<seu-dominio>/webhook/whatsapp/cloud`
- A Meta faz um **GET** com os parâmetros:
  - `hub.mode=subscribe`
  - `hub.verify_token=<o verify token que você configurou>`
  - `hub.challenge=<número aleatório>`
- Comportamento do backend:
  - `mode=subscribe` + `verify_token` correto → responde `hub.challenge` como texto (200);
  - token incorreto → **403**;
  - parâmetros ausentes/`mode` inválido → **400**;
  - o verify token **nunca** é registrado em log.

## 3. Assinatura dos eventos (POST)

- A Meta envia **POST** para a mesma URL com header `X-Hub-Signature-256: sha256=<hex>`.
- O backend lê o **corpo bruto (byte[]) antes de desserializar**, calcula
  `HMAC-SHA256(app_secret, bytes_exatos_do_corpo)` e compara em **tempo constante**.
- Assinatura ausente/ inválida → **401**. Corpo alterado invalida a assinatura.
- A rota POST é **pública no Spring Security**, mas protegida **criptograficamente**
  pela assinatura. App Secret, assinatura e payload pessoal **não** são logados.

## 4. Variáveis de ambiente

| Variável | Propriedade tipada | Obs |
| --- | --- | --- |
| `TROQUIM_WHATSAPP_CLOUD_ENABLED` | `...cloud.enabled` | feature flag (default false) |
| `TROQUIM_WHATSAPP_VERIFY_TOKEN` | `...cloud.verify-token` | você escolhe (alta entropia) |
| `TROQUIM_WHATSAPP_APP_SECRET` | `...cloud.app-secret` | Meta App > Settings > Basic |
| `TROQUIM_WHATSAPP_ACCESS_TOKEN` | `...cloud.access-token` | token da Graph API (Bearer) |
| `TROQUIM_WHATSAPP_PHONE_NUMBER_ID` | `...cloud.phone-number-id` | WhatsApp > API Setup |
| `TROQUIM_WHATSAPP_WABA_ID` | `...cloud.waba-id` | WhatsApp Business Account ID |
| `TROQUIM_WHATSAPP_GRAPH_API_VERSION` | `...cloud.graph-api-version` | **explícita**, sem default |
| `TROQUIM_WHATSAPP_GRAPH_API_BASE_URL` | `...cloud.base-url` | default `https://graph.facebook.com` |

Prefixo das propriedades: `troquim.integrations.whatsapp.cloud`. Ver
[`.env.example`](../../.env.example) (apenas nomes/placeholders — **nunca** segredos).

**Regras de segurança da config:**
- Segredos **só** por variável de ambiente; nunca em `application*.properties` nem no Git.
- `graph-api-version` é **explícita** (não presumir a versão atual da Graph API).
- Em **azure** com `enabled=true`, faltar qualquer credencial obrigatória **falha o
  startup** (fail-fast). Whitespace ao redor de um segredo também **falha claramente**
  (sem trim silencioso). Com `enabled=false`, a app **inicia sem credenciais**.

## 5. Como gerar o verify token

O verify token é um segredo **que você escolhe** (não vem da Meta). Gere alta entropia:

```
# qualquer um serve; NÃO comite o valor:
openssl rand -hex 32
# ou
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

Coloque o mesmo valor em `TROQUIM_WHATSAPP_VERIFY_TOKEN` e no campo "Verify token" do
painel Meta ao registrar a callback URL.

## 6. App Secret e access token (sem colocar no Git)

- **App Secret:** Meta App Dashboard → App settings → **Basic** → *App Secret*.
  Defina em `TROQUIM_WHATSAPP_APP_SECRET` (ambiente/secret manager). É a chave do HMAC.
- **Access token:** WhatsApp → **API Setup** (token temporário de 24h para testes) ou
  crie um **System User** com token permanente (produção). Defina em
  `TROQUIM_WHATSAPP_ACCESS_TOKEN`. Nunca versione.

No deploy da Droplet, injete via ambiente do container (ver
[BACKEND_RELEASE_DEPLOY.md](BACKEND_RELEASE_DEPLOY.md)); não escreva em arquivo versionado.

## 7. Assinar/testar um payload localmente (sem dados reais)

O HMAC é sobre os **bytes exatos** do corpo. Exemplo com `curl` + `openssl`:

```bash
BODY='{"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages","value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"PNID"},"messages":[{"id":"wamid.LOCALTEST","from":"5511999990000","timestamp":"1700000000","type":"text","text":{"body":"oi"}}]}}]}]}'
APP_SECRET='seu-app-secret-local'

SIG="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$APP_SECRET" -hex | sed 's/^.*= //')"

curl -sS -X POST http://localhost:8080/webhook/whatsapp/cloud \
  -H "Content-Type: application/json" \
  -H "X-Hub-Signature-256: $SIG" \
  --data "$BODY" -w '\nHTTP %{http_code}\n'
```

Use `printf '%s'` (sem newline) para que os bytes assinados sejam idênticos aos enviados.
Rode com `TROQUIM_WHATSAPP_CLOUD_ENABLED=true` e um `app-secret` local fictício.

## 8. Registrar no painel Meta e assinar o campo `messages` do WABA

1. Meta App Dashboard → **WhatsApp** → **Configuration**.
2. Em **Webhook**, clique **Edit**: informe a **Callback URL**
   (`https://<dominio>/webhook/whatsapp/cloud`) e o **Verify token**. A Meta faz o GET
   de verificação → deve retornar 200 com o challenge.
3. Em **Webhook fields**, clique **Manage** e **assine (Subscribe)** o campo **`messages`**
   (é o que entrega mensagens recebidas e status). Sem isso, nenhum POST chega.

## 9. Validar o webhook sem dados reais

- GET de verificação: bata na callback URL com `hub.mode=subscribe` + verify token
  correto → deve devolver o challenge; token errado → 403.
- POST: use o script da §7 com um `app-secret` fictício local e `enabled=true`.
- Cenários cobertos por testes automatizados (ver §12): assinatura válida/ inválida/
  ausente/ corpo alterado, texto/status-only/vazio/múltiplas mensagens/tipo não suportado,
  idempotência durável, e o cliente outbound contra servidor fake.

## 10. Smoke test (com número oficial de teste)

1. Configure as variáveis (§4) com credenciais **de teste** da Meta e
   `TROQUIM_WHATSAPP_CLOUD_ENABLED=true`.
2. Registre a callback + assine `messages` (§8).
3. Do número de teste do painel (WhatsApp > API Setup), envie "oi" para o número da conta.
4. Esperado: 200 no webhook; o TroQuim responde com o menu/fluxo de atendimento.
5. Reenvie o **mesmo** evento (a Meta reentrega em falhas): não deve haver resposta
   duplicada nem ação de negócio duplicada (idempotência por message ID).

## 11. Rotação de access token / App Secret

- **Access token:** gere um novo (System User) na Meta, atualize
  `TROQUIM_WHATSAPP_ACCESS_TOKEN` no ambiente e recrie apenas o `troquim-bot`
  (`docker compose ... up -d --no-deps --force-recreate troquim-bot`). Sem downtime dos
  outros serviços. Revogue o token antigo depois de validar.
- **App Secret:** rotacionar o App Secret **invalida assinaturas** até o novo valor
  estar ativo em ambos os lados. Faça em janela: gere o novo secret na Meta, atualize
  `TROQUIM_WHATSAPP_APP_SECRET` e recrie o serviço. Durante a troca, POSTs assinados com
  o secret antigo retornarão 401 (a Meta reentrega); o backfill ocorre ao reprocessar.

## 12. Desligar por feature flag

`TROQUIM_WHATSAPP_CLOUD_ENABLED=false` (ou variável ausente) desabilita **todos** os
beans da integração (controller, ingestion, gateway, receipt store, validação). A app
inicia sem credenciais. As rotas `/webhook/whatsapp/cloud` deixam de ter handler.

## 13. Coexistência temporária com Evolution

- Os webhooks anteriores da Evolution permanecem **intactos**:
  `POST /webhook/whatsapp` e `POST /webhook/whatsapp/messages-upsert`.
- A rota Cloud é separada (`/webhook/whatsapp/cloud`), permitindo os dois canais no ar.
- Ambos convergem para o **mesmo** fluxo de Conversation; a idempotência do Cloud é por
  `(provider, external_message_id)` na tabela `inbound_message_receipts` — independente da
  dedup em memória da Evolution.

## 14. Plano de cutover e rollback

**Cutover (Evolution → Cloud):**
1. Suba o backend com `enabled=true` e credenciais de teste; valide GET + POST (§9/§10).
2. Aponte o número oficial/produção para a Cloud API (painel Meta) e assine `messages`.
3. Monitore: 200 nos webhooks, respostas corretas, sem duplicidade.
4. Quando estável, descomissione o canal Evolution (fora do escopo desta branch — a
   Evolution **não** é removida aqui).

**Rollback:**
1. `TROQUIM_WHATSAPP_CLOUD_ENABLED=false` e recrie o `troquim-bot` → desliga a Cloud.
2. Reative o fluxo Evolution (que seguiu no ar) apontando o número de volta, se aplicável.
3. A tabela `inbound_message_receipts` permanece (não guarda dado pessoal); pode ser
   truncada em manutenção se desejado. Nenhuma migração de negócio é revertida.

## 15. Idempotência e retry de outbound

O receipt (`inbound_message_receipts`) tem estado durável **PENDING → SENT** e guarda a
`response_text`:

- 1ª entrega: o negócio é processado e a **resposta é persistida** (PENDING) atomicamente
  com o avanço da conversa; o envio outbound ocorre em seguida.
- Se o outbound **falha**, o receipt fica **PENDING** com a resposta guardada — a resposta
  **não se perde**.
- **Re-entrega** da mesma `message_id`:
  - se **PENDING** → **não** chama Conversation de novo; apenas **reenvia** a resposta
    persistida; ao ter sucesso → **SENT**;
  - se **SENT** → responde 200 e não faz nada.

Assim: nunca há reprocessamento de negócio (sem duplicar Appointment/Reservation) nem perda
definitiva da resposta. Sem fila distribuída. (Nota: re-entregas concorrentes de um PENDING
podem, em corrida rara, reenviar a resposta mais de uma vez — o que **não** duplica ação de
negócio; o exactly-once do outbound sob concorrência fica para evolução futura.)

## 16. Validação do `phone_number_id` (piloto)

Cada `value.metadata.phone_number_id` do payload é comparado ao `phone-number-id`
**configurado**. Se divergir, as mensagens daquele evento são **ignoradas** (não chamam
Conversation nem outbound) e o webhook responde 200. Isto é escopo de **piloto (canal
único)** — **não** é multi-tenancy de canais.

> **Futuro:** o par **(provider, phone_number_id)** resolverá o **BusinessId** do tenant,
> habilitando múltiplos números/negócios. Enquanto isso, apenas o número piloto configurado
> é atendido.

## 17. Escopo do produto

O canal serve casos **empresariais específicos** (atendimento, serviços, disponibilidade,
reservas, agendamentos, cancelamentos, informações do negócio). A IA interpreta intenção;
**o Domain decide o negócio**. Não é "pergunte qualquer coisa" nem assistente geral.
