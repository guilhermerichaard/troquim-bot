# ARCHITECTURE_V2_1 — Errata Normativa (emenda cirúrgica à V2)

> **Natureza deste documento.** V2.1 é uma emenda cirúrgica à [ARCHITECTURE_V2.md](ARCHITECTURE_V2.md). Ele **não** reescreve a V2. Todas as decisões da V2 permanecem em vigor, **exceto** onde este documento as substitui explicitamente. As seções abaixo são numeradas por *tema de correção* (C1–C10), e cada uma declara qual seção da V2 ela substitui ou refina.
>
> **Precedência:** onde V2.1 e V2 divergirem, **V2.1 prevalece**. Onde V2.1 for silente, a V2 vige.
>
> **Não-regressões (reafirmadas, não reabertas):** colapsar Reservation no MVP (V2 §10.3); `BusinessId` obrigatório (V2 §7); exclusion constraint de intervalos (V2 §11); transação única por confirmação (V2 §13); idempotência persistente (V2 §12). Estes continuam de primeira classe, não são detalhes técnicos.
>
> Nenhum código, migration, schema, config ou teste foi alterado. Afirmações de evidência reconfirmadas lendo `src/main/java` nesta rodada.

---

# C1. Availability: decisão normativa (substitui V2 §6, §8, §9)

A V2 tratava `Availability` ao mesmo tempo como agregado persistido e como cálculo do engine. **Isto acaba aqui.**

### Modelo normativo
Adotado o modelo proposto, com papéis fixos:

| Elemento | Papel | Persistido? |
|---|---|---|
| `WorkingSchedule` | Agregado. Jornada recorrente semanal de **um profissional** (janelas por `DayOfWeek`). | **Sim** |
| `CalendarException` | Agregado. Exceção de calendário para uma data: fechamento total ou horário alterado. Escopo **de negócio** (todos os profissionais) **ou** de profissional. | **Sim** |
| `ProfessionalBlock` | Agregado. Bloqueio pontual de um profissional (almoço, folga, compromisso externo) num intervalo. | **Sim** |
| `Appointment` | Agregado. O agendamento; ocupa um intervalo. | **Sim** |
| `Service` (+ `ServiceDuration`, buffers) | Agregado + VOs. Autoridade de duração e buffers. | **Sim** (Service); duração/buffers são VOs dentro dele |
| `AvailabilityEngine` | **Domain Service.** Única autoridade que calcula possibilidades. | **Não** (é comportamento) |
| `AvailableSlot` | **Value Object / DTO derivado.** Resultado do cálculo (data, início, fim, profissional). | **Não** — nunca persistido |

### Respostas objetivas exigidas
- **Existe um agregado chamado `Availability`?** **Não.** O agregado `availability/Availability` atual é legado; será reformado em `WorkingSchedule` (ver plano). Não existe entidade `Availability` no modelo-alvo.
- **O que é persistido?** `WorkingSchedule`, `CalendarException`, `ProfessionalBlock`, `Appointment`, `Service` (com `ServiceDuration`/buffers como VOs).
- **O que é calculado?** A disponibilidade — como lista de `AvailableSlot` — produzida pelo `AvailabilityEngine` a partir de `WorkingSchedule − CalendarException − ProfessionalBlock − Appointment`s ocupantes, aplicando `ServiceDuration`+buffers.
- **O que é só DTO/VO?** `AvailableSlot` (derivado), `ServiceDuration`, buffers, e o intervalo ocupado (`TimeRange`/`tstzrange` — ver C3).
- **Única autoridade para calcular possibilidades?** `AvailabilityEngine` (Domain Service). **Nenhum** outro componente calcula disponibilidade — nem menu, nem confirmação, nem `ScheduleService` (que é aposentado, V2 §10.4).

### Consequência sobre `Appointment.availabilityId`
Como `AvailableSlot` **não** é persistido, `Appointment` **não** referencia um "slot". O campo/coluna `availabilityId` atual (que hoje aponta para um hash fabricado — V2 §2/D4) é **descontinuado**: `Appointment` referencia `ProfessionalId` + `ServiceId` + o **intervalo ocupado** (C3). Sem FK para entidade inexistente. (Remoção física do campo é migração, fora desta etapa.)

**Não há duas fontes de verdade:** a disponibilidade é *sempre* derivada; o que ocupa horário é *sempre* o conjunto de agregados persistidos acima, lidos pelo engine.

---

# C2. Fonte única de horário operacional (substitui V2 §6 tabela, refina §8)

A V2 mantinha `BusinessHours` dentro de `Business` **e** janelas em Availability — regra duplicada.

