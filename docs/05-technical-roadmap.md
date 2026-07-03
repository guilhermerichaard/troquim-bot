# Technical Roadmap - Troquim Bot

## 📊 Auditoria Completa do Projeto

### 1. Arquitetura

**Padrão Adotado:** Arquitetura em camadas com DDD (Domain-Driven Design) simplificado

**Estrutura de Pacotes:**
```
com.troquim_bot
├── application/          # Application Services (orquestração de use cases)
│   ├── professional/
│   ├── service/
│   ├── customer/
│   ├── business/
│   ├── availability/
│   ├── reservation/
│   └── conversation/
├── domain/               # (NÃO IMPLEMENTADO - domínio misturado com application)
├── business/             # Aggregate: Business
├── professional/         # Aggregate: Professional
├── service/              # Aggregate: Service
├── customer/             # Aggregate: Customer
├── availability/         # Aggregate: Availability
├── reservation/          # Aggregate: Reservation
├── repository/           # Interfaces de Repository (abstrações)
├── controller/           # REST Controllers
├── ai/                   # Camada de Inteligência Artificial
│   ├── config/
│   ├── intent/
│   ├── llm/
│   ├── memory/
│   ├── prompt/
│   └── tool/
├── chatbot/              # Chatbot integration
├── conversation/         # Aggregate: Conversation
├── schedule/             # Agendamento (módulo separado)
├── common/               # Value Objects compartilhados
└── webhook/              # Webhook para integração WhatsApp
```

**Problemas Identificados:**
- ❌ Mistura entre domain e application (aggregates no root do projeto)
- ❌ Não há camada de domain separada
- ❌ Não há camada de infrastructure separada
- ❌ Não há camada de interfaces separada

### 2. DDD (Domain-Driven Design)

**Pontos Positivos:**
- ✅ Aggregates bem definidos com invariantes
- ✅ Value Objects (TimeSlot, Money, CustomerName, PhoneNumber)
- ✅ Repositories como interfaces puras (sem dependência de frameworks)
- ✅ Application Services com responsabilidades claras

**Pontos Negativos:**
- ❌ Aggregates no pacote raiz (deveriam estar em `domain`)
- ❌ Falta de Domain Events
- ❌ Falta de Factory methods
- ❌ Falta de validação cruzada entre aggregates
- ❌ Status incompletos (CustomerStatus falta BLOQUEADO, ReservationStatus falta CONFIRMADA/EXPIRADA)

### 3. Application Layer

**Pontos Positivos:**
- ✅ Services com injeção de dependência via construtor
- ✅ Validações de entrada
- ✅ Métodos de negócio bem definidos

**Problemas Identificados:**
- ❌ Construtores padrão criam dependências (Service Locator anti-pattern)
- ❌ Falta de `@Transactional`
- ❌ Falta de logging
- ❌ Falta de validação de existência de entidades relacionadas
- ❌ Nomenclatura inconsistente (português vs inglês)

### 4. Dependências

**Principais Dependências (pom.xml):**
- Spring Boot 4.1.0
- Spring Web
- Spring Data JPA
- Spring Security
- H2 Database
- Lombok
- Jackson

**Problemas:**
- ❌ Falta SpringDoc/OpenAPI para documentação
- ❌ Falta Bean Validation (JSR-380)
- ❌ Falta dependência de teste para Mockito

### 5. Acoplamentos

**Problemas de Acoplamento:**
- ❌ Application Services conhecem implementações concretas (InMemory*)
- ❌ Controllers têm lógica de tratamento de exceções
- ❌ AI Layer acoplado ao Ollama (hardcoded URL)
- ❌ Falta de abstração para serviços externos

### 6. Technical Debt

| Prioridade | Issue | Impacto |
|------------|-------|---------|
| 🔴 CRÍTICO | Falta validação cruzada Business ↔ Professional | Dados inconsistentes |
| 🔴 CRÍTICO | Falta validação cruzada Business ↔ Availability | Horários inválidos |
| 🔴 CRÍTICO | Falta validação cruzada Customer/Professional/Service ↔ Reservation | Reservas inválidas |
| 🔴 CRÍTICO | Falta Domain Events | Sistema event-driven quebrado |
| 🟡 IMPORTANTE | Falta `@Transactional` | Consistência de dados |
| 🟡 IMPORTANTE | Falta `@ControllerAdvice` | Erros mal tratados |
| 🟡 IMPORTANTE | Falta testes de Application Service | Cobertura insuficiente |
| 🟢 MELHORIA | Código boilerplate nos repositories | Manutenção difícil |
| 🟢 MELHORIA | Falta paginação | Performance |
| 🟢 MELHORIA | Falta logging | Debug difícil |

