# WhatsApp Flow de agendamento

Agenda rica no WhatsApp: a conversa oferece um botão **"Abrir agenda"**, o cliente escolhe
serviço, profissional, data e horário em telas nativas, e o agendamento é confirmado pelo
mesmo domínio que atende o bot textual.

Rota do Data Endpoint: `POST /api/v1/whatsapp/flows`
(pública: `https://api.troquim.app/api/v1/whatsapp/flows`).

---

## 1. Arquitetura

| Camada | Onde | Responsabilidade |
|---|---|---|
| Interface | `whatsapp/flow/api` | decifra → delega → cifra → HTTP. Nada de negócio. |
| Application | `whatsapp/flow/application` | coordenador, handlers por ação, presenter, validação, sessões |
| Application (compartilhada) | `application/booking`, `application/availability`, `application/messaging` | confirmação, disponibilidade, portas de saída |
| Domain | `appointment`, `reservation`, `availability`, `customer` | conflito, agregados, identidade |
| Infrastructure | `whatsapp/flow/infrastructure`, `infrastructure/whatsappcloud` | criptografia, chave, JPA, Graph API |

Regras de dependência que sustentam isso:

- O **Flow é Interface**: não calcula slots, não decide compatibilidade, não confirma nada.
- A **Meta Cloud API é Infrastructure**: o adaptador não sabe o que é horário ou conflito.
- A conversa **não conhece** WhatsApp Flow. Ela depende da capacidade
  `AberturaDeAgenda` (`application/booking`), implementada do outro lado da fronteira.

## 2. Sequência ponta a ponta

```
cliente: "1" (agendar)
  └─ StrictMvpMenuService
       └─ AberturaDeAgenda.abrirPara(telefone)          [capacidade opcional]
            └─ AbrirAgendaPorFlowService
                 1. FlowSessionStore.abrir(...)          → sessão ABERTA persistida
                 2. OutboundFlowGateway.sendFlow(...)    → porta
                      └─ WhatsAppCloudFlowGateway        → Graph API (interactive/flow)
                 3. falhou? → sessionStore.invalidar()   → compensação
cliente toca em "Abrir agenda"
  └─ Meta → POST /api/v1/whatsapp/flows (cifrado)
       └─ WhatsAppFlowController → FlowExchangeService → handler da ação
            SERVICO → PROFISSIONAL → DATA → HORARIO → DADOS → CONFIRMACAO
            (cada passo revalida TUDO contra AvailabilityApplicationService)
       └─ CONFIRM → BookingApplicationService.confirmarEm(...)
            └─ Reservation → Appointment → Customer (uma transação)
       └─ sessão CONCLUIDA + tela SUCESSO
```

A ordem em (1)(2)(3) é deliberada: a sessão existe **antes** do envio, senão um toque
imediato no botão encontraria um token desconhecido; e um envio que falha **invalida** a
sessão, senão sobraria um token válido que ninguém recebeu.

## 3. Ciclo de vida da sessão

`whatsapp_flow_sessions` amarra o `flow_token` ao cliente e ao tenant. Isso existe porque
o payload de `data_exchange` **não carrega telefone nem businessId** — aceitá-los do
cliente permitiria agendar em nome de terceiros ou atravessar tenants.

| Estado | Significado | Aceita troca? |
|---|---|---|
| `ABERTA` | válida e em uso | sim |
| `CONCLUIDA` | agendamento confirmado | sim, **só** para devolver o mesmo desfecho (idempotência) |
| `EXPIRADA` | passou de `expira_em` | não |
| `INVALIDADA` | envio falhou; token nunca chegou ao cliente | não |

- **TTL configurável**: `TROQUIM_WHATSAPP_FLOW_SESSAO_TTL_MIN` (padrão 30 min).
- **Vencimento é avaliado na LEITURA** (`FlowSession.utilizavel`), não por rotina agendada:
  depender de limpeza periódica deixaria uma janela em que a sessão vencida ainda funciona.