### Decisão normativa
- **Horário operacional de agendamento vive no contexto Scheduling**, em `WorkingSchedule`. É a **única** fonte para o cálculo de disponibilidade.
- **`Business` possui apenas dados administrativos + `BusinessZoneId`** (C3). O VO `BusinessHours` (hoje em `business/BusinessHours.java`: janela única `abertura/fechamento` + dias, com semântica inclusiva `[open, close]`) é **rebaixado a metadado administrativo/exibição** (ex.: "horário exibido ao cliente") e **não é consultado** pelo `AvailabilityEngine`. Se não houver uso administrativo real, é candidato a remoção futura (não nesta etapa).

### Onde cada coisa vive
- **Horário normal:** `WorkingSchedule` (por profissional; janelas recorrentes por `DayOfWeek`, wall-clock local — C3).
- **Feriados e exceções:** `CalendarException` (por data; tipo `CLOSED` ou `ALTERED_HOURS`; escopo de negócio ou de profissional).
- **Bloqueios pontuais:** `ProfessionalBlock` (por profissional; intervalo civil).
- **Negócio com vários profissionais:** cada `Professional` tem seu próprio `WorkingSchedule`; o `AvailabilityEngine` calcula por profissional; `CalendarException` de escopo de negócio aplica-se a **todos**.
- **Fechamento total do negócio:** `CalendarException` de escopo de negócio, tipo `CLOSED`, para a data (ou intervalo de datas) → o engine retorna **zero** `AvailableSlot` para todos os profissionais naquela data.
- **Como evitar duplicação de regra:** só o `AvailabilityEngine` consome `WorkingSchedule`/`CalendarException`/`ProfessionalBlock`. `BusinessHours` deixa de ser autoridade de agendamento. Uma janela operacional é definida **em um único lugar** (WorkingSchedule) e nunca replicada em Business.

*(Correção de coerência: `WorkingSchedule` usa intervalo meio-aberto `[start, end)` — C3 — corrigindo a semântica inclusiva de `BusinessHours.estaAberto`.)*

---

# C3. Modelo temporal e timezone (NOVA seção normativa — a V2 era silente)

Evidência: zero uso de `ZoneId/ZonedDateTime/Instant/timestamptz` no código; tudo é `LocalDate/LocalTime/LocalDateTime`. **Timezone é implícito hoje — proibido.**

### Decisões
- **`BusinessZoneId`:** atributo **obrigatório** de todo `Business`, armazenado como string IANA (ex.: `"America/Sao_Paulo"`). Sem default implícito no código; o seed do piloto define `America/Sao_Paulo` explicitamente. Toda conversão civil↔instante usa o `BusinessZoneId` do tenant dono do agendamento.
- **Tipos Java no domínio:**
  - Templates de jornada (`WorkingSchedule`): `DayOfWeek` + `LocalTime` (wall-clock, agnóstico de fuso).
  - `CalendarException`: `LocalDate` (+ `LocalTime` opcional para horário alterado).
  - `ProfessionalBlock` e a **seleção** do `Appointment`: `LocalDate` + `LocalTime` (civil, interpretados no `BusinessZoneId`). Esta seleção civil é a **intenção do usuário** ("sexta 14h") e é autoritativa para exibição.
  - O **instante** derivado usa `ZonedDateTime`/`Instant` (UTC) apenas na conversão.
- **Tipos na persistência:**
  - Templates: `time` + inteiro de dia-da-semana. `CalendarException`: `date` (+ `time`).
  - `Appointment`: colunas civis (`date DATE`, `start_time TIME`, `end_time TIME`) **mais** uma coluna derivada `occupied TSTZRANGE` (UTC).
- **Conversão local↔UTC:** na escrita, `(LocalDate, LocalTime, BusinessZoneId) → ZonedDateTime → Instant (UTC)`. Na leitura/exibição, `Instant → BusinessZoneId → LocalDateTime`.
- **`timestamp` vs `timestamptz` vs `tsrange` vs `tstzrange`:** o intervalo ocupado e a exclusion constraint usam **`tstzrange` (sobre `timestamptz`, ancorado em UTC)**. **Proibido** `timestamp`/`tsrange` naive (ambíguos em DST). A exclusion constraint da V2 §11.4 passa a usar `tstzrange` com `&&`.
- **Fonte única vs coluna derivada:** a coluna `occupied (tstzrange)` **não** é uma segunda fonte de verdade — é uma **projeção determinística** da seleção civil + `BusinessZoneId`, computada no momento da escrita, existindo **somente** para o enforcement (análoga a um índice/coluna calculada). Autoridade: *civil = intenção*; *`occupied` = chave de enforcement derivada*. Nunca editada independentemente.
- **Mudança de timezone do Business:** appointments já commitados **mantêm** seu `occupied` (instante fixo) e sua seleção civil registrada — **não** se movem. A mudança de `BusinessZoneId` afeta **apenas agendamentos futuros**. (Sem retro-conversão silenciosa.)
- **Semântica de `[start, end)`:** **meio-aberto** — `end` é exclusivo. Dois agendamentos onde um termina exatamente quando o outro começa **não** se sobrepõem. `tstzrange(..., '[)')`.
- **Duração e buffers no intervalo ocupado:** `end = start + ServiceDuration`; `occupied = [ start − bufferAntes , end + bufferDepois )`, convertido para instantes UTC. É esse `tstzrange` que a exclusion constraint compara com `&&`.

