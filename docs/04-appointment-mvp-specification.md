# Appointment MVP - Technical Specification

## Overview

This document defines the technical specification for the **Appointment** feature in the Troquim platform. The Appointment aggregate represents a confirmed and persistent booking between a customer and a business service.

---

## 1. AGGREGATE: Appointment

### Location
`src/main/java/com/troquim_bot/appointment/Appointment.java`

### Concept
Agendamento confirmado e persistente. Representa o compromisso final entre cliente, profissional e serviço.

### Aggregate Root
`Appointment`

### Internal Entities
None

### Value Objects (Internal)
- `AppointmentId` - Identificador único do agendamento
- `AppointmentStatus` - Estado do agendamento (PENDENTE, CONFIRMADO, RECUSADO, CANCELADO, CONCLUIDO)
- `AppointmentDateTime` - Data e horário do agendamento (com duração)
- `ServiceType` - Tipo de serviço (reference to Service)
- `ServiceDuration` - Duração do serviço (reference to Service)
- `ServicePrice` - Preço do serviço (reference to Service)
- `AppointmentNotes` - Observações do agendamento

### References to Other Aggregates (by ID)
- `CustomerId` - Referência ao cliente
- `ProfessionalId` - Referência ao profissional
- `ServiceId` - Referência ao serviço
- `AvailabilityId` - Referência à disponibilidade (opcional, para rastreamento)
- `ReservationId` - Referência à reserva que originou o agendamento (opcional)

### Invariants Protected
1. Appointment deve estar vinculada a um Customer existente
2. Appointment deve estar vinculada a um Professional existente
3. Appointment deve estar vinculada a um Service existente
4. Appointment não pode ser criado em data/hora no passado
5. Appointment não pode ser modificado após X horas de antecedência (regra configurável)
6. Appointment não pode ter conflito de horário com outro agendamento do mesmo profissional
7. Appointment deve ter status válido para transições permitidas
8. Appointment não pode ser deletado se estiver CONCLUIDO ou CANCELADO (apenas inativação lógica)

### Domain Events
- `AppointmentCreated` - Quando um agendamento é criado
- `AppointmentConfirmed` - Quando um agendamento é confirmado
- `AppointmentRejected` - Quando um agendamento é rejeitado
- `AppointmentCancelled` - Quando um agendamento é cancelado
- `AppointmentCompleted` - Quando um agendamento é concluído
- `AppointmentRescheduled` - Quando um agendamento é reagendado

---

## 2. VALUE OBJECTS

### 2.1 AppointmentId
**Location:** `src/main/java/com/troquim_bot/appointment/AppointmentId.java`

```java
public class AppointmentId {
    private final UUID value;
    
    public static AppointmentId from(UUID value) { ... }
    public static AppointmentId generate() { ... }
    public UUID getValue() { ... }
}
```

### 2.2 AppointmentStatus
**Location:** `src/main/java/com/troquim_bot/appointment/AppointmentStatus.java`

```java
public enum AppointmentStatus {
    PENDENTE,      // Aguardando confirmação
    CONFIRMADO,    // Confirmado pelo profissional
    RECUSADO,      // Recusado pelo profissional
    CANCELADO,    // Cancelado pelo cliente ou profissional
    CONCLUIDO      // Serviço realizado
}
```

**Transitions:**
- PENDENTE → CONFIRMADO (confirmação)
- PENDENTE → RECUSADO (recusa)
- PENDENTE → CANCELADO (cancelamento)
- CONFIRMADO → CANCELADO (cancelamento)
- CONFIRMADO → CONCLUIDO (conclusão)
- CANCELADO → PENDENTE (reajuste - raro)

### 2.3 AppointmentDateTime
**Location:** `src/main/java/com/troquim_bot/appointment/AppointmentDateTime.java`

**Reutilizable Value Objects:**
- `LocalDate` (Java standard) - Data do agendamento
- `LocalTime` (Java standard) - Horário de início
- `ServiceDuration` (existing) - Duração do serviço

