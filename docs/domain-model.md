# Domain Model - Troquim

## Domínio Principal

O Troquim é uma plataforma de agendamento para empreendedores de beleza. O domínio principal gira em torno de **agendamentos confirmados** entre clientes e negócios de serviços.

---

## Aggregates Aprovados

### 1. Business
**Conceito**: O negócio cliente do Troquim (salao, barbearia, studio, etc).

**Responsabilidades**:
- Configurar horários de funcionamento
- Definir regras de agendamento
- Gerenciar recursos físicos/lógicos
- Estabelecer políticas de cancelamento e confirmação

**Entidades internas**: Nenhuma

**Value Objects internos**:
- BusinessId
- BusinessName
- ContactInfo
- BusinessHours
- BusinessStatus (ATIVO, INATIVO, SUSPENSO)

**Invariants protegidas**:
- Business deve ter nome e contato válidos
- Business não pode ser deletado se tiver Appointments futuros
- Business deve ter horário de funcionamento definido para aceitar agendamentos

**Domain Events**:
- `BusinessCreated`
- `BusinessActivated`
- `BusinessDeactivated`
- `BusinessUpdated`

---

### 2. Customer
**Conceito**: O cliente final que agenda serviços no negócio.

**Entidades internas**: Nenhuma

**Value Objects internos**:
- CustomerId
- CustomerName
- PhoneNumber
- CustomerContact
- CustomerPreferences
- CustomerStatus (ATIVO, INATIVO, BLOQUEADO)

**Invariants protegidas**:
- Customer deve ter pelo menos um contato (phone ou email)
- Customer não pode ser deletado se tiver Appointments futuros
- PhoneNumber deve ser único por Business

**Domain Events**:
- `CustomerCreated`
- `CustomerUpdated`
- `CustomerContactChanged`
- `CustomerBlocked`
- `CustomerDeleted`

---

### 3. Reservation
**Conceito**: Reserva temporária de um slot com TTR (Time To Reserve).

**Entidades internas**: Nenhuma

**Value Objects internos**:
- ReservationId
- ReservationStatus (ATIVA, CONFIRMADA, EXPIRADA, CANCELADA)
- ReservationWindow (tempo de validade da reserva)
- ReservationToken

**Invariants protegidas**:
- Reservation deve estar vinculada a um Resource e um TimeSlot
- Reservation não pode ser criada se slot não estiver LIVRE
- Reservation deve expirar após janela de validade
- Apenas uma Reservation ativa por Resource/TimeSlot

**Domain Events**:
- `ReservationCreated`
- `ReservationConfirmed`
- `ReservationExpired`
- `ReservationCancelled`

---

### 4. Appointment
**Conceito**: Agendamento confirmado e persistente.

**Entidades internas**: Nenhuma

**Value Objects internos**:
- AppointmentId
- AppointmentStatus (PENDENTE, CONFIRMADO, RECUSADO, CANCELADO, CONCLUIDO)
- AppointmentDateTime
- ServiceType
- ServiceDuration
- ServicePrice
- AppointmentNotes

**Invariants protegidas**:
- Appointment deve estar vinculado a um Customer
- Appointment deve estar vinculado a um Resource
- Appointment deve ter uma Reservation confirmada
- Appointment não pode ser criado no passado
- Appointment não pode ser modificado após X horas de antecedência (regra configurável)

**Domain Events**:
- `AppointmentCreated`
- `AppointmentConfirmed`
- `AppointmentCancelled`
- `AppointmentCompleted`
- `AppointmentRescheduled`

---

## Conceitos que NÃO são Aggregates

### Availability (Domain Service / Calculation Engine)
**Conceito**: Motor de cálculo de disponibilidade. Não é um Aggregate porque:
- Não possui identidade própria
- Não é persistido
- É um serviço de domínio que consulta outros Aggregates
- Calcula slots disponíveis baseado em Resource, Calendar e Rules

**Responsabilidades**:
- Calcular slots livres
- Verificar conflitos
- Aplicar regras de negócio

**Dependências**: Resource, BusinessCalendar, BusinessRules

---

### Conversation (Application/Interface Context)
**Conceito**: Estado da conversa via WhatsApp. Não é um Aggregate porque:
- É um contexto de aplicação/interface
- Não possui regras de negócio próprias
- É um estado volátil da interface
- Não precisa ser persistido como entidade de domínio

**Responsabilidades**:
- Gerenciar estado da conversa
- Controlar fluxo de diálogo
- Coordenar com Reasoning

**Dependências**: Customer, Business

---

### Reasoning (AI/Application Capability)
**Conceito**: Capacidade de IA para interpretação de intenções. Não é um Aggregate porque:
- É uma capability da aplicação
- Não possui identidade própria
- Não é persistido
- É um serviço de aplicação que usa IA

**Responsabilidades**:
- Interpretar intenções
- Classificar mensagens
- Tomar decisões baseadas em IA