---

# C4. Protocolo de idempotência completo (substitui V2 §12)

A máquina `IN_PROGRESS/COMPLETED/FAILED` da V2 estava incompleta e sujeita a registro preso. **Substituída** por um protocolo *insert-as-guard em transação única*, que **elimina** o estado `IN_PROGRESS` persistente e, com ele, o problema do registro abandonado.

### Duas camadas + fronteiras transacionais

**Camada A — Idempotência da mensagem (webhook).**
Tabela `processed_inbound_message(inbound_message_id PK, business_id, received_at)`.
- Ao receber o webhook: `INSERT` de `inbound_message_id` **na mesma transação** que processa a mensagem (mutação de `ConversationState` e, se houver, o comando de confirmação).
  - `INSERT` bem-sucedido → primeira vez → processa.
  - Violação de PK → **já processada** (retry da Meta / segunda instância / após restart) → não reprocessa.
- **HTTP 200 é retornado somente após o COMMIT** dessa transação. Se a transação falhar/rollback → resposta não-2xx → Meta reenvia. (Assim, "confirmar recebimento" ⇔ estado durável — impede perda de mensagem: nenhum 200 é enviado antes do commit.)

**Camada B — Idempotência do comando de confirmação (`ConfirmBooking`).**
Tabela `booking_command(command_id PK, business_id, inbound_message_id, appointment_id, created_at)`.
- `ConfirmBooking` executa **uma** transação que faz: `INSERT booking_command(command_id, appointment_id)` **+** `INSERT appointment` **+** `INSERT/UPDATE customer`.
- A linha de idempotência é criada **na mesma transação** do `Appointment` (resposta à pergunta explícita). Logo, `booking_command` só se torna visível no commit, já apontando para um `Appointment` existente — **COMPLETED por construção**, sem janela de inconsistência.

### Comportamento por cenário
- **Primeira execução:** insere `booking_command` + `Appointment` + `Customer`, commita, envia resposta pós-commit (C10).
- **Repetição enquanto a primeira processa (concorrente, mesmo `command_id`):** o segundo `INSERT` do mesmo PK **bloqueia** no índice único até a primeira transação commitar/abortar. Se a primeira commitar → o segundo recebe `unique_violation` → lê a linha existente → retorna o **mesmo** `appointment_id`. Se a primeira abortar → o segundo prossegue. (Sem estado `IN_PROGRESS` explícito; o *lock* do índice é a exclusão mútua.)
- **Repetição depois de concluída:** `command_id` já existe (COMPLETED) → retorna o mesmo `appointment_id`, sem novo insert.
- **Falha com rollback:** a transação inteira desfaz → **nenhuma** linha de `booking_command` fica. Ausência de linha = "não concluído" = retry seguro. (Resposta a "como FAILED é persistido se a transação sofrer rollback?" → **não é**; não persistimos FAILED; ausência é o sinal retriável. Persistir FAILED dentro de uma transação que sofre rollback é impossível sem transação separada — que evitamos deliberadamente.)
- **Crash após persistir Appointment e antes de responder:** a transação **commitou** (`booking_command` COMPLETED + `Appointment` atômicos). A Meta reenvia → Camada A dedup pelo `inbound_message_id`, ou Camada B encontra `command_id` COMPLETED → retorna o **mesmo** `appointment_id`, HTTP 200. Sem duplicata.
- **Registro preso em IN_PROGRESS:** **não existe por construção** — não há `IN_PROGRESS` persistido em transação separada; o único "bloqueio" é o insert não-commitado, liberado automaticamente pelo Postgres no commit/rollback/queda de conexão. (Resposta a "como identificar e recuperar IN_PROGRESS abandonado?" → o problema é eliminado; não há o que recuperar.)
- **Restart da aplicação:** nenhum estado de idempotência em memória (o `Set` da V2/D9 é **removido**); tudo está no Postgres → sobrevive.
- **Duas instâncias:** o índice único (`inbound_message_id`, `command_id`) é global no banco → exclusão mútua entre instâncias.
- **Retry da Meta:** coberto pela Camada A (mensagem) e pela Camada B (comando).
- **Política de timeout e recuperação:** como não há `IN_PROGRESS` persistente, a única duração a limitar é a da própria transação — definida por `lock_timeout` e `statement_timeout` na conexão, de modo que uma tentativa travada aborta e libera o *lock* do índice. Sem varredor de "registros presos".
- **Retenção e limpeza:** `processed_inbound_message` e `booking_command` crescem indefinidamente. Retenção de **30–90 dias** (janela muito maior que qualquer retry realista da Meta). Limpeza por tarefa agendada **in-process** (`@Scheduled`, não distribuída) que apaga linhas mais antigas que a janela. Seguro porque nenhum retry chega tão tarde.