### 7. Testes

**Cobertura Atual:**
- ✅ Testes de Controller (ProfessionalControllerTest)
- ❌ Falta testes de Application Service
- ❌ Falta testes de integração
- ❌ Falta testes de domínio (aggregates)
- ❌ Falta testes de validação de conflitos

### 8. Organização dos Pacotes

**Problemas:**
- ❌ Duplicidade: `customer` e `Cliente` (model antigo)
- ❌ Duplicidade: `service` e `Service` (módulo antigo)
- ❌ Falta de organização clara entre domain/application/infrastructure
- ❌ AI Layer misturado com core domain

---

# Technical Roadmap

## MVP Roadmap

### FEATURE 10 — Conversation Orchestrator Completion

**Objetivo:** Completar o ConversationOrchestrator para processar mensagens do WhatsApp e orquestrar o fluxo de agendamento.

**Motivação:**
- Webhook recebe mensagens mas não processa
- Fluxo de agendamento via WhatsApp incompleto
- IntentService existe mas não é usado no fluxo principal

**Risco:** Médio (integração com múltiplos sistemas)

**Critério de Aceite:**
- [ ] ConversationOrchestrator.processarMensagem() implementado
- [ ] Integração com IntentService funcional
- [ ] Integração com EvolutionService funcional
- [ ] Fluxo de agendamento via WhatsApp funcional
- [ ] Testes de integração criados

---

### FEATURE 11 — Intent Engine Without AI

**Objetivo:** Implementar Intent Engine com regras fixas (sem dependência de LLM) para o MVP.

**Motivação:**
- IntentService já existe com classificação básica
- Dependência de Ollama pode falhar no MVP
- Necessário para demo sem infraestrutura AI

**Risco:** Baixo

**Critério de Aceite:**
- [ ] IntentService sem dependência de LLM
- [ ] Regras de intent para agendamento funcionais
- [ ] IntentType.AGENDAMENTO integrado ao fluxo
- [ ] Testes de intent criados

---

### FEATURE 12 — Evolution API End-to-End

**Objetivo:** Completar integração com Evolution API para envio e recebimento de mensagens WhatsApp.

**Motivação:**
- EvolutionService existe mas é limitado
- Falta abstração para múltiplas instâncias
- Necessário para comunicação bidirecional

**Risco:** Médio

**Critério de Aceite:**
- [ ] EvolutionService com abstração de instância
- [ ] Recebimento de webhook funcional
- [ ] Envio de mensagem funcional
- [ ] Tratamento de erros da API
- [ ] Testes de integração criados

---

### FEATURE 13 — WhatsApp Scheduling MVP

**Objetivo:** Implementar fluxo completo de agendamento via WhatsApp.

**Motivação:**
- MVP precisa de fluxo de agendamento funcional
- Customer → Service → Professional → Time → Confirmation
- Baseado na especificação do Appointment MVP

**Risco:** Alto

**Critério de Aceite:**
- [ ] Fluxo: saudação → serviços → profissional → horário → confirmação
- [ ] Integração com ReservationApplicationService
- [ ] Integração com AvailabilityApplicationService
- [ ] Integração com ServiceApplicationService
- [ ] Integração com CustomerApplicationService
- [ ] Testes de fluxo criados

---

### FEATURE 14 — MVP Hardening

**Objetivo:** Reforçar o MVP com validações críticas e tratamento de erros.

**Motivação:**
- Validações básicas existem mas precisam ser robustas
- Necessário para demo estável
- Prevenir dados inconsistentes

**Risco:** Médio

**Critério de Aceite:**
- [ ] Validação de conflito de horários
- [ ] Validação de entidades ativas
- [ ] Tratamento de exceções no fluxo
- [ ] Mensagens de erro amigáveis
- [ ] Testes de cenários de erro

---

### FEATURE 15 — Pilot Demo Readiness