**Dependências**: Conversation

---

## Relações Permitidas

```
Business (1) ←─── (N) Customer
Business (1) ←─── (N) Appointment

Customer (1) ←─── (N) Appointment
Customer (1) ←─── (N) Reservation

Resource (1) ←─── (N) Reservation
Resource (1) ←─── (N) Appointment

Reservation (1) → (1) Appointment (ao confirmar)

Availability → Resource (consulta)
Availability → BusinessCalendar (consulta)
Availability → BusinessRules (consulta)

Conversation → Customer (consulta)
Conversation → Business (consulta)
Reasoning → Conversation (consulta)
```

---

## Relações Proibidas

❌ **NUNCA**:
- Appointment → Business diretamente (sempre via Resource ou Customer)
- Reservation → Business diretamente
- Conversation → Resource diretamente
- Reasoning → Resource diretamente
- Customer → Resource diretamente
- Qualquer Aggregate → BusinessRules diretamente (sempre via Availability ou Application Service)

❌ **NUNCA** modificar outro Aggregate diretamente:
- Reservation não pode modificar Appointment
- Conversation não pode modificar Customer
- Reasoning não pode modificar Conversation
- Availability não pode modificar Reservation

❌ **NUNCA** acessar Aggregates diretamente:
- Interfaces (WhatsApp, Web, API) não acessam Aggregates diretamente
- Sempre via Application Services
- Conversation e Reasoning são camadas de interface

---

## Ordem de Implementação

### Fase 1: Fundação (Sprint 1)
1. **Business** - base de tudo
2. **Customer** - necessário para agendamentos
3. **Resource** - necessário para disponibilidade

### Fase 2: Configuração (Sprint 2)
4. **BusinessCalendar** - bloqueios e exceções
5. **BusinessRules** - regras configuráveis

### Fase 3: Disponibilidade (Sprint 3)
6. **Availability** - cálculo de slots (domain service)

### Fase 4: Reservas (Sprint 4)
7. **Reservation** - reserva temporária com TTR

### Fase 5: Agendamento (Sprint 5)
8. **Appointment** - agendamento confirmado

### Fase 6: Interface (Sprint 6)
9. **Conversation** - estado da conversa (application context)
10. **Reasoning** - IA e decisões (application capability)

---

## Regras de Ouro do Domínio

### 1. Aggregate Design
- ✅ Cada Aggregate tem uma raiz clara
- ✅ Entidades internas são acessadas apenas via Aggregate Root
- ✅ Modificações sempre passam pela raiz
- ✅ Cada Aggregate protege suas próprias invariants

### 2. Event-Driven
- ✅ Todos os Aggregates emitem Domain Events
- ✅ Eventos são usados para comunicação entre Aggregates
- ✅ Nenhum Aggregate chama métodos de outro diretamente para modificação

### 3. Consistency Boundaries
- ✅ Cada Aggregate garante suas próprias invariants
- ✅ Transações são limitadas a um Aggregate por vez
- ✅ Consistência eventual entre Aggregates

### 4. Anti-Corruption Layer
- ✅ Interfaces (WhatsApp, Web, API) nunca acessam Aggregates diretamente
- ✅ Sempre via Application Services
- ✅ Conversation e Reasoning são camadas de interface

### 5. Naming
- ✅ Aggregates são substantivos no singular (Business, Customer, Appointment)
- ✅ Domain Events são verbos no passado (BusinessCreated, AppointmentConfirmed)
- ✅ Commands são verbos no imperativo (CreateAppointment, CancelReservation)

### 6. Boundaries
- ✅ Aggregates são boundaries de consistência
- ✅ Referências entre Aggregates são por ID, não por objeto
- ✅ Agregados grandes são divididos em múltiplos Aggregates

### 7. Lifecycle
- ✅ Aggregates têm lifecycle claro (Created → Active → Archived/Deleted)
- ✅ Eventos marcam transições de estado
- ✅ Estado é sempre consistente após evento

---

## Exemplo de Fluxo Completo

```
1. Customer inicia Conversation
2. Conversation emite ConversationStarted
3. Reasoning interpreta intenção (IntentDetected)
4. Application Service consulta Availability
5. Customer escolhe slot
6. Application Service cria Reservation (ATIVA)
7. Reservation emite ReservationCreated
8. Business confirma (via humano ou auto)
9. Reservation é confirmada → ReservationConfirmed
10. Appointment é criado a partir da Reservation
11. Appointment emite AppointmentCreated
12. Conversation notifica Customer
```

---

## Notas

- Este documento é a fonte de verdade para o modelo de domínio
- Qualquer mudança no domínio deve ser refletida aqui primeiro
- Aggregates são a base da arquitetura do Troquim
- A arquitetura é baseada em DDD (Domain-Driven Design)
- Event-Driven Architecture é o padrão de comunicação

**Última atualização**: 2026-07-02