### Fronteiras transacionais (resumo)
- Linha de idempotência do comando: **mesma transação** do Appointment.
- FAILED: **não persistido** (rollback apaga a tentativa; ausência = retriável).
- Webhook responde 200: **somente pós-commit**.
- Efeitos externos (resposta WhatsApp): **fora** da transação, **pós-commit** (C10).

Sem mensageria distribuída. O Postgres é o árbitro de exclusão e o `@Scheduled` local faz a limpeza.

---

# C5. Identidade explícita da operação / idempotency key (substitui V2 §12.2 "chave natural")

**Proibido** usar `business + professional + date + start + customer` como chave **principal** de idempotência: após um cancelamento, um novo agendamento legítimo para o mesmo slot/cliente colidiria com a chave antiga.

### Identidades distintas (quatro conceitos, quatro papéis)
| Identidade | O que identifica | Onde atua |
|---|---|---|
| **`InboundMessageId`** | A mensagem do WhatsApp (id da mensagem na Meta). | Camada A (dedup de mensagem). "mesma mensagem recebida de novo". |
| **`CommandId`** | O **comando** de confirmação. Derivado deterministicamente do `InboundMessageId` que o disparou. | Camada B (idempotência do comando). "mesma confirmação executada de novo". |
| **Conflito de horário** | **Não é identidade.** É o invariante de não-sobreposição. | Exclusion constraint `(business, professional, tstzrange occupying)`. Dois comandos *diferentes* para o mesmo slot → o segundo recebe conflito (resultado de domínio), não *hit* de idempotência. |
| **`AppointmentId`** | O agregado `Appointment` resultante (UUID próprio). | Referência estável do agendamento. |

### Chave principal
- **`CommandId` (UUID)** é a chave principal de idempotência do `ConfirmBooking`, **derivada** do `InboundMessageId`. Mesma mensagem "confirmar" → mesmo `CommandId` → idempotente. Mensagem diferente → `CommandId` diferente → permitido.
- A tupla natural `(business, professional, date, start)` é usada **apenas** pela exclusion constraint (evitar sobreposição), **nunca** como chave de idempotência.

### Novo agendamento legítimo após cancelamento (o caso crítico)
1. Cancelamento → `Appointment.status = CANCELADO` → sai do filtro `WHERE status IN ('PENDENTE','CONFIRMADO')` da exclusion constraint → o slot **libera**.
2. Novo agendamento chega como **nova mensagem** → novo `InboundMessageId` → novo `CommandId` → nova `AppointmentId`.
3. Passa na idempotência (comando diferente) **e** na exclusion constraint (o antigo está `CANCELADO`, não ocupa). **Sem colisão.**

Separar `CommandId` (idempotência) de exclusion constraint (conflito) é o que torna isso correto — misturar os dois na "chave natural" era o defeito.

---

# C6. Segurança do MVP (substitui V2 §21 item de segurança — agora requisito ATUAL)

Evidência: `SecurityConfig.anyRequest().permitAll()` + CSRF off; e **em produção, na internet**, estão abertos `/dev/conversation`, `/customers`, `/appointments`, `/business`, `/professionals`, `/services`, `/availability`, `/reservations`, `/conversations`, `/clientes`, `/ordens`. Isto é CRUD administrativo e injeção de conversa **públicos**. **Não é risco futuro; é exposição atual.**