- **A sessão concluída não é apagada** — é ela que reconhece a reentrega e evita o segundo
  agendamento.
- Token desconhecido, vencido e invalidado falham **igual** (427, sem pista de qual caso),
  para não ajudar quem sonda tokens.

## 4. Confirmação: conflito ≠ falha técnica

`BookingResult` distingue quatro desfechos: `CONFIRMADO`, `INDISPONIVEL` (conflito real),
`INVALIDO` e `FALHA_TECNICA`.

A distinção é feita **na fonte**, por tipo de exceção — nunca por texto:
`ReservationApplicationService` e `AppointmentApplicationService` lançam
`HorarioIndisponivelException` no conflito; qualquer outra `RuntimeException` é falha
técnica.

Por que importa: antes, uma falha de banco chegava ao cliente como "horário ocupado" —
um diagnóstico de agenda que o sistema não tinha como sustentar. Agora:

| Desfecho | Tela do Flow | Conversa textual | Mensagem |
|---|---|---|---|
| conflito (`INDISPONIVEL`) | volta para `HORARIO` | pede outro horário | "Esse horário não está mais disponível. Escolha outro, por favor." |
| falha técnica | fica em `CONFIRMACAO`, com a MESMA escolha | mantém o draft | "Não conseguimos concluir seu agendamento agora. Tente novamente em instantes." |

**A mensagem de falha técnica é deliberadamente neutra.** Numa falha de persistência não
sabemos se a escrita chegou a ocorrer, se o rollback se aplicou, nem se o horário segue
livre — quem falhou foi exatamente o mecanismo que nos daria essa resposta. Por isso ela
**não** afirma que o horário continua disponível, que nada foi criado, nem que repetir dará
no mesmo. Diz só o que é verificável: não concluímos, e dá para tentar de novo.

Repetir é seguro por causa da **idempotência**, não por a agenda estar inalterada: por
`flow_token` no Flow e por slot (mesmo cliente, mesma data, mesmo horário) no domínio. Um
retry após falha transitória confirma sem duplicar — coberto por teste nos dois canais.

O texto é único (`BookingResult.MENSAGEM_FALHA_TECNICA`) e compartilhado pelos dois
consumidores: duas constantes iguais é como elas divergem.

**Limite transacional.** `confirmarEm` é `@Transactional` e engloba **quatro** escritas:
reivindicação do comando, Reservation, Appointment e Customer. Falha técnica **sempre
lança** (`BookingPersistenceException`) — nunca vira retorno —, porque só assim o rollback
apaga a reivindicação junto. Retornar normalmente comitaria uma chave reivindicada e sem
desfecho, e todo retry dela ficaria preso.

**Em nenhum caminho a tela de SUCESSO aparece sem persistência concluída.**

## 4.1 Idempotência por comando

Antes, o Flow usava `flow_token` como se fosse chave de idempotência. Não é: o token
identifica a **sessão**, e uma sessão pode legitimamente tentar confirmar escolhas
diferentes — quem voltasse e escolhesse outro horário recebia de volta o agendamento
anterior, em silêncio. Pior, o recibo era gravado numa transação **separada**, depois do
commit do agendamento: se essa segunda escrita falhasse, o retry só não duplicava porque
um laço varria os agendamentos do cliente. Isso é unicidade de domínio, não idempotência.

**Chave do comando** (`BookingCommandKey`):

```
command_key = <base> : SHA-256( v1 | businessId | telefone | serviceId | professionalId | data | horario )
```

- A **base** é o `flow_token` (Flow) — contexto confiável do servidor, nunca do payload.
- O payload canônico usa **IDs estáveis e valores normalizados, em ordem fixa**. Sem nome
  visível (muda sem mudar o comando) e sem serialização de JSON (a ordem dos campos não é
  garantida pelo cliente).
