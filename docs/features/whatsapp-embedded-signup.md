# Onboarding — Conectar WhatsApp Business (Embedded Signup)

Vínculo entre um tenant do TroQuim e a conta WhatsApp Business dele, via **Facebook
Login for Business / WhatsApp Embedded Signup**.

Estado atual: **completo e ligado ao `/app`** (dono autenticado por sessão). O botão
"Conectar WhatsApp Business" vive na área privada do dono (ver `docs/ai/HANDOFF.md`,
Fase 3), não mais só no endpoint administrativo.

## 1. Divisão pública/secreta

Esta é a regra que organiza o resto do desenho.

| Valor | Onde vive | Vai ao navegador? |
| --- | --- | --- |
| `META_APP_ID` | env do backend, devolvido pelo `/start` | **sim** |
| `META_EMBEDDED_SIGNUP_CONFIG_ID` | env do backend, devolvido pelo `/start` | **sim** |
| `META_APP_SECRET` | env do backend | **nunca** |
| access token da conta | banco, cifrado | **nunca** |
| `state` (nonce) | gerado no backend | sim — é o que amarra a volta |

App ID e Config ID são identificadores públicos: sozinhos não autorizam nada, e o
diálogo da Meta não abre sem eles. O App Secret assina a troca do code por token, e por
isso essa troca acontece **exclusivamente no servidor**.

## 2. Fluxo

```
navegador                    backend                        Meta
    |                           |                             |
    |-- POST /start ----------->|                             |
    |<-- state, appId, configId-|  grava PENDENTE             |
    |                           |                             |
    |-- FB.login(config_id) ------------------------------->  |
    |<-- code + session data ------------------------------   |
    |                           |                             |
    |-- POST /finish ---------->|                             |
    |   (state, code, waba?)    |-- troca code por token ---> |
    |                           |<-- access_token ----------- |
    |                           |  cifra e grava CONECTADO    |
    |<-- status ----------------|                             |
```

O `state` é validado **antes** de qualquer chamada à Meta: um code avulso, sem início
correspondente naquele tenant, não gera sequer tráfego externo.

## 3. Endpoints — dois entrypoints, um serviço

`ConectarWhatsAppChannelService` não resolve tenant sozinho: quem chama prova
explicitamente de qual negócio e (quando houver) de qual dono está falando. Dois
controllers chamam o mesmo serviço, sem duplicar nenhuma regra:

| Método | Rota | Identidade | Devolve |
| --- | --- | --- | --- |
| POST | `/api/v1/admin/whatsapp/connections/start` | `ROLE_ADMIN` (Bearer) | `state`, `appId`, `configId`, `status` |
| POST | `/api/v1/admin/whatsapp/connections/finish` | `ROLE_ADMIN` | `status`, `wabaId`, `phoneNumberId` |
| GET | `/api/v1/admin/whatsapp/connections/current` | `ROLE_ADMIN` | `status`, `wabaId`, `phoneNumberId`, `conectado` |
| DELETE | `/api/v1/admin/whatsapp/connections/current` | `ROLE_ADMIN` | `204` |
| POST | `/api/v1/app/whatsapp/connection/start` | `ROLE_OWNER` (sessão) | `state`, `appId`, `configId`, `status` |
| POST | `/api/v1/app/whatsapp/connection/finish` | `ROLE_OWNER` | `status`, `wabaId`, `phoneNumberId` |
| DELETE | `/api/v1/app/whatsapp/connection` | `ROLE_OWNER` | `204` |

O caminho do dono (`/api/v1/app/**`) é o que a página `/app` usa de fato; o
administrativo continua existindo para operação/suporte sem exigir login de dono.

`finish` aceita `{state, code, wabaId?, phoneNumberId?}` no admin, e `{state, code}` no
caminho do dono (WABA/phone number vêm da própria sessão do Embedded Signup nesse
fluxo). Entrada inválida — corpo vazio, sem `state`, sem `code`, nonce desconhecido,
vencido, já consumido, de outro tenant ou de outro dono — responde **400 seco, sem
corpo**: distinguir os casos ajudaria quem estivesse sondando.

Desligado (`enabled=false`), todas as rotas de conexão respondem **503**. Sem sessão
válida, o caminho do dono responde **401/403** antes mesmo de checar a feature flag —
`SecurityConfigDefaultDeny` barra em `/api/v1/app/**` como um todo.

## 4. Garantias

- **Um tenant, uma conexão.** `business_id` é UNIQUE (V7). Reconectar reaproveita a
  linha; não acumula credencial antiga.
- **Nonce de uso único, vinculado a negócio E dono.** Consumido na finalização e
  descartado também no fracasso — repetir exige um novo início. Reiniciar invalida o
  nonce anterior no mesmo instante. Quando iniciado por um dono autenticado, o nonce
  também é amarrado ao `ownerUserId` (V10): finalizar com um dono diferente do que
  iniciou é recusado igual a um nonce desconhecido — mesmo estando no tenant certo.
