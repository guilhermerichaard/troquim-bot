# ARCHITECTURE_V2 — Arquitetura Normativa do MVP Troquim

> Documento normativo. Onde este documento e a "Bíblia do Troquim" (documento anterior gerado em conversa) divergirem, **este documento prevalece para o MVP**. O documento anterior é insumo, não autoridade absoluta.
>
> Nenhuma linha de código, migration, configuração, schema ou teste foi alterada na elaboração deste documento. Todas as afirmações da seção 2 foram reconfirmadas lendo o código em `src/main/java` nesta rodada.

---

# 1. Status e escopo

- **Status:** proposto / normativo. Vigente a partir da aprovação.
- **Estágio do produto:** MVP de atendimento e agendamento, single-tenant na prática, monólito modular Spring Boot + PostgreSQL, deploy único na Droplet.
- **No escopo deste documento:** fronteiras de módulos, aggregate boundaries, tenancy por `BusinessId`, fonte única de verdade de Appointment, autoridade de disponibilidade, concorrência/consistência, idempotência, limite transacional do caso de uso de confirmação, separação Conversation/Application, remoção de dependências concretas de Infrastructure na Application, decisão normativa sobre Reservation, estrutura de pacotes, plano incremental de migração, estratégia de testes, critério de "MVP arquiteturalmente pronto".
- **Fora do escopo (proibido nesta etapa e/ou adiado):** microsserviços, Kubernetes, mensageria distribuída, uma `Policy` por regra, `BusinessCalendar`, `Professional` generalizado para `Resource` com capacidade, eventos de domínio por padrão, refatoração de código, exclusão de arquivos, implementação.
- **Prioridade de execução (ordem fixa):** (1) impedir corrupção e duplicidade de dados; (2) isolamento por `BusinessId`; (3) consolidar fontes de verdade; (4) corrigir limites transacionais; (5) corrigir fronteiras arquiteturais; (6) melhorar modelagem; (7) preservar simplicidade de MVP.

---

# 2. Diagnóstico baseado no código

Fatos verificados nesta rodada (arquivo:linha). Este é o chão de concreto sobre o qual o resto do documento decide.

**D1 — Nenhum agregado do tenant referencia `BusinessId`.**
`grep BusinessId` em `customer/ service/ professional/ availability/ reservation/ schedule/ appointment/` → zero. `Business` (`business/Business.java`) existe e se declara "raiz de referência para todos os Aggregates", mas está desconectado. `AppointmentJpaEntity`/`ReservationJpaEntity` não têm coluna `business_id`. **O sistema é single-tenant de fato.**

**D2 — Duas fontes de verdade para "agendamento".**
- `appointment/Appointment.java` — tipado (`CustomerId`, `ProfessionalId`, `ServiceId`, `AvailabilityId`, `ReservationId`, `LocalDate/LocalTime`), persistido via `AppointmentJpaEntity` (tabela `appointments`), usado por 8 arquivos incluindo o fluxo STRICT_MVP.
- `schedule/Appointment.java` — stringly-typed (`numeroCliente`, `nomeCliente`, `servico`, `dia`, `horario` como `String`), servido pelo `schedule/AppointmentBookingService.java` (`@Service`), usado por `conversation/ConversationService.java` (o caminho NORMAL/legado).
Dois modelos, dois caminhos de escrita, sem reconciliação.

**D3 — A disponibilidade exibida não reflete o banco.**
`AvailabilityApplicationService.consultarDisponibilidade(...)` ([linha 244](../../src/main/java/com/troquim_bot/application/availability/AvailabilityApplicationService.java)) delega para `ScheduleService.listarHorariosDisponiveis(...)`. `ScheduleService` ([schedule/ScheduleService.java:13](../../src/main/java/com/troquim_bot/schedule/ScheduleService.java)) é um `@Service` com `Map<String,ScheduleDay>` **em memória e hardcoded** (09h–18h; sáb até 13h), reconstruído no construtor a cada restart, e **não consulta** `appointments`/`reservations`. O agregado `Availability` existe mas **não** é a fonte da leitura do menu. Resultado: um slot já agendado no Postgres continua listado como LIVRE.

**D4 — A "disponibilidade" no momento da escrita é fabricada.**
`BookingApplicationService.confirmar(...)` ([linha 95](../../src/main/java/com/troquim_bot/application/booking/BookingApplicationService.java)) monta `availabilityId = uuidDeterministico("availability:" + prof + dia + hora)`. Nenhum agregado `Availability` é lido. O horário escolhido não é validado contra disponibilidade real na confirmação.

**D5 — `Service` e `ServiceDuration` não estão no núcleo operacional.**
`BookingApplicationService` usa `DURACAO_PADRAO = Duration.ofHours(1)` ([linha 43](../../src/main/java/com/troquim_bot/application/booking/BookingApplicationService.java)) e `serviceId = uuidDeterministico("service:" + nome)`. O VO `ServiceDuration` (`service/ServiceDuration.java`, rico e correto) e o agregado `Service` **nunca são consultados**. Todo serviço dura 1h. Não há noção de buffer.

**D6 — Reservation não protege nada no fluxo atual; é órfã.**
Em `BookingApplicationService.confirmar`, o caminho de sucesso: cria `Reservation` → chama `appointmentApplicationService.criarAgendamento(...)` (o método **direto**, [linha 61](../../src/main/java/com/troquim_bot/application/appointment/AppointmentApplicationService.java)) → salva `Customer`. Existe o método correto `criarAgendamentoDeReserva(reservationId)` ([linha 83](../../src/main/java/com/troquim_bot/application/appointment/AppointmentApplicationService.java)) que consumiria a reserva (vincula `reservationId`, cancela a reserva) — mas ele **não é chamado**. Consequências verificadas:
- a `Reservation` nunca é vinculada ao `Appointment` e nunca é cancelada no sucesso → fica **ATIVA para sempre**;
- `Reservation` e `Appointment` são criados **sincronamente, no mesmo método, sem intervalo de tempo**, em tabelas separadas, com **checagens de conflito independentes**;
- `Reservation` e `appointment/Appointment` têm o **mesmo conjunto de campos**.

**D7 — Concorrência: dois checks TOCTOU, zero enforcement.**
- `ReservationApplicationService.criarReserva` ([linha 91–96](../../src/main/java/com/troquim_bot/application/reservation/ReservationApplicationService.java)): `findByProfessionalIdAndDate` → laço em memória `conflitaCom`; filtra `isAtivo()` mas **não** `!isExpirada()`.
- `AppointmentApplicationService.checkConflito` ([linha 216–229](../../src/main/java/com/troquim_bot/application/appointment/AppointmentApplicationService.java)): mesmo padrão read-all-then-check em memória.
- `AppointmentJpaEntity`/`ReservationJpaEntity`: **sem `@Version`, sem `uniqueConstraints`, sem exclusion constraint**. `ddl-auto=update`. Nenhum `@Transactional` no `BookingApplicationService` nem nos application services do caminho de escrita.
Entre o `SELECT` e o `INSERT`, duas requisições concorrentes para o mesmo horário passam ambas. **Double booking é possível hoje.**