- Consequência: "mesmo token, dados diferentes" é um **comando diferente**.
- `request_fingerprint` é guardado à parte e **conferido** num acerto de chave; divergência
  falha alto em vez de devolver o resultado de outro comando.

**Protocolo claim-then-complete**, tudo na transação do caso de uso:

| Momento | O que acontece |
|---|---|
| 1ª escrita da transação | `INSERT ... ON CONFLICT DO NOTHING` reivindica a chave |
| 2ª execução simultânea | o PostgreSQL **bloqueia** no índice único até a 1ª terminar |
| após commit da 1ª | a 2ª destrava com 0 linhas, relê e devolve o desfecho — sem escrever |
| após rollback da 1ª | a linha some, a 2ª destrava e o `INSERT` dela **sucede** |
| fim do caso de uso | `UPDATE` grava `appointment_id` + desfecho, no mesmo commit |

O conflito é tratado como **dado** (linhas afetadas), não como exceção: no PostgreSQL uma
violação de constraint **aborta** a transação, e continuar nela depois do `catch` daria
`current transaction is aborted`. Por isso não há `catch (DataIntegrityViolationException)`
em volta da reivindicação.

### Regra do MVP: um `flow_token`, no máximo um agendamento

A chave do comando é `base:fingerprint`, mas a **base** (`command_base`, o `flow_token`)
é guardada isolada e tem regra própria:

| Situação | Resultado |
|---|---|
| mesmo token, **mesmos** dados | `CONFIRMADO` — devolve o mesmo `appointment_id`, sem duplicar |
| mesmo token, **dados diferentes** após confirmar | `SESSAO_JA_CONFIRMADA` — recusa, não cria nada, orienta a abrir novo Flow |
| mesmo token após **conflito** de agenda | segue valendo — perder um horário para outra pessoa não gasta o Flow |

Garantida em dois níveis, ambos na transação do agendamento:

1. **`SELECT` antes da reivindicação** — resposta limpa ao cliente. Vem antes de propósito:
   se fosse depois, a recusa deixaria para trás uma linha reivindicada e sem desfecho, e
   aquela chave ficaria presa para sempre.
2. **`UNIQUE INDEX uq_booking_idempotency_base_confirmada (command_base) WHERE outcome_status = 'CONFIRMADO'`**
   — a rede para a corrida em que dois comandos da mesma base passam pelo `SELECT` ao mesmo
   tempo. A perdedora aborta e sobe como falha técnica, sem tentar se recuperar dentro de
   uma transação já invalidada.

**Não depende da `FlowSession`.** A sessão é atualizada em transação separada, depois do
commit do agendamento; se aquela escrita falhasse, a sessão diria "não confirmada" e o
mesmo token concluiria um segundo agendamento. A regra vive junto com o dado que protege.

**Invariante de slot — agora no banco.** O teste de concorrência contra PostgreSQL real
expôs um defeito pré-existente: dois comandos *diferentes* disputando o mesmo horário
liam "sem conflito" e ambos gravavam. A V5 adiciona
`UNIQUE INDEX uq_appointments_slot_ativo (professional_id, date, start_time) WHERE status <> 'CANCELADO'`.
A transação perdedora é abortada e sobe como falha técnica — ela **não** tenta se
recuperar dentro de uma transação já invalidada. Isto é invariante de domínio; convive
com a idempotência e protege coisa diferente.

⚠️ **Risco de aplicação:** se a base já tiver agendamentos ativos duplicados no mesmo
slot, a criação do índice falha e a migration não sobe. A V5 traz a query de verificação
no comentário.

## 5. Fonte única de disponibilidade

Havia duas — na verdade três instâncias:

| Onde | O que fazia |
|---|---|
| `ScheduleService` | gabarito semanal em memória (seg–sáb, 09–18h; sáb até 13h) |
| `AvailabilityApplicationService.consultarDisponibilidade` | devolvia o gabarito **cru** → a conversa oferecia horários **já agendados** |
| `FlowAvailabilityQuery` | cruzava gabarito × Appointments por conta própria |

