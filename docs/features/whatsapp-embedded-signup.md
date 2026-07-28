# Onboarding — Conectar WhatsApp Business (Embedded Signup)

Vínculo entre um tenant do TroQuim e a conta WhatsApp Business dele, via **Facebook
Login for Business / WhatsApp Embedded Signup**.

Estado atual: **backend pronto (Fases 1–2), sem UI**. Não existe superfície autenticada
no produto — a landing é pública e o admin autentica por Bearer em header. A Fase 3 (o
botão) depende de decidir onde vive o painel; ver §6.

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

## 3. Endpoints (todos sob `ROLE_ADMIN`)

| Método | Rota | Devolve |
| --- | --- | --- |
| POST | `/api/v1/admin/whatsapp/connections/start` | `state`, `appId`, `configId`, `status` |
| POST | `/api/v1/admin/whatsapp/connections/finish` | `status`, `wabaId`, `phoneNumberId` |
| GET | `/api/v1/admin/whatsapp/connections/current` | `status`, `wabaId`, `phoneNumberId`, `conectado` |

`finish` aceita `{state, code, wabaId?, phoneNumberId?}`. Entrada inválida — corpo
vazio, sem `state`, sem `code`, nonce desconhecido, vencido, já consumido ou de outro
tenant — responde **400 seco, sem corpo**: distinguir os casos ajudaria quem estivesse
sondando.

Desligado (`enabled=false`), as três rotas respondem **503**.

## 4. Garantias

- **Um tenant, uma conexão.** `business_id` é UNIQUE (V7). Reconectar reaproveita a
  linha; não acumula credencial antiga.
- **Nonce de uso único.** Consumido na finalização e descartado também no fracasso —
  repetir exige um novo início. Reiniciar invalida o nonce anterior no mesmo instante.
- **Credencial cifrada em repouso.** AES-256-GCM, IV novo por escrita, `key_version`
  gravado para permitir rotação. A chave vem de `TROQUIM_CHANNEL_CRYPTO_KEY` e não tem
  default: uma chave padrão em código valeria o mesmo que não cifrar.
- **Nada conecta sozinho.** Sem `finish` explícito com nonce válido e code aceito pela
  Meta, nenhum número é vinculado. Não há caminho automático em dev.
- **Coexistência não é flag nossa.** Quando o número já pertence ao WhatsApp Business
  App, quem decide é a Meta, dentro do próprio Embedded Signup. O resultado chega pronto.
- **Segredo não vaza pelas saídas baratas.** `toString` de `ChannelConnection` e de
  `EncryptedCredential` omite credencial e nonce; nenhum DTO de resposta tem campo de
  token; os logs registram só tenant, status e o tipo da exceção.

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

## 6. O que falta (Fase 3)

O botão precisa de uma superfície **autenticada**: quem clica tem de estar associado a um
tenant. Hoje não existe login em lugar nenhum do produto — a landing é pública e o admin
usa Bearer em header, que navegador não envia sozinho num clique.

Quando houver essa decisão, a página faz:

```js
FB.login(resposta => enviarAoBackend(resposta.authResponse.code, state), {
  config_id: configId,            // veio do /start
  response_type: 'code',
  override_default_response_type: true,
});
```

e envia ao backend **apenas** `code` + `state`. Nenhuma regra de negócio no frontend:
ele não decide status, não interpreta WABA e não conhece o App Secret.

## 7. Multi-tenancy: limite atual

`PilotTenantProvider` devolve sempre o mesmo `TROQUIM_PILOT_BUSINESS_ID`. Os dados já
são escopados por `business_id` e o isolamento está coberto por teste
(`ChannelConnectionTenantIsolationTest`), mas enquanto não houver autenticação que
resolva o tenant, na prática existe um só. O módulo está pronto para o dia em que houver
mais — não é preciso remodelar nada, só trocar o `TenantProvider`.