### Norma mínima (sem IAM complexo)
- **Padrão default-deny:** `anyRequest().authenticated()`. Substitui `permitAll()`. *Allowlist* explícita apenas para o que é público.
- **Endpoints públicos (allowlist):** somente (a) o **webhook** de entrada do canal e (b) `/actuator/health`. Nada mais.
- **Autenticidade do webhook:** validar assinatura da Meta (`X-Hub-Signature-256`, HMAC-SHA256 com o *app secret*) em todo POST; validar o *verify token* no handshake GET. Assinatura inválida/ausente → `403`. Obrigatório **antes** de integrar a Meta.
- **`/dev/*` em produção:** `DevConversationController` (injeção crua de conversa) **desabilitado em produção** — ativo **somente** sob profile `test`/`dev` (via `@Profile` ou flag de config). Em `prod`/`azure`, a rota não existe.
- **Endpoints administrativos/internos** (`/customers`, `/appointments`, `/business`, `/professionals`, `/services`, `/availability`, `/reservations`, `/conversations`, `/clientes`, `/ordens`): exigem autenticação. **Mínimo do MVP:** uma credencial administrativa única (API key/bearer token ou HTTP Basic forte) vinda de *secret*, verificada por um filtro. **Não** é IAM; é uma credencial de operador único — suficiente e apropriado ao MVP.
- **Rate limiting mínimo:** limite por IP no webhook e nos endpoints administrativos. Preferir `limit_req` no **nginx do host** (já existe, custo zero na app) e/ou um *bucket* in-process. Não distribuído.
- **Proteção contra chamadas arbitrárias:** default-deny + allowlist explícita (acima) já elimina a superfície aberta.
- **Secrets:** *app secret*, *verify token*, credencial administrativa — via ambiente/*secret store* (`.env` da Droplet, **não** commitado). *(Nota: há o problema já registrado de `.env` versionado no Git — rotacionar o que for real e remover do versionamento.)* Nenhum secret em código ou Git.
- **Local vs produção:** `/dev/*` e autenticação relaxada **apenas** sob profile `test`/`dev`; `prod`/`azure` aplica a *filter chain* acima. **O profile é o interruptor.**

Prioridade: esta seção é **pré-requisito de exposição pública** — deve entrar antes de qualquer tráfego real e antes do webhook Meta.

---

# C7. Context Map corrigido (substitui V2 §4 e §5)

`Application` e `Infrastructure` são **camadas**, não bounded contexts. Removidos do mapa. Contextos reais:

```
        Messaging/Channel Integration  (ACL do canal externo)
                     │  InboundMessage (normalizada)
                     ▼
              Conversation             (interpreta, mantém contexto)
                     │  invoca comandos/queries
                     ▼
                Scheduling  ◄── Service Catalog (duração/buffers)
                  (núcleo)  ◄── Customer Mgmt   (CustomerId)
                     ▲
                     └──────── Tenancy (BusinessId, BusinessZoneId)  [upstream de todos]
```

| Contexto | Responsabilidade | Modelo possuído | Dependências permitidas | Contrato de integração | Upstream/Downstream |
|---|---|---|---|---|---|
| **Tenancy** | Identidade do negócio, fuso, ciclo de vida do tenant | `Business`, `BusinessId`, `BusinessZoneId` | nenhuma | `resolveBusiness(BusinessId) → {zone, status}` | **Upstream** de todos |
| **Customer Management** | Cliente final do negócio | `Customer` (por tenant) | Tenancy | `resolveOrCreateCustomer(BusinessId, phoneE164) → CustomerId` | Downstream de Tenancy; upstream de Scheduling |
| **Service Catalog** | Catálogo de serviços, duração, buffers | `Service`, `ServiceDuration`, buffers | Tenancy | `getService(BusinessId, ServiceId) → {duração, buffers}` | Downstream de Tenancy; upstream de Scheduling |
| **Scheduling** (núcleo) | Jornada, exceções, bloqueios, agendamento, cálculo de disponibilidade | `WorkingSchedule`, `CalendarException`, `ProfessionalBlock`, `Professional`, `Appointment`, `AvailabilityEngine` | Tenancy, Customer Mgmt, Service Catalog | `ConfirmBooking(command)`, `AvailabilityQuery(...) → List<AvailableSlot>` | Downstream dos três acima; invocado por Conversation |
| **Conversation** | Interpretar mensagem, manter `ConversationState`, traduzir resultado em texto | `ConversationState` | Scheduling (invoca), Customer Mgmt | consome `AvailableSlot`/`BookingResult`; **não** possui regra de negócio | Downstream de Messaging; upstream (chamador) de Scheduling |
| **Messaging/Channel Integration** | Entrada/saída do canal (Evolution hoje, Meta depois); webhook; autenticidade; dedup de mensagem | adapters de canal, `InboundMessageId` | Conversation (entrega mensagem normalizada) | `InboundMessage → Conversation`; `OutboundReply ← Conversation` (pós-commit) | **Upstream** de Conversation; ACL sobre o canal externo |

**Cada módulo/contexto contém internamente as camadas** `domain / application / infrastructure` (ver V2 §16). A camada é *dentro* do contexto; nunca um contexto.

---

# C8. Customer: identidade (substitui V2 §6 linha Customer; refina §16)

Evidência: `CustomerId.fromPhone` = `UUID.nameUUIDFromBytes("customer:" + telefone_cru)` — sem `BusinessId`, sem E.164; `CustomerJpaEntity.phone` não-unique, sem `business_id`. Existe o VO `PhoneNumber` (normaliza para E.164) **mas o identity o ignora**. Efeito atual: formatos diferentes do mesmo número → Customers distintos; **mesmo número em dois negócios → mesmo `CustomerId` (colisão cross-tenant).**

### Decisões
- **Customer pertence ao Business** (por tenant). **Não** há Customer global no MVP.
- **Unicidade = `(BusinessId, phone_e164)`.** Constraint no banco: `UNIQUE (business_id, phone_e164)`.
- **Normalização E.164** via o VO `PhoneNumber` **antes** de qualquer identidade/lookup. O telefone cru nunca é chave.
- **`CustomerId` é um surrogate UUID aleatório** (não derivado do telefone). O *resolve-or-create* é por lookup na chave única `(business_id, phone_e164)`. Abandona-se o hash `fromPhone` (que impede escopo por tenant e é sensível a formato).
- **Mesmo telefone em dois negócios:** **duas** linhas `Customer` distintas (um por `business_id`), cada uma com seu `CustomerId`. Sem colisão (corrige o bug atual).
- **Atualizar nome sem duplicar:** *resolve* por `(business_id, phone_e164)` → encontrado → atualiza o nome **na mesma linha**. Sem novo Customer.
- **Customer global necessário?** **Não** no MVP. Justificativa concreta: não há funcionalidade cross-business; um identity global adiciona um contexto e questões de privacidade (LGPD) com zero retorno agora. Reconsiderar **apenas** se surgir fidelidade/histórico cross-business real.

---

# C9. Optimistic locking: onde `@Version` vive (refina V2 §11.5)

- **`@Version` pertence à entidade de persistência JPA** (ex.: `AppointmentJpaEntity`), **não** ao agregado de domínio puro (`appointment/Appointment`, que permanece um POJO sem anotação JPA).
- **O domínio pode conhecer uma versão apenas se houver necessidade explícita** (ex.: expor versão para concorrência otimista em uma API/DTO). Sem essa necessidade, a versão vive **somente** na entidade de persistência e é tratada pelo repositório/JPA.
- **Proibido acoplar o agregado puro a `@Version`.** (Correção da redação ambígua da V2, que dizia "`@Version` em Appointment".)
- Papéis (reafirmando V2): `@Version` cobre *updates concorrentes do mesmo agregado* (reagendar/cancelar/confirmar); a **não-sobreposição** é da exclusion constraint (C3), não do `@Version`.

---

# C10. Fronteira transacional: coordenação vs agregado (refina V2 §13)

- **Cada agregado preserva seus próprios invariantes:** `Customer` valida seus campos; `Appointment` valida `start < end` e transições de estado; `WorkingSchedule` valida suas janelas.
- **A Application (`ConfirmBooking`) pode coordenar mais de um agregado numa transação local do monólito** — `Customer` + `Appointment` + `booking_command` numa transação ACID única do Postgres. Isto é legítimo em monólito modular (banco único).
- **Isto não transforma `Customer` e `Appointment` num único agregado:** continuam agregados separados, com identidades e invariantes próprios. A transação é uma **fronteira de coordenação da Application**, não uma fronteira de agregado. (A regra "um agregado por transação" é diretriz para cenários distribuídos/escala; em banco único, coordenar poucos agregados numa transação local é aceitável e preferível a consistência eventual aqui.)
- **Efeitos externos ficam FORA da transação:** envio da resposta WhatsApp, chamadas à API Meta/Evolution, qualquer HTTP. Não se faz *rollback* de mensagem enviada.
- **Mensagem do WhatsApp é enviada SOMENTE após commit bem-sucedido:** via *hook* pós-commit (`TransactionSynchronization.afterCommit`, ou sequência `commit → então enviar`). Se o envio falhar, o agendamento **permanece** (já commitado); a resposta é reenviada em *best-effort*/registrada. **Nunca** enviar antes do commit (senão um agendamento revertido "confirmaria" ao cliente). Liga-se a C4 (200 pós-commit) e C6 (canal autenticado).