Pior: `AvailabilityApplicationService` tinha três construtores e **nenhum `@Autowired``,
então o Spring escolhia o vazio e o serviço criava um `ScheduleService` **próprio** —
diferente do bean usado pelo resto do sistema.

**Fonte oficial agora: `AvailabilityApplicationService`.** O gabarito é o conjunto de
partida; os Appointments ativos são o filtro autoritativo; horários que já passaram hoje
são descartados. Conversa e Flow chamam os mesmos métodos:

```java
List<LocalTime> horariosLivres(LocalDate, ProfessionalId)
List<LocalDate> datasSemVaga(LocalDate de, LocalDate ate, ProfessionalId)
boolean         estaLivre(LocalDate, LocalTime, ProfessionalId)
```

`FlowAvailabilityQuery` virou delegação pura — se voltar a conter regra, a duplicação
voltou. `InMemoryAvailabilityRepository` passou a ser bean (`@Repository`) porque a
injeção agora é explícita.

## 6. Catálogo de Service e Professional — ainda placeholder

**O domínio não tem catálogo persistido.** Não há seed de `Service` nem de `Professional`;
o menu textual usa as mesmas cinco chaves fixas.

`FlowCatalogProvider` concentra essa limitação num ponto só, com as **mesmas chaves
canônicas** do booking (`unha`, `cabelo`, `sobrancelha`, `cilios`, `pe e mao`),
profissional único (`professional:troquim-mvp-default`) e duração de 1h — a duração que o
domínio realmente aplica. **Preço não é exposto porque não existe no domínio**; inventar um
valor na tela seria criar informação que o sistema não tem.

Isto **é** dado placeholder e vai para produção como tal se o piloto subir assim. Menor
caminho arquitetural: repositório de `Service`/`Professional` por tenant + seed do salão
piloto, trocando só a implementação deste provider — nenhum handler conhece a lista.

## 7. Segurança

- **Autenticação é a criptografia.** RSA-OAEP (SHA-256, MGF1-SHA256) para a chave AES
  efêmera; AES-128-GCM para o corpo; resposta cifrada com a mesma chave e o **IV invertido
  bit a bit**. A rota é `permitAll` só por isso, e só para `POST` na rota exata —
  `SecurityConfigDefaultDeny` mantém `anyRequest().denyAll()`.
- **Corpo limitado a 64 KB**, cortado antes de qualquer parsing ou operação criptográfica.
- **Chave privada nunca versionada**: `WHATSAPP_FLOW_PRIVATE_KEY` (PEM PKCS#8, `\n`
  escapado), opcional `WHATSAPP_FLOW_PRIVATE_KEY_PASSWORD`. Ligado sem chave legível, a
  aplicação **não sobe** (fail-fast).
- **Telefone e tenant nunca vêm do payload** — sempre da sessão e do `TenantProvider`.
- **Logs**: nem payload decifrado, nem chave AES/RSA, nem IV, nem telefone, nem
  `flow_token`, nem access token da Meta. Só tipo de falha e estado operacional. Coberto
  por teste.
- **Payload inválido tem tratamento uniforme**: 400 para envelope/corpo malformado,
  421 só para falha de chave, 427 só para sessão.

### Códigos do protocolo

| Código | Quando | Efeito |
|---|---|---|
| 421 | falha ao decifrar a chave AES | a Meta refaz o handshake de chave pública |
| 427 | `flow_token` desconhecido, vencido ou invalidado | o Flow encerra |
| 400 | envelope/corpo malformado ou grande demais | — |
| 500 | falha inesperada | — |

## 8. Configuração

```bash
# Criptografia
TROQUIM_WHATSAPP_FLOW_ENABLED=false     # feature flag; desligado, nenhum bean é criado
WHATSAPP_FLOW_PRIVATE_KEY=              # PEM PKCS#8
WHATSAPP_FLOW_PRIVATE_KEY_PASSWORD=     # só se o PEM for ENCRYPTED PRIVATE KEY