**New Value Object:**
```java
public class AppointmentDateTime {
    private final LocalDateTime startDateTime;
    private final ServiceDuration duration;
    
    // Factory methods
    public static AppointmentDateTime of(LocalDateTime start, ServiceDuration duration) { ... }
    public static AppointmentDateTime of(LocalDate date, LocalTime time, ServiceDuration duration) { ... }
    
    // Derived properties
    public LocalDateTime getStartDateTime() { ... }
    public LocalDateTime getEndDateTime() { ... }
    public LocalDate getDate() { ... }
    public LocalTime getStartTime() { ... }
    public LocalTime getEndTime() { ... }
    public ServiceDuration getDuration() { ... }
    
    // Business behavior
    public boolean isPast() { ... }
    public boolean isToday() { ... }
    public boolean isFuture() { ... }
    public boolean isWithinBusinessHours(BusinessHours hours, DiaSemana dayOfWeek) { ... }
}
```

### 2.4 AppointmentNotes
**Location:** `src/main/java/com/troquim_bot/appointment/AppointmentNotes.java`

```java
public class AppointmentNotes {
    private static final int MAX_LENGTH = 500;
    
    private final String value;
    
    public AppointmentNotes(String value) {
        this.value = value != null ? value.trim() : null;
        if (this.value != null && this.value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Observações não podem ter mais de " + MAX_LENGTH + " caracteres");
        }
    }
    
    public String getValue() { ... }
    public boolean isEmpty() { ... }
}
```

---

## 3. REPOSITORY: AppointmentRepository

### Location
`src/main/java/com/troquim_bot/repository/AppointmentRepository.java`

### Interface
```java
public interface AppointmentRepository {
    
    /**
     * Salva um Appointment (cria ou atualiza).
     */
    Appointment save(Appointment appointment);
    
    /**
     * Busca um Appointment por ID.
     * @return Appointment se encontrado, null caso contrário
     */
    Appointment findById(AppointmentId id);
    
    /**
     * Verifica se existe um Appointment com o ID informado.
     */
    boolean exists(AppointmentId id);
    
    /**
     * Busca todos os Appointments.
     */
    List<Appointment> findAll();
    
    /**
     * Busca Appointments por Customer ID.
     */
    List<Appointment> findByCustomerId(CustomerId customerId);
    
    /**
     * Busca Appointments por Professional ID.
     */
    List<Appointment> findByProfessionalId(ProfessionalId professionalId);
    
    /**
     * Busca Appointments por Service ID.
     */
    List<Appointment> findByServiceId(ServiceId serviceId);
    
    /**
     * Busca Appointments por data.
     */
    List<Appointment> findByDate(LocalDate date);
    
    /**
     * Busca Appointments de um profissional em uma data específica.
     */
    List<Appointment> findByProfessionalIdAndDate(ProfessionalId professionalId, LocalDate date);
    
    /**
     * Busca Appointments com status específico.
     */
    List<Appointment> findByStatus(AppointmentStatus status);
    
    /**
     * Busca Appointments futuros de um Customer.
     */
    List<Appointment> findFutureAppointmentsByCustomerId(CustomerId customerId);
    
    /**
     * Busca Appointments futuros de um Professional.
     */
    List<Appointment> findFutureAppointmentsByProfessionalId(ProfessionalId professionalId);
    
    /**
     * Remove um Appointment por ID.
     */
    void delete(AppointmentId id);
}
```

### Implementation
`src/main/java/com/troquim_bot/repository/InMemoryAppointmentRepository.java`

---

## 4. APPLICATION SERVICE: AppointmentApplicationService

### Location
`src/main/java/com/troquim_bot/application/appointment/AppointmentApplicationService.java`

### Dependencies
- `AppointmentRepository`
- `CustomerRepository`
- `ProfessionalRepository`
- `ServiceRepository`
- `ReservationRepository`
- `AvailabilityRepository`