---

# Diferenças entre V2 e V2.1

1. **Availability desambiguada (C1):** não existe agregado `Availability`; persistidos = `WorkingSchedule`/`CalendarException`/`ProfessionalBlock`/`Appointment`/`Service`; `AvailableSlot` é VO derivado; `AvailabilityEngine` (Domain Service) é a única autoridade. `Appointment.availabilityId` descontinuado.
2. **Horário operacional com fonte única (C2):** vive em `WorkingSchedule` (Scheduling). `BusinessHours` rebaixado a metadado administrativo; `Business` guarda dados administrativos + `BusinessZoneId`. Definidos os lugares de horário normal, exceções, bloqueios, multi-profissional e fechamento total.
3. **Modelo temporal e timezone (C3, nova):** `BusinessZoneId` obrigatório; templates em wall-clock local; `Appointment` civil + `occupied tstzrange` (UTC) derivado; exclusion constraint sobre `tstzrange` `[)`; regras de conversão, DST e mudança de fuso; duração/buffers no intervalo.
4. **Idempotência reescrita (C4):** protocolo *insert-as-guard* em transação única; **eliminado** o `IN_PROGRESS` persistente (e o problema de registro preso); FAILED não é persistido (ausência = retriável); 200 só pós-commit; retenção 30–90 dias com limpeza `@Scheduled` local.
5. **Identidade explícita da operação (C5):** `CommandId` (derivado de `InboundMessageId`) como chave principal; distinção entre `InboundMessageId`/`CommandId`/conflito/`AppointmentId`; caminho correto para reagendar após cancelamento sem colisão.
6. **Segurança agora é requisito atual (C6):** default-deny; público só webhook + health; assinatura Meta; `/dev/*` fora de produção por profile; credencial administrativa mínima; rate limit no nginx; secrets fora do Git.
7. **Context Map corrigido (C7):** removidas `Application`/`Infrastructure` como contextos; seis contextos reais com responsabilidade/modelo/dependências/contrato/upstream-downstream; camadas ficam **dentro** de cada contexto.
8. **Customer por tenant (C8):** unicidade `(BusinessId, phone_e164)`; E.164 via `PhoneNumber`; `CustomerId` surrogate; abandono do hash `fromPhone`; sem Customer global no MVP.
9. **`@Version` na entidade JPA (C9):** não no agregado puro; domínio só conhece versão sob necessidade explícita.
10. **Fronteira transacional esclarecida (C10):** coordenação de múltiplos agregados numa transação local ≠ fundir agregados; efeitos externos fora da transação; WhatsApp só pós-commit.