# Envio
TROQUIM_WHATSAPP_FLOW_ID=               # id do Flow publicado; sem ele → fallback textual
TROQUIM_WHATSAPP_FLOW_NAME=             # alternativa ao id
TROQUIM_WHATSAPP_FLOW_CTA=Abrir agenda  # máx. 20 caracteres (limite da Meta)
TROQUIM_WHATSAPP_FLOW_DRAFT=false       # true = modo draft, só teste interno

# Sessão e telas
TROQUIM_WHATSAPP_FLOW_SESSAO_TTL_MIN=30
TROQUIM_WHATSAPP_FLOW_JANELA_DIAS=30
```

Gerar o par (a pública vai para a Meta, a privada nunca sai daqui):

```bash
openssl genrsa -out flow-private.pem 2048
openssl pkcs8 -topk8 -inform PEM -in flow-private.pem -out flow-private-pkcs8.pem
openssl rsa -in flow-private.pem -pubout -out flow-public.pem
```

Sem `flow-id`/`flow-name`, a abertura por Flow fica `INDISPONIVEL` e a conversa segue pelo
menu textual — degradação explícita, não erro.

## 9. Validação do Flow JSON

Arquivo: `src/main/resources/whatsapp/flows/agendamento-salao.flow.json`
(versão **7.3**, `data_api_version` **3.0**, pt-BR).

**BLOQUEIO — a validação oficial NÃO foi executada.** A Meta não publica validador de
schema executável: o repositório
[WhatsApp-Flows-Tools](https://github.com/WhatsApp/WhatsApp-Flows-Tools) contém apenas
`examples/endpoint`, `examples/webhook` e coleções Postman — não há CLI nem pacote npm
oficial (busca no registry retorna só pacotes de terceiros). A validação real é o **Flow
Builder do WhatsApp Manager**, que exige credencial da Meta.

O que **foi** feito:

1. **Conferência contra a documentação oficial de componentes**, que revelou e corrigiu
   três defeitos reais:
   - `CalendarPicker` em `mode: "single"` usa `label` **string** — estava com o objeto
     `{start-date, end-date}`, que só vale em `mode: "range"`;
   - datas são **`"YYYY-MM-DD"`**, não epoch em milissegundos — `min-date`, `max-date` e
     `unavailable-dates` estavam em epoch, e o presenter foi corrigido junto;
   - `visible` passou a apontar para um booleano dedicado (`tem_erro`) em vez de uma
     string — a 7.3 introduziu checagem de tipo que reprovaria o anterior.
2. **`FlowJsonEstruturaTest`** (9 testes) prende as invariantes que falham em SILÊNCIO:
   campo usado como `${data.x}` sem declaração no schema (a Meta descarta sem avisar), id
   de tela divergente do enum Java, rota fora do `routing_model` (validado desde a 7.3),
   `flow_action` desconhecida pelo backend, tela terminal única, ausência de "digite N".

**Não verificado** (exige o Flow Builder ou dispositivo real): renderização em Android e
iOS, limitações específicas do iOS, e a regra de `Footer` dentro de `If` — a documentação
permite no primeiro nível exigindo `else`, o que o JSON cumpre, mas só o validador oficial
confirma.

### Como validar antes de publicar

1. WhatsApp Manager → Account tools → Flows → **Create Flow**.
2. Colar o conteúdo do arquivo no editor JSON; o Builder valida e mostra o preview.
3. Testar no simulador (Android e iOS) antes de publicar.
4. Publicar e copiar o **Flow ID** para `TROQUIM_WHATSAPP_FLOW_ID`.

## 10. Cadastrar o Data Endpoint na Meta

1. WhatsApp Manager → Flows → o Flow → **Endpoint**.
2. URI: `https://api.troquim.app/api/v1/whatsapp/flows`.
3. Colar a **chave pública** (`flow-public.pem`) em *Sign public key*.
4. Usar **Health check** do painel: deve responder ao `ping` com `{"data":{"status":"active"}}`.
5. Anexar o número (phone number) ao Flow.