### Methods
```java
@Service
public class AppointmentApplicationService {
    
    /**
     * Cria um novo Appointment a partir de uma Reservation confirmada.
     * Transição: Reservation CONFIRMADA → Appointment PENDENTE
     */
    public Appointment criarAppointment(
        CustomerId customerId,
        ProfessionalId professionalId,
        ServiceId serviceId,
        LocalDate date,
        LocalTime startTime,
        String notes
    ) { ... }
    
    /**
     * Cria um Appointment diretamente (sem Reservation).
     * Usado para agendamentos manuais.
     */
    public Appointment criarAppointmentDireto(
        CustomerId customerId,
        ProfessionalId professionalId,
        ServiceId serviceId,
        LocalDate date,
        LocalTime startTime,
        String notes
    ) { ... }
    
    /**
     * Busca um Appointment por ID.
     */
    public Optional<Appointment> buscarPorId(AppointmentId id) { ... }
    
    /**
     * Busca todos os Appointments.
     */
    public List<Appointment> buscarTodos() { ... }
    
    /**
     * Busca Appointments por Customer ID.
     */
    public List<Appointment> buscarPorCustomerId(CustomerId customerId) { ... }
    
    /**
     * Busca Appointments por Professional ID.
     */
    public List<Appointment> buscarPorProfessionalId(ProfessionalId professionalId) { ... }
    
    /**
     * Busca Appointments por data.
     */
    public List<Appointment> buscarPorData(LocalDate date) { ... }
    
    /**
     * Busca Appointments futuros de um Customer.
     */
    public List<Appointment> buscarFuturosPorCustomerId(CustomerId customerId) { ... }
    
    /**
     * Busca Appointments futuros de um Professional.
     */
    public List<Appointment> buscarFuturosPorProfessionalId(ProfessionalId professionalId) { ... }
    
    /**
     * Confirma um Appointment (transição PENDENTE → CONFIRMADO).
     */
    public Appointment confirmarAppointment(AppointmentId id) { ... }
    
    /**
     * Recusa um Appointment (transição PENDENTE → RECUSADO).
     */
    public Appointment recusarAppointment(AppointmentId id) { ... }
    
    /**
     * Cancela um Appointment (transição CONFIRMADO/PENDENTE → CANCELADO).
     */
    public Appointment cancelarAppointment(AppointmentId id) { ... }
    
    /**
     * Marca um Appointment como concluído (transição CONFIRMADO → CONCLUIDO).
     */
    public Appointment concluirAppointment(AppointmentId id) { ... }
    
    /**
     * Reagenda um Appointment (cria novo, cancela o antigo).
     */
    public Appointment reagendarAppointment(
        AppointmentId id,
        LocalDate novaData,
        LocalTime novoStartTime,
        String novasObservacoes
    ) { ... }
    
    /**
     * Atualiza observações do Appointment.
     * Só permitido se PENDENTE ou CONFIRMADO e dentro do prazo.
     */
    public Appointment atualizarObservacoes(AppointmentId id, String observacoes) { ... }
    
    /**
     * Verifica se existe Appointment com ID informado.
     */
    public boolean existeAppointment(AppointmentId id) { ... }
}
```

---

## 5. DTOS

### 5.1 CreateAppointmentRequest
**Location:** `src/main/java/com/troquim_bot/controller/dto/CreateAppointmentRequest.java`

```java
public class CreateAppointmentRequest {
    @JsonProperty("customerId")
    private String customerId;
    
    @JsonProperty("professionalId")
    private String professionalId;
    
    @JsonProperty("serviceId")
    private String serviceId;
    
    @JsonProperty("date")
    private String date;  // ISO format: YYYY-MM-DD
    
    @JsonProperty("startTime")
    private String startTime;  // ISO format: HH:MM
    
    @JsonProperty("notes")
    private String notes;
}
```

### 5.2 UpdateAppointmentRequest
**Location:** `src/main/java/com/troquim_bot/controller/dto/UpdateAppointmentRequest.java`

```java
public class UpdateAppointmentRequest {
    @JsonProperty("notes")
    private String notes;
}
```

### 5.3 RescheduleAppointmentRequest
**Location:** `src/main/java/com/troquim_bot/controller/dto/RescheduleAppointmentRequest.java`

```java
public class RescheduleAppointmentRequest {
    @JsonProperty("date")
    private String date;
    
    @JsonProperty("startTime")
    private String startTime;
    
    @JsonProperty("notes")
    private String notes;
}
```

### 5.4 AppointmentResponse
**Location:** `src/main/java/com/troquim_bot/controller/dto/AppointmentResponse.java`

```java
public class AppointmentResponse {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("customerId")
    private String customerId;
    
    @JsonProperty("customerName")
    private String customerName;
    
    @JsonProperty("professionalId")
    private String professionalId;
    
    @JsonProperty("professionalName")
    private String professionalName;
    
    @JsonProperty("serviceId")
    private String serviceId;
    
    @JsonProperty("serviceName")
    private String serviceName;
    
    @JsonProperty("serviceDuration")
    private String serviceDuration;
    
    @JsonProperty("servicePrice")
    private String servicePrice;
    
    @JsonProperty("date")
    private String date;
    
    @JsonProperty("startTime")
    private String startTime;
    
    @JsonProperty("endTime")
    private String endTime;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("notes")
    private String notes;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
    
    public static AppointmentResponse from(Appointment appointment) { ... }
}
```

---

## 6. CONTROLLER: AppointmentController

