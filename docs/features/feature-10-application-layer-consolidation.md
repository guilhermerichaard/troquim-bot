# Feature 10: Application Layer Consolidation

## Objetivo

Consolidar a camada de aplicação do módulo Conversation, introduzindo uma arquitetura mais limpa e desacoplada com responsabilidades bem definidas entre os componentes, facilitando a manutenibilidade e extensibilidade do sistema.

## Arquivos Criados

| Arquivo | Responsabilidade |
|---------|-----------------|
| `src/main/java/com/troquim_bot/application/conversation/ConversationOrchestrator.java` | Orquestração do fluxo de mensagens WhatsApp (recepção, processamento, envio) |
| `src/main/java/com/troquim_bot/application/conversation/ConversationRegistry.java` | CRUD de conversas (criar, buscar, atualizar, avançar etapa, voltar etapa, resetar, cancelar) |
| `src/main/java/com/troquim_bot/application/conversation/ConversationMessageProcessor.java` | Interface para processamento de mensagens (abstração para diferentes implementações) |
| `src/main/java/com/troquim_bot/application/conversation/ConversationServiceMessageProcessor.java` | Implementação do processador usando ConversationService existente |
| `src/main/java/com/troquim_bot/application/conversation/ConversationInputMapper.java` | Mapeamento e validação de entrada (UUIDs, datas, horários) |

## Arquivos Modificados

| Arquivo | Mudança |
|---------|---------|
| `src/main/java/com/troquim_bot/application/conversation/ConversationApplicationService.java` | Transformado em fachada, delegando para os novos componentes |

## Fluxo Final

```
WebhookController
    ↓
ConversationApplicationService (fachada)
    ├── ConversationRegistry (CRUD de conversas)
    ├── ConversationOrchestrator (coordenação WhatsApp)
    │       ├── WhatsAppAdapter (recepção)
    │       └── ConversationMessageProcessor (processamento)
    │               └── ConversationServiceMessageProcessor (implementação)
    └── ConversationInputMapper (validação de entrada)
```

### Detalhamento do Fluxo

1. **WebhookController** recebe o payload do WhatsApp via POST `/webhook/whatsapp`
2. **ConversationApplicationService** atua como fachada, delegando para:
   - **ConversationOrchestrator** para processamento de webhooks
   - **ConversationRegistry** para operações CRUD de conversas
   - **ConversationInputMapper** para validação de parâmetros
3. **ConversationOrchestrator** coordena:
   - Recepção da mensagem via `WhatsAppAdapter.receberMensagem()`
   - Processamento via `ConversationMessageProcessor.gerarResposta()`
   - Envio da resposta via `WhatsAppAdapter.enviarMensagem()`
4. **ConversationServiceMessageProcessor** delega para `ConversationService` existente

## Decisões Arquiteturais

### 1. Fachada (ConversationApplicationService)
- **Decisão**: Manter `ConversationApplicationService` como fachada única de entrada
- **Motivo**: Simplifica a API exposta ao controller, mantém compatibilidade com código existente
- **Benefício**: Permite evoluir internamente sem impactar consumidores externos

### 2. Orquestrador (ConversationOrchestrator)
- **Decisão**: Separar a orquesttração do fluxo WhatsApp em classe dedicada
- **Motivo**: Centralizar lógica de coordenação (lock, deduplicação, logging)
- **Benefício**: Facilita testes unitários e manutenção do fluxo de mensagens

### 3. Registry (ConversationRegistry)
- **Decisão**: Isolar operações de persistência em classe dedicada
- **Motivo**: Separar responsabilidade de CRUD do restante da lógica
- **Benefício**: Código mais limpo, fácil de mockar em testes

### 4. Interface de Processamento (ConversationMessageProcessor)
- **Decisão**: Criar interface para processamento de mensagens
- **Motivo**: Permitir substituição de implementação (LLM, regras, híbrido)
- **Benefício**: Extensibilidade futura sem modificar código existente

### 5. Input Mapper (ConversationInputMapper)
- **Decisão**: Centralizar validação e transformação de entrada
- **Motivo**: Evitar duplicação de lógica de validação (UUIDs, datas, horários)
- **Benefício**: Consistência e redução de código boilerplate

## Testes

- **340 testes passando**
- Testes cobrindo:
  - `ConversationOrchestratorTest` - orquestração e fluxo de webhooks
  - `ConversationRegistryTest` - operações CRUD
  - `ConversationServiceMessageProcessorTest` - processamento de mensagens
  - `ConversationInputMapperTest` - validação de entrada
  - Testes de integração WhatsApp

## Resultado do Build

```
BUILD SUCCESS
```

## Próximos Passos

1. **Implementar novos processadores**
   - `LlmMessageProcessor` - processamento via LLM
   - `RuleBasedMessageProcessor` - processamento baseado em regras
   - `HybridMessageProcessor` - combinação de estratégias

2. **Adicionar novos adaptadores**
   - `TelegramAdapter` - suporte a Telegram
   - `InstagramAdapter` - suporte a Instagram

3. **Melhorar o Registry**
   - Adicionar isolamento multi-tenant por business
   - Implementar busca por customerId

4. **Persistência avançada**
   - Implementar `JpaConversationRepository`
   - Migrar de `InMemoryConversationRepository`