**Nginx** — o proxy precisa preservar o corpo bruto e não reescrever `Content-Type`:

```nginx
location /api/v1/whatsapp/flows {
    proxy_pass         http://backend;
    proxy_http_version 1.1;
    proxy_set_header   Host $host;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_request_buffering on;
    client_max_body_size 64k;   # alinhado ao limite do controller
}
```

TLS válido é obrigatório: a Meta recusa endpoint sem HTTPS confiável.

## 11. Rotação do token da Meta

O access token anterior **expirou** (`code 190 / subcode 463`). Nenhuma credencial foi
inserida nem renovada nesta implementação — **uma credencial válida será necessária antes
do teste real e do deploy**.

Procedimento:

1. Meta Business Suite → Configurações do sistema → **Usuários do sistema**.
2. Selecionar o usuário do sistema → **Gerar novo token** → app do WhatsApp →
   permissões `whatsapp_business_messaging` e `whatsapp_business_management`.
3. Preferir **token de longa duração** (usuário do sistema não expira em 60 dias).
4. Atualizar `TROQUIM_WHATSAPP_ACCESS_TOKEN` no ambiente (nunca no Git).
5. Reiniciar o backend e confirmar pelo health check do Flow.

Sintoma de token vencido: `WhatsAppCloudApiException` com HTTP 401 no envio. O efeito é
degradação limpa — a sessão é invalidada e a conversa cai no menu textual.

## 12. Testes

```bash
./mvnw test
```

**761 testes, 0 falhas, 0 erros, 0 ignorados. BUILD SUCCESS (~43 s).**

| Classe | Cobre |
|---|---|
| `WhatsAppFlowEndpointTest` (17) | protocolo, criptografia, ping, 421/427/400, telas, corrida, idempotência, ausência de vazamento em log, INIT→SUCESSO |
| `AbrirAgendaPorFlowServiceTest` (13) | sessão antes do envio, token imprevisível, vínculo com phone/tenant, TTL, estados, compensação, config ausente, canal sem suporte |
| `WhatsAppCloudFlowGatewayTest` (6) | payload `interactive/flow` real, `data_exchange`, draft/published, flow_name, erro 401 tipado, sigilo em log |
| `BookingFalhaTecnicaTest` (11) | conflito × falha técnica, idempotência de comando sequencial, mesma base com dados diferentes, retry sem depender de listar agendamentos, fingerprint divergente |
| `BookingIdempotencyPostgresTest` (8) | **PostgreSQL real**: concorrência com a mesma chave, disputa de slot entre comandos distintos, rollback liberando a chave, sobrevivência a novo contexto |
| `ConversaAteConfirmacaoTest` (2) | conversa → botão → sessão → endpoint → Appointment; conversa textual preservada |
| `WhatsAppFlowPersistenceFailureTest` (1) | falha de escrita não vira sucesso nem "horário ocupado" |
| `FlowJsonEstruturaTest` (9) | contrato do Flow JSON com o código |

**Infraestrutura falsa usada apenas em teste**, declarada aqui:

- `FlowTestCrypto` — lado cliente do protocolo, escrito **independentemente** do
  `FlowCipher` de produção, para o round-trip provar interoperabilidade e não simetria.
  Par RSA gerado em memória por execução; nenhuma chave de teste versionada.
- `GatewayEspiao` / `GatewayDeTeste` — substitutos do `OutboundFlowGateway` (não há Meta
  para receber a mensagem).
- `SessionStoreEmMemoria` — store com a mesma semântica de estado do adaptador JPA.
- `RepositorioQueFalhaAoSalvar` — repositório que recusa escrita, para provar a falha técnica.
- Servidor HTTP do JDK — outro lado falso da Graph API; o client HTTP é o real.