### Location
`src/main/java/com/troquim_bot/controller/AppointmentController.java`

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/appointments` | Lista todos os Appointments |
| `GET` | `/appointments/{id}` | Busca Appointment por ID |
| `GET` | `/appointments/customer/{customerId}` | Busca Appointments por Customer |
| `GET` | `/appointments/professional/{professionalId}` | Busca Appointments por Professional |
| `GET` | `/appointments/professional/{professionalId}/date/{date}` | Busca Appointments por Professional e data |
| `GET` | `/appointments/date/{date}` | Busca Appointments por data |
| `GET` | `/appointments/status/{status}` | Busca Appointments por status |
| `POST` | `/appointments` | Cria novo Appointment |
| `POST` | `/appointments/from-reservation/{reservationId}` | Cria Appointment a partir de Reservation |
| `PUT` | `/appointments/{id}/confirm` | Confirma Appointment |
| `PUT` | `/appointments/{id}/reject` | Recusa Appointment |
| `PUT` | `/appointments/{id}/cancel` | Cancela Appointment |
| `PUT` | `/appointments/{id}/complete` | Marca Appointment como concluído |
| `PUT` | `/appointments/{id}/reschedule` | Reagenda Appointment |
| `PUT` | `/appointments/{id}` | Atualiza observações do Appointment |
| `DELETE` | `/appointments/{id}` | Remove Appointment (apenas PENDENTE) |

### Implementation Details

```java
@RestController
@RequestMapping("/appointments")
public class AppointmentController {
    
    private final AppointmentApplicationService appointmentApplicationService;
    
    // GET /appointments
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getAllAppointments() { ... }
    
    // GET /appointments/{id}
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getAppointmentById(@PathVariable String id) { ... }
    
    // GET /appointments/customer/{customerId}
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByCustomer(@PathVariable String customerId) { ... }
    