**D8 — Expiração de Reservation não é imposta.**
`expiresAt = LocalDateTime.of(data, inicio)` ([BookingApplicationService:97](../../src/main/java/com/troquim_bot/application/booking/BookingApplicationService.java)) — expira no início do próprio horário agendado, não em minutos. Não há varredor de expiração. O check de conflito ignora expiração. A "Terceira Regra" do documento anterior ("expiração obrigatória") não existe no código.

**D9 — Idempotência é in-memory e não distribuída.**
`ConversationOrchestrator` ([linha 35](../../src/main/java/com/troquim_bot/application/conversation/ConversationOrchestrator.java)): `Set<String> mensagensProcessadas = ConcurrentHashMap.newKeySet()`; dedup por `messageId` ([linha 97](../../src/main/java/com/troquim_bot/application/conversation/ConversationOrchestrator.java)). Perde estado no restart; não é compartilhado entre réplicas. Não há idempotência no caso de uso de confirmação em si. O documento anterior exige o oposto (seção 5.4: "não pode depender de memória local").

**D10 — Vazamentos de fronteira.**
- Orquestração na camada de conversa: `StrictMvpMenuService` está no pacote `conversation` mas cria draft, seta step, chama `bookingApplicationService`/`availabilityApplicationService` — trabalho de Application (violação de #8/#9 das dez decisões).
- Application conhecendo Infrastructure concreta: `BookingApplicationService` e `AppointmentApplicationService` importam `InMemoryCustomerRepository`/`InMemoryReservationRepository`/`InMemoryAppointmentRepository` em construtores de conveniência (violação de #10; mesmo padrão do bug já corrigido em `CustomerApplicationService`).
- `AvailabilityApplicationService` depende de `schedule.ScheduleService` (Application do contexto Availability acoplada ao modelo legado).

**D11 — Segurança aberta.**
`SecurityConfig` ([config/SecurityConfig.java](../../src/main/java/com/troquim_bot/config/SecurityConfig.java)): `anyRequest().permitAll()`, CSRF desabilitado. Fora do escopo desta etapa, registrado como risco em §21.

---

# 3. Princípios permanentes

P1. **Toda regra de negócio pertence ao domínio.** Nunca a Controller, Conversation, prompt, IA ou adapter.
P2. **Modelo de domínio ≠ enforcement técnico.** O domínio expressa o invariante; o PostgreSQL o impõe. Um sem o outro é decoração (o código atual prova isso — ver D7).
P3. **Uma única fonte de verdade por conceito.** Duas fontes é bug.
P4. **Isolamento por tenant é estrutural, não convenção.** `BusinessId` participa de identidade, índice e constraint — não é só uma coluna.
P5. **O caso de uso é o limite transacional.** Uma confirmação = uma transação = tudo-ou-nada.
P6. **Idempotência é persistente.** Sobrevive a restart e a múltiplas instâncias.
P7. **Conversation interpreta; Application decide e orquestra; Domain valida; Infrastructure integra.**
P8. **Simplicidade de MVP é uma feature.** Só se introduz uma abstração quando a dor é comprovada. Ausência de enforcement de dados **não** é simplicidade — é dívida.
P9. **Falha técnica vira erro de domínio na fronteira.** Uma violação de constraint do Postgres nunca vaza como stack trace; vira "horário indisponível".

---

# 4. Context Map

Monólito modular. Contextos e relação (setas = dependência / fluxo):

```
        ┌─────────────────┐
        │  Conversation   │  (interpreta intenção, mantém contexto)
        └────────┬────────┘
                 │ invoca casos de uso (comandos/queries), nunca decide regra
                 ▼
        ┌─────────────────┐
        │   Application   │  (orquestra, limite transacional, idempotência)
        └───┬─────────┬───┘
            │         │
            ▼         ▼
   ┌────────────┐  ┌────────────────────────┐
   │  Identity  │  │      Scheduling        │
   │  & Tenancy │  │  (núcleo do domínio)   │
   │  Business  │  │  Service, Professional,│
   │  Customer  │  │  Availability,         │
   └────────────┘  │  Appointment           │
                   └───────────┬────────────┘
                               │ portas (interfaces de repositório)
                               ▼
                     ┌───────────────────┐
                     │  Infrastructure   │  (JPA/Postgres, Evolution/WhatsApp)
                     └───────────────────┘
```

Relações DDD:
- **Conversation → Application:** Customer/Supplier. Conversation é cliente; Application publica um contrato de casos de uso (Application Services / command handlers). Conversation **não** importa agregados de Scheduling para decidir.
- **Application → Scheduling / Identity:** Application depende de agregados e de **portas** (interfaces) de repositório, nunca de implementações.
- **Scheduling → Shared Kernel:** value objects de tempo e `BusinessId`/ids compartilhados vivem num Shared Kernel mínimo (`common`).
- **Infrastructure → tudo (implementa portas):** Anti-Corruption Layer para Evolution/WhatsApp (o adapter traduz payload externo em comando; nenhuma regra ali).

---

# 5. Bounded Contexts

| Contexto | Responsabilidade | Agregados / Serviços | Decisão |
|---|---|---|---|
| **Identity & Tenancy** | Quem é o negócio (tenant) e o cliente final | `Business`, `Customer` | MANTER; conectar via `BusinessId` |
| **Scheduling** (núcleo) | Serviço, profissional, disponibilidade, agendamento | `Service`, `Professional`, `Availability`, `Appointment` | AJUSTAR (ver §8) |
| **Conversation** | Interpretar mensagem, manter estado da conversa, traduzir resultado em texto | `ConversationState`, parsers do menu STRICT_MVP | AJUSTAR (mover orquestração p/ Application) |
| **Messaging/Integration** | Entrada/saída WhatsApp (Evolution hoje, Meta depois) | adapters, webhook | MANTER como Infrastructure/ACL |

O contexto **Scheduling** é onde vive a regra crítica (conflito, duração, disponibilidade). Conversation e Messaging são periféricos e não podem conter regra.

---

# 6. Aggregate Boundaries

Regra geral: **agregado pequeno, referência por Id, uma transação por agregado** — exceto o caso de uso de confirmação, que coordena a criação de **um** agregado (`Appointment`) sob uma transação com enforcement no banco.

| Agregado | Raiz | Contém | Referencia por Id | Invariantes próprios |
|---|---|---|---|---|
| `Business` | `Business` | `BusinessHours` | — | status válido; ao menos um contato; horário de funcionamento |
| `Customer` | `Customer` | — | `BusinessId` | identidade por telefone dentro do tenant |
| `Service` | `Service` | `ServiceDuration`, buffers | `BusinessId` | duração > 0; buffers ≥ 0 |
| `Professional` | `Professional` | — | `BusinessId` | status |
| `Availability` | `Availability` | janelas por dia | `BusinessId`, `ProfessionalId` | `start < end`; sem sobreposição de janelas |
| `Appointment` | `Appointment` | intervalo ocupado | `BusinessId`, `CustomerId`, `ProfessionalId`, `ServiceId`, `AvailabilityId`, `reservationId?` | `start < end`; estado; **não sobrepõe** outro Appointment ocupante do mesmo `(Business, Professional)` |

**Decisão-chave:** o invariante de não-sobreposição é do **agregado `Appointment`** como conceito, mas sua **imposição** é do PostgreSQL (exclusion constraint) porque cruza múltiplas instâncias do agregado (P2). O domínio expressa `Appointment.ocupaIntervalo()` e `conflitaCom(...)`; o banco garante que dois commits concorrentes não coexistam.

---

# 7. Modelo de Tenancy — `BusinessId` obrigatório

**Classificação: IMPLEMENTAR AGORA.**

- **Evidência (código):** D1 — nenhum agregado referencia `BusinessId`; sem coluna `business_id`.
- **Arquivo/classe:** `business/Business.java`; ausência em `customer/`, `service/`, `professional/`, `availability/`, `reservation/`, `appointment/`; `infrastructure/persistence/*JpaEntity.java`.
- **Problema:** lock-in single-tenant; qualquer query/constraint global mistura tenants no primeiro cliente adicional; retrofit pós-piloto exige migração de todas as tabelas + reescrita de todas as queries + backfill.
- **Decisão normativa:** `BusinessId` é obrigatório em **entidade de domínio, coluna, repositório, toda query, todo índice e toda constraint** dos agregados de tenant (`Customer`, `Service`, `Professional`, `Availability`, `Appointment`). Nenhuma query de tenant sem filtro por `BusinessId`. A exclusion/unique constraint de agendamento inclui `business_id` como primeira coluna de particionamento lógico.
- **Justificativa:** isolamento é estrutural (P4); custo hoje ≈ baixo (pouco dado), custo depois ≈ migração dolorosa (prioridade 2).
- **Trade-off:** um pouco mais de cerimônia em cada assinatura de repositório e no seed do MVP (um `BusinessId` default para o salão piloto).
- **Impacto:** todas as entidades JPA ganham `business_id NOT NULL`; repositórios ganham o parâmetro; caso de uso de confirmação resolve o `BusinessId` do tenant a partir do canal/instância.
- **Migração:** ver §18, Fase 1 (coluna + backfill com o `BusinessId` do piloto + `NOT NULL` + índices).
- **Testes:** teste que rejeita persistência sem `businessId`; teste que uma query de um tenant nunca retorna linha de outro (isolamento).
- **Reconsideração:** nunca reconsiderar a presença; só reconsiderar a estratégia (ex.: schema-per-tenant) se e quando houver requisito de isolamento físico — não é o caso no MVP.

---

# 8. Scheduling Domain

### 8.1 Service como autoridade de duração e buffers — AJUSTAR → IMPLEMENTAR AGORA (wiring)

- **Evidência:** D5 — 1h hardcoded; `ServiceDuration` e `Service` ignorados.
- **Arquivo/classe/método:** `BookingApplicationService.DURACAO_PADRAO`; `service/ServiceDuration.java` (existe, correto); `service/Service.java`.
- **Problema:** a duração — regra de domínio do serviço — vive num `static final`; o fim do intervalo (`end`) e portanto a detecção de conflito estão errados para qualquer serviço ≠ 1h.
- **Decisão:** `Service` é a **única autoridade** de `duração` e de `buffers` (antes/depois). O caso de uso de confirmação obtém `Service` por Id, calcula `end = start + duração` e o intervalo ocupado = `[start − bufferAntes, end + bufferDepois)`. Nada de duração hardcoded.
- **Justificativa:** P1/P3; conflito correto depende de duração real.
- **Trade-off:** exige um catálogo mínimo de `Service` por tenant (mesmo que 5 serviços fixos no seed). Aceitável.
- **Impacto:** `Service` passa a ser lido no fluxo; `Appointment` passa a armazenar o intervalo ocupado (com buffer) para a constraint.
- **Migração:** §18 Fase 3. Seed dos serviços do piloto com durações reais.
- **Testes:** conflito entre serviço de 30min e 1h no mesmo profissional; buffer que faz dois serviços adjacentes conflitarem.
- **Reconsideração:** modelar buffers variáveis por profissional só quando houver demanda; hoje buffer é atributo do `Service`.

### 8.2 Buffers

- **Decisão:** buffer é atributo do `Service` (VO simples `minutosAntes`/`minutosDepois`, default 0). Entra no cálculo do intervalo ocupado, não em regra separada. **Não** criar `Policy` para isso.

---

# 9. Availability Engine — única autoridade

**Classificação: AJUSTAR (consolidar) — alta prioridade.**

- **Evidência:** D3 (leitura do menu vem do `ScheduleService` em memória hardcoded, não do agregado `Availability`) e D4 (escrita fabrica `availabilityId`, não lê `Availability`).
- **Arquivo/classe:** `application/availability/AvailabilityApplicationService.consultarDisponibilidade`; `schedule/ScheduleService`; `BookingApplicationService`.
- **Problema:** três verdades de disponibilidade não reconciliadas (agenda hardcoded do `ScheduleService`; agregado `Availability`; a inexistente validação na escrita). Slot ocupado aparece livre; escrita não valida disponibilidade.
- **Decisão normativa:** existe **um** *Availability Engine* (serviço de domínio no contexto Scheduling) que é a **única** autoridade para calcular possibilidades. Ele computa slots a partir de: janelas do agregado `Availability` do profissional − `Appointment`s ocupantes (estados PENDENTE/CONFIRMADO) − bloqueios, aplicando `ServiceDuration`+buffers. **Tanto a leitura (menu) quanto a escrita (confirmação) passam por ele.** `ScheduleService` (hardcoded/in-memory) é aposentado (§10.4 / §18).
- **Justificativa:** P3 — uma fonte; elimina o "slot ocupado aparece livre".
- **Trade-off:** o cálculo de slots passa a bater no banco (mais custo que um `Map` estático). Para o volume do MVP, irrelevante.
- **Impacto:** menu e confirmação consomem o mesmo engine; `AvailabilityId` passa a referenciar um `Availability` real.
- **Migração:** §18 Fase 4.
- **Testes:** um slot com Appointment ocupante não é ofertado; o menu e a confirmação concordam para a mesma entrada.
- **Reconsideração:** materializar um read model de disponibilidade (projeção) **só** se o cálculo on-the-fly virar gargalo — ver §21 (não é `BusinessCalendar`; é uma projeção de performance).

### 9.1 Revalidação obrigatória na confirmação

**Classificação: IMPLEMENTAR AGORA.**

- **Decisão:** disponibilidade é validada **duas vezes**: ao ofertar (menu) e **novamente dentro da transação de confirmação**, imediatamente antes do insert. A oferta é uma leitura potencialmente obsoleta; a confirmação é a decisão. A revalidação na confirmação + o enforcement do banco (§11) são as duas linhas de defesa.
- **Justificativa:** entre ofertar e confirmar, outro cliente pode ter agendado. Sem revalidação, o double booking depende só do banco; com ela, a maioria dos casos vira erro de domínio limpo antes de bater na constraint.
- **Testes:** ofertar slot, ocupar por outro caminho, confirmar → erro de domínio "indisponível" (não exceção técnica).

---

# 10. Appointment e Reservation

### 10.1 Fonte única de verdade de Appointment — IMPLEMENTAR AGORA (consolidação)

- **Evidência:** D2 — `appointment/Appointment` (tipado, persistido) vs `schedule/Appointment` (String, in-memory, caminho NORMAL).
- **Decisão normativa:** **`appointment/Appointment` (tipado, persistido em `appointments`) é a única fonte de verdade.** Todo caminho de escrita — STRICT_MVP e NORMAL — converge para ele via o caso de uso `ConfirmBooking`.
- **Justificativa:** P3.
- **Trade-off:** o caminho NORMAL (`ConversationService` → `schedule.AppointmentBookingService`) precisa ser reapontado antes de aposentar o legado.
- **Migração:** §18 Fase 2 e 6.
- **Testes:** um agendamento feito por qualquer canal aparece em `/appointments`; não existe agendamento que exista só em memória.

### 10.2 Destino do modelo legado `schedule/Appointment` — REMOVER (ao final da migração)

- **Evidência:** D2/D3. `schedule/*` (`Appointment`, `AppointmentService`, `AppointmentBookingService`, `ScheduleService`, `ScheduleDay`, `ScheduleSlot`, `SlotStatus`) é o modelo paralelo stringly-typed + agenda em memória.
- **Decisão:** marcar `schedule/*` como **legado a remover**. Não apagar agora (proibido nesta etapa e ainda referenciado por `ConversationService`). Sequência: (1) reapontar o caminho NORMAL para `ConfirmBooking`; (2) reapontar a leitura de disponibilidade para o Availability Engine; (3) então remover `schedule/*` num commit dedicado.
- **Justificativa:** P3, prioridade 3.
- **Trade-off:** conviver com o legado por 1–2 fases; mitigado por não haver escrita nova roteada para ele após a Fase 6.
- **Testes:** após a Fase 6, `grep` não encontra referência de produção a `schedule.*`; suíte verde sem o pacote.
- **Reconsideração:** nenhuma — é dívida a quitar.

### 10.3 Decisão normativa sobre Reservation — **COLAPSAR em Appointment no MVP; reintroduzir depois**

**Classificação: AJUSTAR (colapsar agora) + ADIAR (reintrodução).**

- **Evidência:** D6, D8. A Reservation é criada, **nunca consumida** (`criarAgendamentoDeReserva` é dead code no fluxo), **nunca expira** de fato, fica ATIVA para sempre, em tabela separada, com check de conflito próprio e independente do de Appointment. Reservation e Appointment têm os mesmos campos e são criados sem intervalo de tempo entre si.
- **Problema:** no fluxo síncrono atual, a Reservation **não oferece proteção real** — ela duplica o Appointment, adiciona uma tabela, uma escrita, uma saga de compensação (que nem roda no sucesso) e um segundo ponto de corrida. É custo sem benefício.
- **Critérios avaliados (decisão, não "depende"):** o padrão hold-antes-de-commit só se paga quando existe **um intervalo de tempo entre reservar e confirmar** em que o slot precisa ficar protegido. Isso ocorre com: **pagamento**, **aprovação humana**, **confirmação posterior em outro momento/canal**, **hold temporário com timeout**. **Nenhum desses existe no MVP** — a confirmação e a criação do Appointment são a mesma operação síncrona. Logo, hoje a Reservation é overengineering.
- **Decisão:** **colapsar.** No MVP, `ConfirmBooking` cria diretamente **um `Appointment`** (estado inicial PENDENTE), protegido pela exclusion constraint (§11). Não criar Reservation no fluxo. Manter as classes `Reservation`/`ReservationId`/tabela `reservations` no repositório **sem removê-las** e **sem escrevê-las** (não apagar arquivos nesta etapa; a remoção física é um commit posterior opcional).
- **Como evoluir sem reescrever o sistema:** a reintrodução é **aditiva**, não uma reescrita, porque os pontos de extensão já existem: `appointment/Appointment` já tem o campo opcional `reservationId`; `AppointmentJpaEntity` já tem a coluna `reservation_id` (nullable); `AppointmentApplicationService.criarAgendamentoDeReserva` já implementa o consumo. Quando um dos gatilhos abaixo chegar, cria-se o caso de uso `HoldSlot` (cria `Reservation` com TTL + estado que ocupa intervalo na constraint) e `ConfirmBooking` passa a aceitar `criarAgendamentoDeReserva`. A exclusion constraint (§11) passa a incluir também reservas ativas não expiradas nos "estados que ocupam horário" — sem mudar o modelo de Appointment.
- **Gatilhos objetivos de reintrodução (qualquer um):** (a) cobrança/pagamento antes de confirmar; (b) aprovação humana do dono do negócio; (c) confirmação assíncrona (cliente confirma minutos/horas depois); (d) múltiplos canais concorrendo pelo mesmo slot com necessidade de hold curto; (e) requisito explícito de timeout de reserva.
- **Trade-off:** perde-se, no MVP, a capacidade de "segurar" um slot durante um fluxo longo — que hoje não existe. Ganha-se um modelo com uma única escrita e um único ponto de enforcement.
- **Impacto:** `BookingApplicationService` deixa de criar Reservation; a compensação manual (saga) desaparece (não há mais dois writes a coordenar).
- **Testes:** confirmar não cria linha em `reservations`; a suíte de Reservation existente continua válida como teste de unidade do agregado (para a reintrodução futura), mas nenhum teste de fluxo depende de Reservation.
- **Reconsideração:** ver gatilhos (a)–(e).

### 10.4 `ScheduleService` (agenda em memória) — REMOVER (Fase 4/6)

Coberto em §9 e §10.2. É a fonte hardcoded que precisa dar lugar ao Availability Engine.

---

# 11. Concorrência e consistência

**Classificação: IMPLEMENTAR AGORA (prioridade 1).**

O enunciado é explícito: **não** basta `unique(start_time)`. É preciso impedir **sobreposição de intervalos** considerando duração, buffers, profissional/recurso, `BusinessId`, estados que ocupam horário e duas requisições concorrentes.

### 11.1 Invariante de domínio
Para um mesmo `(BusinessId, ProfessionalId)` e data, **não podem coexistir dois `Appointment`s em estado ocupante cujos intervalos `[start − bufferAntes, end + bufferDepois)` se sobreponham.** "Estados ocupantes" = `PENDENTE`, `CONFIRMADO` (e, quando Reservation for reintroduzida, reservas ativas não expiradas). O agregado expressa isso (`Appointment.ocupaIntervalo()`, `conflitaCom(outro)`), corrigindo a checagem que hoje ignora duração real e expiração.

### 11.2 Validação da Application
Dentro da transação de `ConfirmBooking`, imediatamente antes do insert: (1) resolve `Service` (duração+buffers) → calcula o intervalo ocupado; (2) revalida disponibilidade via Availability Engine (§9.1); (3) tenta inserir. A validação da Application é a **primeira linha** (erro de domínio limpo na maioria dos casos); ela **não** substitui o enforcement do banco.

### 11.3 Transação
`ConfirmBooking` é **um único `@Transactional`** (§13). Hoje não há `@Transactional` no caminho de escrita (D7) — isso é corrigido. Uma confirmação escreve `Customer` (se novo) + `Appointment` na mesma transação; falha em qualquer passo desfaz tudo (sem saga manual).

### 11.4 Mecanismo PostgreSQL (o enforcement real)
**Exclusion constraint** com `btree_gist`:
```
-- conceitual; a migration será escrita na etapa de implementação
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE appointments ADD CONSTRAINT appt_no_overlap
  EXCLUDE USING gist (
    business_id     WITH =,
    professional_id WITH =,
    tsrange(occupied_start, occupied_end, '[)') WITH &&
  )
  WHERE (status IN ('PENDENTE','CONFIRMADO'));
```
Isto impõe a não-sobreposição de **intervalos** (não de horário inicial), particionada por `business_id` e `professional_id`, restrita aos estados ocupantes (constraint parcial), considerando duração+buffers (já embutidos em `occupied_start/occupied_end`). Duas requisições concorrentes: a primeira commita; a segunda recebe violação de exclusão (SQLState `23P01`) e falha — **o banco é o árbitro**, não a velocidade da internet. Isto substitui os dois checks TOCTOU em memória (D7).

### 11.5 Optimistic locking
`@Version` em `Appointment` (e em `Availability` quando janelas forem editáveis). Cobre **atualizações concorrentes** do mesmo agregado (reagendar/cancelar/confirmar) — dois writes na mesma linha → o segundo recebe `OptimisticLockException` → traduzido para erro de domínio. **Não** é o mecanismo da não-sobreposição (isso é a exclusion constraint); é complementar.

### 11.6 Quando lock pessimista seria necessário
Só quando a decisão exigir **serializar uma leitura-computa-escreve que não se expressa como constraint** — por exemplo, capacidade N do recurso (contar quantos ocupam a janela antes de decidir), ou alocação entre múltiplos profissionais com regra de balanceamento. Nesse caso: `SELECT ... FOR UPDATE` na "linha do recurso/dia". **No MVP (cadeira única, capacidade 1), a exclusion constraint basta; lock pessimista NÃO é necessário.** Gatilho para reconsiderar: introdução de capacidade > 1 (que também está adiada, §21).

### 11.7 Conversão de falha técnica em erro de domínio
Na fronteira Infrastructure/Application, capturar `23P01` (exclusion_violation), `23505` (unique_violation) e `OptimisticLockException` e traduzir para uma exceção de domínio `HorarioIndisponivelException` (ou equivalente), que a Application converte em `BookingResult.indisponivel(...)` e a Conversation em mensagem ("Esse horário acabou de ser ocupado, escolha outro"). **Nunca** vazar SQLState/stack trace para a conversa (P9).

- **Impacto:** entidades JPA ganham `@Version` e colunas `occupied_start/occupied_end`; migration cria extensão + constraint; tradução de exceção num único ponto.
- **Testes obrigatórios:** ver §19 (teste de concorrência real com duas transações simultâneas; teste de sobreposição por duração; teste de buffer; teste de tradução de exceção).
- **Reconsideração:** trocar exclusion constraint por lógica aplicacional só se surgir capacidade > 1 (então soma-se lock pessimista, não se remove a constraint).

---

# 12. Idempotência

**Classificação: IMPLEMENTAR AGORA (prioridade 1) + REMOVER o `Set` em memória.**

- **Evidência:** D9 — `Set` em memória no `ConversationOrchestrator`.
- **Problema a resolver (enunciado):** retry da Meta; restart; múltiplas instâncias; mesma mensagem recebida de novo; mesma confirmação executada de novo; operação já concluída; operação ainda em processamento; operação que falhou.

### 12.1 Idempotência de webhook (inbound)
Tabela persistente `processed_messages(message_id PK, business_id, received_at)`. Ao receber, tenta `INSERT` do `message_id`:
- insert bem-sucedido → é a primeira vez → processa;
- violação de PK → **já recebida** (retry da Meta / reentrega / outra instância) → ignora (resposta 200 idempotente).
Isto sobrevive a restart e é compartilhado entre instâncias (é o banco). Substitui o `Set` (D9).

### 12.2 Idempotência de confirmação (caso de uso `ConfirmBooking`)
Chave de idempotência persistente por operação de negócio. Duas fontes possíveis de chave (decisão: usar a natural, com a explícita como reforço):
- **natural:** determinística de `(business_id, professional_id, date, start_time, customer_id)`;
- **explícita:** um `idempotency_key` propagado do inbound quando disponível.
Tabela `booking_idempotency(key PK, business_id, status, appointment_id, created_at, updated_at)` com máquina de estados:
- **ausente** → cria a linha `IN_PROGRESS` na mesma transação e prossegue;
- **IN_PROGRESS** (execução concorrente da mesma operação) → rejeita a segunda com "processando" (ou aguarda) — evita dois inserts simultâneos da mesma confirmação;
- **COMPLETED** → retorna o **mesmo** `appointment_id` (mesmo resultado, sem novo insert) — "apertou confirmar 5×, 1 Appointment";
- **FAILED** → permite retry (nova tentativa reusa a chave).
Observação de robustez: mesmo que a idempotência falhe em alguma borda, a **exclusion constraint (§11.4) é a rede final** contra duplicidade — as duas defesas são complementares (idempotência devolve o mesmo resultado; a constraint impede o duplo).

- **Impacto:** duas tabelas novas (`processed_messages`, `booking_idempotency`); `ConversationOrchestrator` deixa de usar `Set` e passa a consultar `processed_messages`.
- **Migração:** §18 Fase 5.
- **Testes obrigatórios:** §19 (retry de webhook após restart; confirmação repetida devolve o mesmo Appointment; duas confirmações concorrentes da mesma operação → 1 Appointment).
- **Reconsideração:** política de expiração/limpeza de `processed_messages` (retenção) quando o volume crescer — detalhe operacional, não arquitetural.

---

# 13. Application Layer

**Caso de uso `ConfirmBooking` — limite transacional.**

- **Decisão:** um único command handler `ConfirmBooking` (Application), `@Transactional`, que: resolve `BusinessId` do tenant; resolve/valida `Service` (duração+buffers); revalida disponibilidade (Availability Engine); aplica idempotência (§12.2); cria `Customer` se novo e `Appointment` (PENDENTE) na **mesma** transação; traduz violação de constraint/lock em erro de domínio (§11.7). Sem Reservation (§10.3), sem saga.
- **Evidência corrigida:** hoje a orquestração está partida entre `BookingApplicationService` (sem `@Transactional`, com saga manual e compensação parcial) e `StrictMvpMenuService` (no pacote conversation). D6/D7/D10.
- **Regra de dependência:** Application depende de **portas** (interfaces de repositório) e de agregados/serviços de domínio — **nunca** de implementações concretas de Infrastructure. Corrige D10 (imports de `InMemory*`).
- **Impacto:** `BookingApplicationService` evolui para o handler transacional; `AppointmentApplicationService`/`ReservationApplicationService` perdem a lógica de conflito em memória (o enforcement migra para o banco) e os construtores que instanciam `InMemory*`.
- **Testes:** teste de caso de uso com rollback (falha no meio → nada persiste); teste de fronteira (Application não importa `infrastructure.*` nem `Inmemory*`).
- **Classificação:** IMPLEMENTAR AGORA (transação + tradução de erro); AJUSTAR (mover conflito para o banco); REMOVER (imports concretos).

---

# 14. Conversation Layer

- **Decisão:** Conversation **só** interpreta a mensagem, mantém o `ConversationState` e traduz o resultado do caso de uso em texto. As transições do menu STRICT_MVP e as chamadas aos casos de uso saem de `StrictMvpMenuService` (hoje no pacote `conversation`) para a Application, como orquestração de caso de uso. O parsing (mapear "1"/"cabelo" → intenção/serviço) pode permanecer na Conversation, mas **decidir** e **persistir** não.
- **Evidência:** D10 — `StrictMvpMenuService` orquestra (cria draft, seta step, chama booking/availability).
- **Problema:** regra e orquestração na camada de interpretação; transição de estado sem validação de domínio; é onde nasceu o sintoma "AGUARDANDO_DIA + 'cabelo'".
- **Trade-off:** um pouco mais de indireção (Conversation → Application) em troca de fronteira limpa e testável.
- **Impacto:** `ConversationOrchestrator` chama o handler `ConfirmBooking`; o menu vira um tradutor de entrada/saída.
- **Testes:** Conversation não importa agregados de Scheduling para decidir; um teste garante que a transição de estado inválida é rejeitada pelo caso de uso, não "aceita" pela conversa.
- **Classificação:** AJUSTAR.

---

# 15. Infrastructure

- **Decisão:** Infrastructure implementa apenas portas de persistência (JPA/Postgres) e integração (Evolution/WhatsApp, futura Meta) como Anti-Corruption Layer. Nenhuma regra de negócio. A Application depende de interfaces (`*Repository` no domínio/porta); as implementações JPA vivem em `infrastructure/persistence`. A tradução de exceção técnica → domínio (§11.7) acontece na borda de Infrastructure/Application, num único ponto.
- **Evidência corrigida:** hoje há vazamento reverso — a Application importa `InMemory*Repository` (D10). O correto é o oposto: Infrastructure conhece o domínio, não a Application conhece a Infrastructure.
- **Classificação:** MANTER (o padrão de adapter já existe: `JpaCustomerRepository @Primary` etc.); AJUSTAR (remover os imports concretos na Application); IMPLEMENTAR AGORA (extensão `btree_gist`, constraint, `@Version`, tabelas de idempotência via migrations versionadas).
- **Nota de migrations:** hoje o schema é `ddl-auto=update` (auto). Constraints/exclusion/índices exigem **migrations versionadas** (Flyway/Liquibase) — introduzir a ferramenta é pré-requisito da Fase 1 (decisão de ferramenta fica para a etapa de implementação; o documento apenas a exige).

---

# 16. Estrutura de pacotes (monólito modular)

Alvo (por bounded context, com camadas internas). Não renomear tudo de uma vez — ver §18.

```
com.troquim_bot
├── shared/                      (Shared Kernel: BusinessId, ids, VOs de tempo, erros de domínio base)
├── tenancy/
│   ├── domain/                  (Business, BusinessHours, BusinessId)
│   ├── application/
│   └── infrastructure/
├── customer/
│   ├── domain/                  (Customer, CustomerId, CustomerProfile)
│   ├── application/             (CustomerApplicationService, CustomerProfileService)
│   └── infrastructure/          (JpaCustomerRepository, CustomerJpaEntity)
├── scheduling/
│   ├── domain/
│   │   ├── service/             (Service, ServiceDuration, buffers)
│   │   ├── professional/        (Professional)
│   │   ├── availability/        (Availability, AvailabilityEngine [serviço de domínio])
│   │   └── appointment/         (Appointment — FONTE ÚNICA; Reservation em standby)
│   ├── application/             (ConfirmBooking [@Transactional], AvailabilityQuery)
│   └── infrastructure/          (Jpa*Repository, *JpaEntity, tradução de exceção)
├── conversation/
│   ├── domain/                  (ConversationState, ConversationStep)
│   ├── application/             (orquestração de conversa → invoca ConfirmBooking)
│   └── interpretation/          (parsers do menu STRICT_MVP — sem decisão de negócio)
├── messaging/                   (ACL WhatsApp/Evolution; webhook; idempotência inbound)
└── platform/                    (config, security)
```

- **Legado a extinguir:** `schedule/*` (agenda em memória + Appointment stringly-typed) e o `model/`, `chatbot/`, `ai/` na medida em que dupliquem responsabilidades — auditar na Fase 6.
- **Classificação:** AJUSTAR (evolução incremental de pacote); não é refatoração big-bang.

---

# 17. ADRs normativas

Cada ADR será um arquivo próprio em `docs/architecture/adr/` na etapa de implementação. Aqui ficam registradas como decisões (o ADR-006 do documento anterior era filosófico; estes são executáveis).

| ADR | Título | Classificação |
|---|---|---|
| ADR-101 | `BusinessId` obrigatório em todo agregado, coluna, query, índice e constraint de tenant | IMPLEMENTAR AGORA |
| ADR-102 | Não-sobreposição de intervalos via exclusion constraint `btree_gist` (não `unique(start)`) | IMPLEMENTAR AGORA |
| ADR-103 | Optimistic locking (`@Version`) para updates de agregado; pessimista só com capacidade > 1 | IMPLEMENTAR AGORA (optimistic) / ADIAR (pessimista) |
| ADR-104 | Idempotência persistente: `processed_messages` (webhook) + `booking_idempotency` (confirmação) | IMPLEMENTAR AGORA |
| ADR-105 | `ConfirmBooking` como único caso de uso transacional de agendamento | IMPLEMENTAR AGORA |
| ADR-106 | `appointment/Appointment` como fonte única; `schedule/*` legado a remover | IMPLEMENTAR AGORA (consolidar) / REMOVER (fim) |
| ADR-107 | Availability Engine como autoridade única (leitura e escrita); revalidação na confirmação | AJUSTAR |
| ADR-108 | `Service` autoridade de duração e buffers | AJUSTAR |
| ADR-109 | Reservation colapsada no MVP; reintrodução aditiva sob gatilhos definidos | AJUSTAR / ADIAR |
| ADR-110 | Tradução de falha técnica (SQLState 23P01/23505, OptimisticLock) em erro de domínio | IMPLEMENTAR AGORA |
| ADR-111 | Migrations versionadas (substituir `ddl-auto=update` para constraints) | IMPLEMENTAR AGORA |
| ADR-112 | Orquestração fora da Conversation; Application depende de portas, não de `InMemory*` | AJUSTAR / REMOVER |

---

# 18. Plano incremental de migração

Cada fase é entregável, testável e não quebra o deploy. Ordem segue a prioridade (§1).

**Fase 0 — Rede de segurança de testes.** Escrever os testes de §19 que faltam **antes** de mudar comportamento (caracterização): concorrência, idempotência, isolamento por tenant. Alguns começarão vermelhos (expõem D7/D9/D1) — é o alvo.

**Fase 1 — Tenancy + ferramenta de migration (prioridade 1–2).** Introduzir migrations versionadas. Adicionar `business_id NOT NULL` em `customers`, `services`, `professionals`, `availability`, `appointments` (+ `reservations`), com backfill do `BusinessId` do salão piloto. Propagar `BusinessId` em portas/queries.

**Fase 2 — `ConfirmBooking` transacional + tradução de erro (prioridade 1,4).** Criar o handler `@Transactional`; mover a orquestração do STRICT_MVP para ele; reapontar `ConversationOrchestrator`. Ainda sem constraint (vem na 3) — mas já com transação e tradução de exceção.

**Fase 3 — Enforcement de concorrência (prioridade 1).** `btree_gist` + exclusion constraint parcial (com `occupied_start/end` derivados de `ServiceDuration`+buffers); `@Version` nas entidades. Remover os dois checks TOCTOU em memória. Ligar `Service` (duração/buffers).

**Fase 4 — Availability Engine único (prioridade 3).** Menu e confirmação passam a ler do engine (janelas `Availability` − Appointments ocupantes). Parar de usar `ScheduleService` para leitura.

**Fase 5 — Idempotência persistente (prioridade 1).** `processed_messages` + `booking_idempotency`; remover o `Set` do `ConversationOrchestrator`.

**Fase 6 — Consolidação de fonte única (prioridade 3,5).** Reapontar o caminho NORMAL (`ConversationService`) para `ConfirmBooking`; remover `schedule/*` e imports `InMemory*` na Application; auditar `model/`/`chatbot/`/`ai/`.

**Fase 7 — Colapso definitivo de Reservation (prioridade 6,7).** Confirmar que nada escreve `reservations`; deixar o agregado em standby documentado (não apagar).

---

# 19. Estratégia de testes (obrigatórios)

| Área | Teste obrigatório | O que prova |
|---|---|---|
| Concorrência | Duas transações simultâneas confirmando o mesmo `(business, professional, date, intervalo)` → exatamente **1** Appointment; a outra recebe erro de domínio | Enforcement da exclusion constraint (§11.4) |
| Sobreposição | Serviço 30min × 60min no mesmo profissional com intervalos que se cruzam → conflito; adjacentes sem buffer → OK; adjacentes com buffer → conflito | Duração+buffers no intervalo (§8/§11) |
| Tenant | Query de um `BusinessId` nunca retorna linha de outro; persistir sem `businessId` falha | Isolamento (§7) |
| Idempotência webhook | Mesma `message_id` reenviada após restart simulado → processada 1× | `processed_messages` (§12.1) |
| Idempotência confirmação | Mesma confirmação 5× → 1 Appointment, mesmo id retornado; concorrentes → 1 | `booking_idempotency` + constraint (§12.2) |
| Transação | Falha após criar Customer, antes do Appointment → rollback total (nada persiste) | Limite transacional (§13) |
| Disponibilidade | Slot com Appointment ocupante não é ofertado; ofertar→ocupar→confirmar = erro de domínio | Availability Engine + revalidação (§9) |
| Fronteiras | Application não importa `infrastructure.*`/`InMemory*`; Conversation não decide regra | §13/§14/§15 |
| Fonte única | Agendamento por qualquer canal aparece em `appointments`; nenhum só em memória | §10.1 |
| Tradução de erro | Violação de constraint vira mensagem de domínio, não stack trace | §11.7 |

Regressão: manter a suíte atual verde a cada fase (hoje 609 testes; o teste de fixture de data já foi corrigido). Nenhuma fase pode reduzir a cobertura dos invariantes acima.

---

# 20. Critério de MVP arquiteturalmente pronto

O MVP está **arquiteturalmente pronto** quando **todos** os itens abaixo forem verdade (checklist objetivo):

1. Nenhuma escrita de tenant sem `business_id`; todas as queries de tenant filtram por ele. ✅/❌
2. Impossível criar dois Appointments ocupantes sobrepostos para o mesmo `(business, professional)` — provado por teste de concorrência real contra Postgres. ✅/❌
3. Existe **uma** fonte de verdade de Appointment; `schedule/*` não recebe escrita de produção. ✅/❌
4. Confirmação é **uma** transação; falha parcial não deixa estado órfão. ✅/❌
5. Disponibilidade lida no menu e validada na confirmação vêm do **mesmo** Availability Engine; slot ocupado nunca é ofertado. ✅/❌
6. Duração e buffers vêm de `Service`; não há duração hardcoded. ✅/❌
7. Idempotência persistente cobre retry de webhook e confirmação repetida, sobrevivendo a restart e a múltiplas instâncias. ✅/❌
8. Falha técnica de concorrência é convertida em erro de domínio; nenhum stack trace vaza para a conversa. ✅/❌
9. Application não depende de implementações concretas de Infrastructure; Conversation não decide regra. ✅/❌
10. Reservation não é escrita no fluxo; seu caminho de reintrodução está documentado e testável. ✅/❌

Enquanto (1), (2), (4) e (7) não forem verdes, **o MVP não deve receber tráfego real de cliente** — são os itens que impedem corrupção/duplicidade de dados (prioridade 1).

---

# 21. Decisões adiadas (ADIAR / REMOVER)

| Item | Classificação | Gatilho para reconsiderar |
|---|---|---|
| `BusinessCalendar` como artefato modelado | ADIAR | Só se o cálculo de disponibilidade on-the-fly virar gargalo mensurável → então uma **projeção/read model**, não entidade central |
| `BusinessRules` → uma `Policy` por regra | ADIAR | Quando houver ≥ 2 regras versionáveis de verdade (ex.: política de cancelamento que muda no tempo). Até lá, regra vive nos agregados |
| `Professional` → `Resource` com capacidade > 1 | ADIAR | Quando um tenant precisar de múltiplos atendentes/salas com capacidade simultânea → então soma-se lock pessimista (§11.6) |
| Eventos de domínio formais | ADIAR | Quando houver consumidor real (integração, projeção, auditoria) — não criar por padrão |
| Lock pessimista | ADIAR | Junto com capacidade > 1 |
| Remoção física de `schedule/*` e de Reservation | REMOVER (commit posterior) | Após Fases 6/7, fora desta etapa (não apagar agora) |
| `SecurityConfig permitAll` | AJUSTAR (fora do escopo desta etapa) | Antes de expor endpoints sensíveis publicamente / antes do webhook Meta — registrar como risco de segurança |
| Multi-instância real (escala horizontal) | ADIAR | A idempotência (§12) já é pré-requisito; a escala em si não é meta do MVP |

---

# 22. Resumo executivo

O código está mais frágil do que a arquitetura declarada sugere e mais frágil do que a primeira revisão apontou: além de não haver `BusinessId`, nem `@Version`, nem constraint, nem `@Transactional` no caminho de escrita, a **Reservation é órfã** (criada e nunca consumida — `criarAgendamentoDeReserva` é dead code), a **disponibilidade do menu vem de um `Map` hardcoded em memória** que não conhece o banco (slot ocupado aparece livre), e existem **dois modelos de Appointment** e **dois caminhos de escrita de conversa** vivos.

As decisões normativas, em ordem de prioridade:
1. **Impedir duplicidade:** exclusion constraint de intervalos (`btree_gist`, particionada por `business_id`+`professional_id`, estados ocupantes) + `@Version` + `ConfirmBooking` transacional + tradução de erro técnico em erro de domínio.
2. **Isolar tenant:** `BusinessId` obrigatório em entidade, coluna, query, índice e constraint — agora, antes do piloto.
3. **Fonte única:** `appointment/Appointment` é a verdade; `schedule/*` é legado a remover; Availability Engine único para leitura e escrita.
4. **Transação:** um `@Transactional` por confirmação; fim da saga manual.
5. **Fronteiras:** orquestração sai da Conversation; Application depende de portas, não de `InMemory*`.
6. **Idempotência persistente:** tabelas para webhook e confirmação; fim do `Set` em memória.
7. **Reservation:** **colapsada no MVP** (não protege nada hoje), com reintrodução **aditiva** — os pontos de extensão (`reservationId`, coluna nullable, `criarAgendamentoDeReserva`) já existem — sob gatilhos objetivos (pagamento, aprovação, confirmação assíncrona, hold com timeout).

Princípio que costura tudo: **modelo de domínio expressa o invariante; o PostgreSQL o impõe.** O código atual tem o modelo e não tem a imposição — por isso aceita double booking. V2 fecha essa lacuna sem microsserviços, sem mensageria, sem `Policy` por regra, sem `BusinessCalendar` e sem generalizar `Professional`.

---

## Anexo — Revisão adversarial interna deste documento

Revisei o próprio documento em busca de contradições. Correções e esclarecimentos aplicados:

1. **"Availability Engine é autoridade única" × "revalidar na confirmação" × "o banco é o árbitro".** Aparente redundância tripla. Não é contradição: são camadas distintas — o Engine **calcula** possibilidades (leitura), a revalidação **rejeita cedo** com erro limpo (UX), a exclusion constraint **garante** sob concorrência (correção). Deixei explícito em §9.1 e §11.2 que a validação da Application **não substitui** o enforcement do banco (P2).

2. **Idempotência × exclusion constraint (dupla proteção contra duplicidade).** Poderiam parecer mecanismos concorrentes. Esclarecido em §12.2: têm **propósitos diferentes** — a idempotência **devolve o mesmo resultado** para uma repetição legítima (5× confirmar → mesmo Appointment, sem erro); a constraint **impede o duplo** sob corrida (retorna erro). Uma sem a outra deixa um buraco (só constraint → repetição vira erro feio; só idempotência → corrida real passa).

3. **Colapsar Reservation × "concorrência nasce na Reservation" (doc anterior).** Contradição com a autoridade anterior — resolvida a favor deste documento (P/ escopo): no MVP a concorrência é imposta no `Appointment` via constraint; a Reservation volta quando houver gap temporal real. Documentei que a reintrodução é **aditiva** para não violar "evoluir sem reescrever".

4. **"Não confundir modelo com enforcement" × colocar o invariante no agregado.** Consistente: o agregado **expressa** `conflitaCom`/`ocupaIntervalo` (modelo), o banco **impõe** (enforcement). §6 e §11.1 deixam claro que a checagem em memória de hoje é insuficiente **como enforcement**, não que o invariante não pertença ao domínio.

5. **"Não refatorar / não apagar / não implementar" × plano de migração com constraints e remoções.** Sem violação: este documento **não** altera código nesta etapa; o §18 é o **plano** para etapas futuras, e a remoção de `schedule/*`/Reservation é explicitamente adiada para commits posteriores (§21). O documento em si é o único artefato criado.

6. **`ddl-auto=update` × exigir constraints/exclusion.** Contradição operacional real: `update` não cria exclusion constraints de forma confiável. Corrigido tornando **migrations versionadas** um pré-requisito explícito (ADR-111, §15, Fase 1) — sem isso, §11 não é implementável.

7. **Buffers no `Service` × "não generalizar / não criar Policy".** Verifiquei que buffer como atributo do `Service` **não** cria uma `Policy` nem generaliza `Professional` — é um VO simples no agregado que já deveria ser autoridade de tempo (§8.2). Consistente com as restrições.

Nenhuma contradição remanescente identificada.