## 13. Rollback

Todo o recurso está atrás de uma flag:

```bash
TROQUIM_WHATSAPP_FLOW_ENABLED=false
```

Desligado, **nenhum bean do módulo é criado**: sem controller, sem gateway, sem sessões.
A conversa volta integralmente ao menu textual. Não é preciso reverter migration — a tabela
`whatsapp_flow_sessions` fica órfã e inerte.

Rollback parcial (manter o endpoint, parar de oferecer o botão): limpar
`TROQUIM_WHATSAPP_FLOW_ID` e `TROQUIM_WHATSAPP_FLOW_NAME`.

Migrations: `V4__whatsapp_flow_sessions.sql` (só aplicada no profile `azure`).

## 13.1 Conversation — idempotência por canal

**Cloud API — preservada e verificada.** `InboundReceiptProcessor.processOnce` é
`@Transactional` e engloba, numa transação só: claim do recibo
(`UNIQUE(provider, external_message_id)`), avanço da conversa, o booking (que entra por
`REQUIRED` e junta) e o estado da conversa. Nada disso foi alterado. É idempotência por
comando, durável, no nível da mensagem.

**Evolution — NÃO roteada. Bloqueio real, documentado em vez de contornado.**

O payload *tem* identidade estável: `data.key.id`, já extraída por
`EvolutionWhatsAppAdapter` e exposta como `IncomingMessage.messageId()`. O que impede o
roteamento pelo mesmo `InboundReceiptProcessor` é um **ciclo de dependência** no ponto de
chamada atual:

```
InboundReceiptProcessor → ConversationApplicationService → ConversationOrchestrator
                                                                    ↓
                                                    (precisaria de) InboundReceiptProcessor
```

Injeção por construtor falha no boot. Resolver com `@Lazy`/`ObjectProvider` só esconde o
ciclo. Há ainda dois obstáculos menores: `InboundReceiptProcessor` e `JpaInboundReceiptStore`
são `@ConditionalOnWhatsAppCloud` (não existem quando a Cloud está desligada — justamente
quando a Evolution é usada), e o orquestrador envia o outbound **dentro** do próprio
método, enquanto `processOnce` foi desenhado para o envio acontecer fora.

**Correção correta (não feita aqui):** mover a ingestão da Evolution para a BORDA, como já
acontece na Cloud — `WebhookController` → parser → `InboundTextMessage(provider="evolution", …)`
→ `processOnce` → outbound → `markSent`. Isso elimina o ciclo (a borda não é dependência do
processador), permite relaxar a flag condicional e substitui o `Set` em memória
(`mensagensProcessadas`, que não sobrevive a reinício) por dedup durável.

Não foi feito nesta mudança para não reestruturar o caminho legado no mesmo passo da
idempotência de booking. Enquanto isso, a Evolution tem **apenas deduplicação em memória**.

## 14. Pendências reais

1. **Credencial da Meta ausente** — o token expirou (190/463). Sem ela não há teste real.
2. **Flow não publicado**, logo não há `Flow ID`. Enquanto isso, a abertura fica indisponível.
3. **Validação oficial do JSON não executada** — ver §9.
4. **Catálogo de Service/Professional é placeholder** — ver §6.
5. **Sessões vencidas não são limpas** — o vencimento é honrado na leitura, mas as linhas
   se acumulam. Falta uma rotina de expurgo (o índice `idx_whatsapp_flow_sessions_expira_em`
   já existe para isso).
6. **`AppointmentBookingService`** (`schedule/`) ainda envolve o `ScheduleService` por fora
   da fronteira oficial, usado pelo `ConversationService` legado. Não foi migrado para não
   ampliar o escopo; é a próxima duplicação a eliminar.
7. **`ScheduleService` continua em memória** — reiniciar o backend recompõe o gabarito.
   Os Appointments é que são persistidos, então nenhum agendamento se perde, mas bloqueios
   manuais de horário sim.