    // GET /appointments/professional/{professionalId}
    @GetMapping("/professional/{professionalId}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByProfessional(@PathVariable String professionalId) { ... }
    
    // GET /appointments/professional/{professionalId}/date/{date}
    @GetMapping("/professional/{professionalId}/date/{date}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByProfessionalAndDate(
        @PathVariable String professionalId, 
        @PathVariable String date
    ) { ... }
    
    // GET /appointments/date/{date}
    @GetMapping("/date/{date}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByDate(@PathVariable String date) { ... }
    
    // GET /appointments/status/{status}
    @GetMapping("/status/{status}")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByStatus(@PathVariable String status) { ... }
    
    // POST /appointments
    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(@RequestBody CreateAppointmentRequest request) { ... }
    
    // POST /appointments/from-reservation/{reservationId}
    @PostMapping("/from-reservation/{reservationId}")
    public ResponseEntity<AppointmentResponse> createAppointmentFromReservation(@PathVariable String reservationId) { ... }
    
    // PUT /appointments/{id}/confirm
    @PutMapping("/{id}/confirm")
    public ResponseEntity<AppointmentResponse> confirmAppointment(@PathVariable String id) { ... }
    
    // PUT /appointments/{id}/reject
    @PutMapping("/{id}/reject")
    public ResponseEntity<AppointmentResponse> rejectAppointment(@PathVariable String id) { ... }
    
    // PUT /appointments/{id}/cancel
    @PutMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancelAppointment(@PathVariable String id) { ... }
    
    // PUT /appointments/{id}/complete
    @PutMapping("/{id}/complete")
    public ResponseEntity<AppointmentResponse> completeAppointment(@PathVariable String id) { ... }
    
    // PUT /appointments/{id}/reschedule
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> rescheduleAppointment(
        @PathVariable String id,
        @RequestBody RescheduleAppointmentRequest request
    ) { ... }
    
    // PUT /appointments/{id}
    @PutMapping("/{id}")
    public ResponseEntity<AppointmentResponse> updateAppointment(
        @PathVariable String id,
        @RequestBody UpdateAppointmentRequest request
    ) { ... }
    
    // DELETE /appointments/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAppointment(@PathVariable String id) { ... }
}
```

---

## 7. STATUS: AppointmentStatus

### Values
| Status | Description | Transitions From | Transitions To |
|--------|-------------|-----------------|----------------|
| `PENDENTE` | Aguardando confirmação | CANCELADO (reajuste) | CONFIRMADO, RECUSADO, CANCELADO |
| `CONFIRMADO` | Confirmado pelo profissional | PENDENTE | CANCELADO, CONCLUIDO |
| `RECUSADO` | Recusado pelo profissional | PENDENTE | - (terminal) |
| `CANCELADO` | Cancelado | PENDENTE, CONFIRMADO | - (terminal) |
| `CONCLUIDO` | Serviço realizado | CONFIRMADO | - (terminal) |

### Business Rules
- Status terminal: RECUSADO, CANCELADO, CONCLUIDO
- Apenas Appointments PENDENTE ou CONFIRMADO podem ser cancelados
- Apenas Appointments CONFIRMADO podem ser concluídos
- Reagendamento cria um novo Appointment e cancela o antigo

---

## 8. BUSINESS RULES

### 8.1 Validation Rules

1. **Validação de Data/Hora**
   - Appointment não pode ser criado com data/hora no passado
   - Appointment não pode ser criado fora do horário de funcionamento do Business
   - Appointment não pode ser criado em dia não funcional do Business

2. **Validação de Conflito**
   - Não permitir dois Appointments com mesmo Professional no mesmo horário
   - Não permitir sobreposição de horários (startTime < other.endTime && other.startTime < endTime)
   - Verificar conflito com Appointments existentes antes de criar

3. **Validação de Dependências**
   - Customer deve existir e estar ATIVO
   - Professional deve existir e estar ATIVO
   - Service deve existir e estar ATIVO
   - Reservation (se informada) deve existir e estar CONFIRMADA

4. **Validação de Modificação**
   - Apenas Appointments PENDENTE/CONFIRMADO podem ser modificados
   - Modificação só permitida até X horas antes (configurável, padrão: 24h)
   - Reagendamento requer verificação de conflito

### 8.2 State Transition Rules

| From | To | Condition |
|------|-----|-----------|
| PENDENTE | CONFIRMADO | Professional confirma |
| PENDENTE | RECUSADO | Professional recusa |
| PENDENTE | CANCELADO | Customer ou Professional cancela |
| CONFIRMADO | CANCELADO | Customer ou Professional cancela |
| CONFIRMADO | CONCLUIDO | Serviço realizado |

### 8.3 Time-Based Rules

- **Expiração de reagendamento:** Não permitir reagendamento após 24h do horário marcado
- **Cancelamento:** Permitir cancelamento até 24h antes (configurável)
- **Conclusão automática:** Marcar como CONCLUIDO após a data/hora de término

---

## 9. DEPENDENCIES

### 9.1 Customer
- **Type:** Aggregate Root
- **Reference:** `CustomerId` (by ID)
- **Usage:** Identificar o cliente que fez o agendamento
- **Validation:** Customer deve existir e estar ATIVO

### 9.2 Professional
- **Type:** Aggregate Root
- **Reference:** `ProfessionalId` (by ID)
- **Usage:** Identificar o profissional responsável
- **Validation:** Professional deve existir e estar ATIVO

### 9.3 Service
- **Type:** Aggregate Root
- **Reference:** `ServiceId` (by ID)
- **Usage:** Identificar o serviço a ser realizado
- **Validation:** Service deve existir e estar ATIVO
- **Reutilizable VOs:** `ServiceDuration`, `Money` (preço)

### 9.4 Availability
- **Type:** Aggregate Root
- **Reference:** `AvailabilityId` (by ID)
- **Usage:** Rastrear qual disponibilidade foi usada
- **Validation:** Opcional - usado apenas para auditoria

### 9.5 Reservation
- **Type:** Aggregate Root
- **Reference:** `ReservationId` (by ID)
- **Usage:** Criar Appointment a partir de Reservation confirmada
- **Validation:** Reservation deve existir e estar CONFIRMADA

---

## 10. REUSABLE VALUE OBJECTS

### Existing Value Objects to Reuse

| Value Object | Location | Usage in Appointment |
|--------------|----------|---------------------|
| `PhoneNumber` | `common/valueobject/PhoneNumber.java` | Não usado diretamente (via Customer) |
| `Money` | `common/valueobject/Money.java` | Preço do serviço |
| `TimeSlot` | `common/valueobject/TimeSlot.java` | Verificação de conflitos de horário |
| `CustomerName` | `common/valueobject/CustomerName.java` | Nome do cliente (via Customer) |
| `ServiceDuration` | `service/ServiceDuration.java` | Duração do serviço |
| `BusinessHours` | `business/BusinessHours.java` | Validação de horário comercial |
| `DiaSemana` | `business/DiaSemana.java` | Validação de dia útil |

### New Value Objects Required

| Value Object | Location | Purpose |
|--------------|----------|---------|
| `AppointmentId` | `appointment/AppointmentId.java` | Identificador único |
| `AppointmentDateTime` | `appointment/AppointmentDateTime.java` | Data/hora com duração |
| `AppointmentNotes` | `appointment/AppointmentNotes.java` | Observações validadas |

---

## 11. FILE STRUCTURE

```
src/main/java/com/troquim_bot/
├── appointment/
│   ├── Appointment.java
│   ├── AppointmentId.java
│   ├── AppointmentStatus.java
│   ├── AppointmentDateTime.java
│   └── AppointmentNotes.java
├── application/
│   └── appointment/
│       └── AppointmentApplicationService.java
├── controller/
│   ├── AppointmentController.java
│   └── dto/
│       ├── CreateAppointmentRequest.java
│       ├── UpdateAppointmentRequest.java
│       ├── RescheduleAppointmentRequest.java
│       └── AppointmentResponse.java
└── repository/
    ├── AppointmentRepository.java
    └── InMemoryAppointmentRepository.java
```

---

## 12. INTEGRATION FLOW

### 12.1 Criação via Reservation
```
1. Customer seleciona slot disponível
2. Reservation é criada (ATIVA)
3. Professional confirma a Reservation
4. ReservationConfirmed event é emitido
5. AppointmentApplicationService cria Appointment a partir da Reservation
6. AppointmentCreated event é emitido
7. Appointment inicia com status PENDENTE
```

### 12.2 Criação Direta
```
1. Customer/Professional cria Appointment diretamente
2. AppointmentApplicationService valida:
   - Customer existe e está ATIVO
   - Professional existe e está ATIVO
   - Service existe e está ATIVO
   - Não há conflito de horário
   - Dentro do horário de funcionamento
3. Appointment é criado com status PENDENTE
4. AppointmentCreated event é emitido
```

### 12.3 Confirmação
```
1. Professional confirma Appointment
2. Status muda de PENDENTE para CONFIRMADO
3. AppointmentConfirmed event é emitido
```

### 12.4 Cancelamento
```
1. Customer/Professional solicita cancelamento
2. Verifica prazo de antecedência
3. Status muda para CANCELADO
4. AppointmentCancelled event é emitido
```

### 12.5 Reagendamento
```
1. Customer solicita reagendamento
2. Verifica prazo e disponibilidade
3. Cria novo Appointment com nova data/hora
4. Cancela Appointment antigo
5. AppointmentRescheduled event é emitido
```

---

## 13. VALIDATION MATRIX

| Operation | Validations |
|-----------|-------------|
| `criarAppointment` | Customer exists & ATIVO, Professional exists & ATIVO, Service exists & ATIVO, No time conflict, Not in past, Within business hours |
| `confirmarAppointment` | Appointment exists, Status = PENDENTE |
| `recusarAppointment` | Appointment exists, Status = PENDENTE |
| `cancelarAppointment` | Appointment exists, Status = PENDENTE or CONFIRMADO, Within cancellation window |
| `concluirAppointment` | Appointment exists, Status = CONFIRMADO |
| `reagendarAppointment` | Appointment exists, Status = PENDENTE or CONFIRMADO, Within reschedule window, No time conflict |
| `atualizarObservacoes` | Appointment exists, Status = PENDENTE or CONFIRMADO, Within modification window |

---

## 14. ERROR HANDLING

### Business Exceptions
- `CustomerNotFoundException` - Customer não encontrado
- `ProfessionalNotFoundException` - Professional não encontrado
- `ServiceNotFoundException` - Service não encontrado
- `AppointmentNotFoundException` - Appointment não encontrado
- `TimeConflictException` - Conflito de horário
- `InvalidStatusTransitionException` - Transição de status inválida
- `ModificationNotAllowedException` - Modificação não permitida
- `PastAppointmentException` - Appointment no passado
- `BusinessHoursViolationException` - Fora do horário comercial

---

## 15. NOTES

- Esta especificação segue os padrões DDD estabelecidos no projeto
- Todos os Aggregates emitem Domain Events
- Referências entre Aggregates são feitas por ID, não por objeto
- A camada de Application Service coordena validações e transações
- Controllers são apenas adaptadores de interface, sem lógica de negócio
- Value Objects são imutáveis e auto-validados
- Status é um enum com transições bem definidas