# Módulo Conversation

## Responsabilidade

O módulo Conversation é o **motor de conversa do WhatsApp** do Troquim Bot. Sua responsabilidade é:

- Gerenciar o ciclo de vida de conversas (criar, avançar, voltar, resetar, cancelar)
- Processar mensagens recebidas via webhook do WhatsApp
- Coordenar a resposta automática via WhatsApp
- Manter o estado da conversa (etapas, dados selecionados)

## Fluxo

```
WebhookController
    ↓
ConversationApplicationService (fachada)
    ↓
ConversationOrchestrator (coordenação)
    ↓
WhatsAppAdapter (interface)
    ↓
EvolutionWhatsAppAdapter (implementação)
    ↓
EvolutionService (infraestrutura)
```

### Detalhamento do Fluxo

1. **WebhookController** recebe o payload do WhatsApp via POST `/webhook/whatsapp`
2. **ConversationApplicationService** delega para o orchestrator
3. **ConversationOrchestrator** coordena:
   - Recepção da mensagem via `WhatsAppAdapter.receberMensagem()`
   - Processamento via `ConversationMessageProcessor.gerarResposta()`
   - Envio da resposta via `WhatsAppAdapter.enviarMensagem()`
4. **EvolutionWhatsAppAdapter** adapta o payload JSON da Evolution API
5. **EvolutionService** faz a chamada HTTP para a API Evolution

## Dependências

### Internas
- `ConversationRepository` - persistência de conversas
- `ConversationService` - processamento de mensagens (legacy)
- `ConversationStateService` - estado da conversa
- `ConversationMemory` - memória de contexto
- `IntentService` - classificação de intenções
- `OllamaService` - LLM para respostas
- `PromptService` - montagem de prompts
- `CustomerProfileService` - perfil do cliente
- `AppointmentService` - agendamentos
- `AppointmentBookingService` - reserva de horários

### Externas
- Evolution API (via HTTP)

## Classes

### Application Layer

| Classe | Responsabilidade | Status |
|--------|-----------------|--------|
| `ConversationApplicationService` | Fachada - expõe operações de conversa | Application Service |
| `ConversationOrchestrator` | Orquestração do fluxo WhatsApp | Application Service |
| `ConversationRegistry` | CRUD de conversas | Application Service |
| `ConversationMessageProcessor` | Interface para processamento | Interface |
| `ConversationServiceMessageProcessor` | Implementação via ConversationService | Application Service |

### Domain Layer

| Classe | Responsabilidade | Status |
|--------|-----------------|--------|
| `Conversation` | Aggregate Root - estado da conversa | Domain |
| `ConversationId` | Value Object - identificador | Domain |
| `ConversationStep` | Enum - etapas da conversa | Domain |
| `ConversationStatus` | Enum - status da conversa | Domain |
| `ConversationService` | Serviço de domínio - lógica de negócio | Domain Service |
| `ContextService` | Contexto da conversa | Domain Service |
| `QuickResponseService` | Respostas rápidas | Domain Service |

### Infrastructure Layer

| Classe | Responsabilidade | Status |
|--------|-----------------|--------|
| `WhatsAppAdapter` | Interface - adaptador WhatsApp | Interface |
| `EvolutionWhatsAppAdapter` | Implementação - Evolution API | Infrastructure |
| `EvolutionService` | Cliente HTTP - Evolution API | Infrastructure |
| `ConversationRepository` | Interface - repositório | Interface |
| `InMemoryConversationRepository` | Implementação - memória | Infrastructure |

## Próximos Pontos de Extensão

### Intent Engine
- `ConversationMessageProcessor` é o ponto de entrada para substituir `ConversationService`
- Permite implementar LLM, regras, ou híbrido

### Multi-tenant
- `ConversationRegistry` pode ser estendido para isolar conversas por business

### Persistência
- `ConversationRepository` pode ser implementado com JPA/Hibernate
- `InMemoryConversationRepository` é a implementação atual (MVP)

### Protocolos
- `WhatsAppAdapter` permite adicionar outros canais (Telegram, Instagram)
- `EvolutionWhatsAppAdapter` é apenas uma implementação

### State Machine
- `ConversationStep` pode evoluir para máquina de estados formal
- Permite validação de transições e timeouts

## Testes

- `ConversationOrchestratorTest` - testes de orquestração
- `ConversationApplicationServiceWhatsAppTest` - testes de integração WhatsApp
- `ConversationControllerTest` - testes de API REST
- `ConversationServiceCustomerProfileTest` - testes de lógica de negócio