*(Não alterado e reafirmado: colapso de Reservation, `BusinessId` obrigatório, exclusion constraint de intervalos, transação única de confirmação, consolidação de fonte única de Appointment, remoção de `schedule/*` legado.)*

---

# Decisões prontas para implementação

Implementáveis pelo Cline **sem nova análise** (respeitando: não é esta etapa que implementa; isto é o *backlog pronto*):

1. **Tenancy:** adicionar `business_id NOT NULL` em `customers`, `services`, `professionals`, `appointments` (e nos novos agregados); backfill com o `BusinessId` do piloto; `BusinessZoneId` (IANA) obrigatório em `Business`, seed `America/Sao_Paulo`. *(C3, C7; V2 §7)*
2. **Customer identity:** `UNIQUE (business_id, phone_e164)`; normalizar via `PhoneNumber` antes de identidade; `CustomerId` surrogate; *resolve-or-create* por `(business_id, phone_e164)`; remover uso de `CustomerId.fromPhone` como identidade. *(C8)*
3. **Concorrência:** `CREATE EXTENSION btree_gist`; exclusion constraint parcial em `appointments` sobre `(business_id =, professional_id =, occupied tstzrange &&) WHERE status IN ('PENDENTE','CONFIRMADO')`; coluna `occupied tstzrange` derivada de civil + `BusinessZoneId`; `@Version` em `AppointmentJpaEntity`; tradução de `23P01/23505/OptimisticLock` → erro de domínio. *(C3, C9; V2 §11)*
4. **`ConfirmBooking` transacional:** um `@Transactional`; cria `booking_command` + `Appointment` + `Customer` na mesma transação; sem Reservation; sem saga. *(C4, C10; V2 §13, §10.3)*
5. **Idempotência:** tabelas `processed_inbound_message` e `booking_command`; `CommandId` derivado de `InboundMessageId`; remover o `Set` do `ConversationOrchestrator`; `@Scheduled` de limpeza (retenção 30–90d); `lock_timeout`/`statement_timeout` na conexão. *(C4, C5)*
6. **Segurança:** `SecurityConfig` default-deny + allowlist (webhook + health); `/dev/*` sob `@Profile("!prod")`; filtro de credencial administrativa; assinatura do webhook Meta; `limit_req` no nginx; secrets via ambiente. *(C6)*
7. **Envio pós-commit:** resposta WhatsApp via `afterCommit`; webhook responde 200 só após commit. *(C4, C10)*
8. **Migrations versionadas:** introduzir Flyway/Liquibase (pré-requisito de 1/3/5), substituindo `ddl-auto=update` para constraints. *(V2 §15, ADR-111)*

*(Sequência recomendada permanece a do plano de fases da V2 §18, agora com C3/C6 antecipados por serem pré-requisitos de exposição pública e de enforcement.)*

---

# Decisões ainda abertas

Ambiguidades reais que **exigem decisão do time** antes de implementar os itens correspondentes (não escondidas):