- **"State assinado" — decisão de design.** O nonce é um valor aleatório de 256 bits
  persistido (não um JWT/HMAC stateless). Escolha deliberada: dá a mesma garantia de
  inforjabilidade de uma assinatura (256 bits de entropia são computacionalmente
  impossíveis de adivinhar), soma expiração + vínculo a negócio e dono, e ganha algo que
  um token assinado stateless não dá de graça — **revogação real no servidor** (o
  `revogar` apaga a linha; um JWT assinado continuaria "válido" até expirar, a menos que
  se mantivesse uma blocklist — reintroduzindo estado de qualquer forma). Também não
  expõe claims (tenant/dono) no próprio valor do token, ao contrário de um JWT decodificável
  no cliente.
- **Credencial cifrada em repouso.** AES-256-GCM, IV novo por escrita, `key_version`
  gravado para permitir rotação. A chave vem de `TROQUIM_CHANNEL_CRYPTO_KEY` e não tem
  default: uma chave padrão em código valeria o mesmo que não cifrar.
- **Nada conecta sozinho.** Sem `finish` explícito com nonce válido e code aceito pela
  Meta, nenhum número é vinculado. Não há caminho automático em dev.
- **Coexistência não é flag nossa.** Quando o número já pertence ao WhatsApp Business
  App, quem decide é a Meta, dentro do próprio Embedded Signup. O resultado chega pronto.
- **Revogação real.** `revogar(businessId)` remove a linha por inteiro — o canal volta a
  "não conectado", o mesmo estado de quem nunca conectou. Reconectar depois é um início
  limpo, sem herdar WABA/phone number antigos.
- **Segredo não vaza pelas saídas baratas.** `toString` de `ChannelConnection` e de
  `EncryptedCredential` omite credencial e nonce; nenhum DTO de resposta (JSON ou HTML)
  tem campo de token ou de App Secret; os logs registram só tenant, status e o tipo da
  exceção. Coberto ponta a ponta por `OwnerAppAccessTest` (HTML) e
  `WhatsAppChannelConnectionEndpointTest`/`ChannelCredentialSigiloTest` (JSON/logs).

## 5. Pré-requisitos da Meta ainda necessários

Nada disto está no código — é configuração no painel da Meta e no `.env` da máquina.

1. **App ID e App Secret** do app Meta → `META_APP_ID`, `META_APP_SECRET`.
2. **Config ID** do fluxo de Embedded Signup → `META_EMBEDDED_SIGNUP_CONFIG_ID`.
   O configurado hoje é `973012265764230`.
3. **Versão da Graph API** → `TROQUIM_WHATSAPP_GRAPH_API_VERSION` (sem default; deve ser
   explícita).
4. **Chave de cifragem** → `TROQUIM_CHANNEL_CRYPTO_KEY`, 32 bytes em base64. Gerar com
   `openssl rand -base64 32` e guardar no cofre, nunca no repositório.
5. **Ligar a feature** → `TROQUIM_EMBEDDED_SIGNUP_ENABLED=true`.
6. **Domínio permitido** no app da Meta (*App Domains* + *Valid OAuth Redirect URIs*),
   apontando para onde a página do botão for servida. Sem isso o diálogo recusa abrir.
7. **Permissões** `whatsapp_business_management` e `whatsapp_business_messaging`,
   aprovadas em App Review, e o app em **Live mode** — em Development só contas com
   papel no app conseguem concluir.
8. **Webhook `account_update`** assinado, para receber mudanças de estado da conta
   (o vínculo pode ser revogado do lado da Meta sem nos avisar por outro caminho).

Itens 6–8 dependem de decisões que ainda não foram tomadas (onde a página será servida,
qual número, qual WABA), então ficam registrados como pendência explícita.

## 6. A página (`/app`)

`GET /app` (protegida por `ROLE_OWNER`) renderiza o botão e o JS do Embedded Signup
inline — ver `OwnerAppPageRenderer`. O script só carrega o SDK da Meta
(`connect.facebook.net/pt_BR/sdk.js`) **depois** do clique, nunca na carga da página:

```js
fetch('/api/v1/app/whatsapp/connection/start', {method: 'POST'})
  .then(r => r.json())
  .then(inicio => {
    // SDK da Meta carregado aqui, so' agora
    FB.login(resposta => {
      fetch('/api/v1/app/whatsapp/connection/finish', {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({state: inicio.state, code: resposta.authResponse.code}),
      }).then(() => window.location.reload());
    }, {config_id: inicio.configId, response_type: 'code', override_default_response_type: true});
  });
```

Nenhuma regra de negócio no frontend: ele não decide status, não interpreta WABA e não
conhece o App Secret — só repassa `code` + `state` ao backend.

## 7. Multi-tenancy: resolvido via identidade do dono

`OwnerAuthService` resolve o tenant de qualquer rota de `/app` a partir da SESSÃO do
dono autenticado (ver Fase 2 em `docs/ai/HANDOFF.md`) — `PilotTenantProvider` não é mais
usado neste caminho. O isolamento é coberto ponta a ponta por `OwnerAppAccessTest`
(HTTP real: login → cookie → `/app`) e por unidade em `ChannelConnectionTenantIsolationTest`.
O caminho administrativo (Bearer) continua usando `TenantProvider`, correto para um
operador sem login de dono — os dois nunca se misturam porque o serviço não resolve
tenant sozinho em nenhum dos dois casos.