**Objetivo:** Preparar o sistema para demo piloto com dados reais.

**Motivação:**
- Necessário para validação com clientes
- Dados de exemplo configuráveis
- Documentação de setup

**Risco:** Baixo

**Critério de Aceite:**
- [ ] Script de setup de dados iniciais
- [ ] README de demo atualizado
- [ ] Configuração de ambiente documentada
- [ ] Checklist de validação pré-demo

---

## Engineering Backlog

### Domain Events

**Objetivo:** Implementar Domain Events para todos os Aggregates.

**Motivação:**
- Sistema event-driven não funciona atualmente
- Impossibilidade de auditoria e tracking
- Integração com outros sistemas quebrada

**Risco:** Baixo

**Critério de Aceite:**
- [ ] EventPublisher interface criada
- [ ] EventListener interface criada
- [ ] Events para Customer, Professional, Service, Availability, Reservation
- [ ] Testes de eventos criados

---

### Cross-Aggregate Validation

**Objetivo:** Implementar validação cruzada entre Aggregates.

**Motivação:**
- Professionals podem ser criados sem Business ativo
- Availability pode estar fora do horário comercial
- Reservations podem referenciar entidades inexistentes

**Risco:** Médio

**Critério de Aceite:**
- [ ] Professional valida Business ativo
- [ ] Availability valida BusinessHours
- [ ] Reservation valida Customer/Professional/Service ativos
- [ ] Service exige BusinessId
- [ ] Testes de validação cruzada

---

### Transaction Management

**Objetivo:** Adicionar `@Transactional` nos Application Services.

**Motivação:**
- Consistência de dados não garantida
- Problemas em ambiente concorrente

**Risco:** Médio

**Critério de Aceite:**
- [ ] `@Transactional` em todos os Application Services
- [ ] Testes de transação criados

---

### Exception Handling

**Objetivo:** Criar `@ControllerAdvice` global.

**Motivação:**
- Exceções retornam erro 500 genérico
- Difícil debugging

**Risco:** Baixo

**Critério de Aceite:**
- [ ] `@ControllerAdvice` global criado
- [ ] Exceções mapeadas para HTTP codes
- [ ] Mensagens de erro claras

---

### OpenAPI

**Objetivo:** Documentar API com SpringDoc/OpenAPI.

**Motivação:**
- API não documentada automaticamente
- Swagger/OpenAPI indisponível

**Risco:** Baixo

**Critério de Aceite:**
- [ ] SpringDoc dependency adicionada
- [ ] `@Operation` em todos os endpoints
- [ ] Swagger UI acessível

---

### Pagination

**Objetivo:** Adicionar paginação em repositories.

**Motivação:**
- Performance degradada com muitos registros
- Não escalável

**Risco:** Médio

**Critério de Aceite:**
- [ ] `findAll(Pageable pageable)` adicionado
- [ ] `count()` adicionado
- [ ] Controllers atualizados

---

### Soft Delete

**Objetivo:** Implementar padrão de soft delete.

**Motivação:**
- Perda de histórico
- Quebra de integridade referencial

**Risco:** Médio

**Critério de Aceite:**
- [ ] Método delete() deprecated
- [ ] Queries filtram por status ATIVO
- [ ] Testes de soft delete criados

---

### Repository Refactoring

**Objetivo:** Eliminar código boilerplate nos repositories.

**Motivação:**
- Código boilerplate
- Manutenção mais difícil

**Risco:** Médio

**Critério de Aceite:**
- [ ] AbstractInMemoryRepository<T, ID> criado
- [ ] Todos os repositories refatorados
- [ ] Testes de repository base criados

---

### Configuration Properties

**Objetivo:** Externalizar configurações hardcoded.

**Motivação:**
- Difícil configuração para diferentes negócios
- Não aproveita Spring Configuration

**Risco:** Baixo

**Critério de Aceite:**
- [ ] `@ConfigurationProperties` para horários padrão
- [ ] `@ConfigurationProperties` para AI config
- [ ] application.yml exemplos criados

---

## 📊 Estimativa

| Trilha | Features | Horas Estimadas |
|--------|----------|-----------------|
| MVP Roadmap | 6 | 40-60 horas |
| Engineering Backlog | 9 | 50-70 horas |
| **Total** | **15** | **90-130 horas** |