1. **Ferramenta de migration:** Flyway **ou** Liquibase. Ambas servem; a escolha é de preferência do time. *Bloqueia* os itens 1/3/5 de "prontas para implementação" até decidida. **Recomendação:** Flyway (SQL puro, mais simples para o MVP).
2. **Escopo de `CalendarException`:** confirmado que suporta escopo de negócio **e** de profissional (C2). Aberto: se o MVP piloto (cadeira única) precisa já do escopo de profissional ou só do de negócio. **Recomendação:** implementar apenas escopo de negócio agora; escopo de profissional quando houver 2º profissional (adiável sem reescrita — é um campo de escopo).
3. **Derivação do `occupied tstzrange`:** computado na aplicação (Java) no momento da escrita **vs** coluna `GENERATED` no Postgres. Como o `BusinessZoneId` é por-tenant, uma coluna `GENERATED` exigiria a zona fixa por tabela — inviável multi-tenant. **Recomendação (quase fechada):** computar na aplicação; deixo aberto apenas para confirmação explícita porque afeta a migration.
4. **Credencial administrativa (C6):** API key estática única **vs** HTTP Basic. Equivalentes para operador único. **Recomendação:** bearer/API key via header, checada por filtro. Decisão trivial do time; não bloqueia o resto.
5. **Reintrodução de Reservation:** permanece **fechada como adiada** (V2 §10.3) — só reabre com evidência concreta de um dos gatilhos (pagamento, aprovação, confirmação assíncrona, hold com timeout). Listada aqui apenas para deixar explícito que **não** está aberta agora.

Nenhuma outra ambiguidade identificada. Os itens de "prontas para implementação" que **não** dependem de (1) podem começar imediatamente.

---

## Anexo — Revisão adversarial interna (V2.1)

Contradições buscadas e resolvidas antes de concluir:

1. **"Não há agregado Availability" × `Appointment` referencia `availabilityId`.** Contradição real da V2. Resolvida em C1: `availabilityId` é descontinuado; `Appointment` referencia `ProfessionalId`+`ServiceId`+intervalo ocupado; `AvailableSlot` não é persistido, logo nada faz FK para ele. Coerente.
2. **`occupied tstzrange` × "não duas fontes de verdade".** Potencial violação. Resolvida em C3: `occupied` é projeção **determinística** da seleção civil + zona, só para enforcement, nunca editada isoladamente — análoga a índice/coluna calculada. Autoridade declarada: civil = intenção. Não é fonte concorrente.
3. **Eliminar `IN_PROGRESS` × "definir política de timeout e recuperação de IN_PROGRESS".** O enunciado pedia para tratar `IN_PROGRESS` preso; minha decisão o **remove**. Verifiquei que isso responde melhor (elimina o modo de falha) e ainda cobre timeout via `lock_timeout`/`statement_timeout` da transação. Não é evasão — é solução mais forte, explicitada em C4.
4. **`CommandId` derivado de `InboundMessageId` × permitir reagendar após cancelamento.** Checado: reagendamento vem de **nova** mensagem → novo `InboundMessageId` → novo `CommandId`; o antigo `Appointment` está `CANCELADO` (fora da constraint). Sem colisão. C5 consistente com C4.
5. **`BusinessHours` rebaixado × "não remover arquivos / não alterar código".** Sem violação: C2 **rebaixa o papel** normativo de `BusinessHours` (deixa de ser autoridade de agendamento); a remoção física é adiada e marcada como candidata, não executada. Nenhum arquivo tocado.
6. **Segurança "requisito atual" × "não implementar nesta etapa".** Coerente: C6 é **norma** (o quê e por quê), não implementação. O item entra em "prontas para implementação" para execução posterior; nenhuma config foi alterada agora.
7. **Coordenar Customer+Appointment numa transação × "um agregado por transação" (DDD).** Potencial contradição doutrinária. Resolvida em C10: distingo *fronteira de coordenação da Application* de *fronteira de agregado*; a diretriz clássica é para cenários distribuídos; em banco único é aceitável. Declarado explicitamente para não confundir modelo com transação.
8. **`@Version` (C9) × exclusion constraint (C3) como mecanismos de concorrência.** Não competem: `@Version` = updates do mesmo agregado; exclusion constraint = não-sobreposição entre agregados. Papéis disjuntos, reafirmados em C9.
9. **Envio pós-commit (C10) × webhook 200 pós-commit (C4) × perda de mensagem.** Verifiquei a cadeia: 200 só após commit da entrada; resposta WhatsApp após commit; se o envio da resposta falhar, o estado de domínio persiste e a resposta é *best-effort*. Nenhuma perda de estado; no máximo uma resposta a reenviar. Consistente.
10. **Timezone e a constraint da V2 (que usava horário/`tsrange` implícito).** C3 força `tstzrange`/`timestamptz`; atualizei explicitamente a V2 §11.4 para o modelo temporal coerente. Sem resquício de `timestamp` naive.

Nenhuma contradição remanescente. As ambiguidades que **não** consegui fechar sozinho (ferramenta de migration; escopo de `CalendarException`; derivação `GENERATED` vs app) estão **declaradas** em "Decisões ainda abertas" — não escondidas.
