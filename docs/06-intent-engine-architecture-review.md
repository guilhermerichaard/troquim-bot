# Architecture Review - Feature 11: Intent Engine (Rule-Based)

## 1. A arquitetura suporta naturalmente um Intent Engine?

**SIM.** A arquitetura já possui suporte nativo, mas com nomenclatura ambígua.

### Evidências:
- `IntentService` já existe em `com.troquim_bot.ai.intent` como um **Intent Engine baseado em regras Java**
- `IntentType` enum já define todas as intenções necessárias para o MVP
- `ConversationMessageProcessor` é uma interface que permite múltiplas implementações
- `ConversationServiceMessageProcessor` delega para `ConversationService`
- `ConversationService` já consome `IntentService` como dependência

### Problema de nomenclatura:
- `IntentService` está no pacote `ai.intent` mas **NÃO usa AI/LLM**
- O nome sugere que é um serviço de IA, mas é apenas regras Java
- Isso pode confundir desenvolvedores futuros

---

## 2. Qual deve ser exatamente o ponto de entrada?

### Ponto de entrada atual:
```
WebhookController
    ↓
ConversationApplicationService.receberWebhookWhatsApp()
    ↓
ConversationOrchestrator.receberWebhookWhatsApp()
    ↓
ConversationOrchestrator.processarMensagem()
    ↓
ConversationMessageProcessor.gerarResposta()
    ↓
ConversationService.gerarResposta()
    ↓
IntentService.classificar() ← PONTO DE ENTRADA REAL
```

### Ponto de entrada para Feature 11:
O `IntentService.classificar()` já é o ponto de entrada. A Feature 11 deve:
- **Refatorar a nomenclatura** para `IntentEngine` ou `IntentClassifier`
- **Manter a interface ConversationMessageProcessor** como contrato de processamento
- **Criar nova implementação** que priorize o Intent Engine

---

## 3. ConversationMessageProcessor continua existindo? Ou ele deve virar um pipeline?

### Análise:

**ConversationMessageProcessor** é uma interface com uma única responsabilidade:
```java
String gerarResposta(String numero, String mensagem);
```

**ConversationServiceMessageProcessor** é a implementação atual que delega para `ConversationService`.

### Recomendação:

**DEVE VIRAR UM PIPELINE.** A interface atual é muito genérica.

#### Pipeline recomendado:
```
MessageProcessor (interface)
    ↓
IntentMessageProcessor (implementação)
    - classifica intenção
    - roteia para handler específico
    - fallback para LLM (se habilitado)
```

### Justificativa:
- Permite múltiplos processadores encadeados
- Facilita a desativação do LLM sem quebrar o fluxo
- Permite adicionar novos processadores (ex: validação, enriquecimento)
- Mantém o princípio Open/Closed

---

## 4. O Intent Engine deve: substituir, decorar, compor ou ficar antes do MessageProcessor?

### Análise das opções:

| Estratégia | Impacto | Recomendação |
|------------|---------|--------------|
| **Substituir** | Remove LLM completamente, mas quebra o contrato existente | ❌ NÃO - risco alto |
| **Decorar** | Adiciona camada sem quebrar, mas mantém acoplamento | ⚠️ PARCIAL - técnica mas não resolve nomenclatura |
| **Compor** | Integra como parte do processo, mas não é primário | ⚠️ PARCIAL - o Intent já é primário |
| **Antes do MessageProcessor** | Processa intenção antes de qualquer lógica | ✅ **SIM - RECOMENDADO** |

### Estratégia recomendada: **ANTES do MessageProcessor**

#### Arquitetura alvo:
```
ConversationOrchestrator
    ↓
IntentEngine.classificar(mensagem) → IntentType
    ↓
IntentRouter.rotear(IntentType, mensagem) → IntentHandler
    ↓
IntentHandler.processar(numero, mensagem, intentType) → resposta
    ↓
[Fallback opcional: LLM]
```

### Justificativa:
1. **IntentEngine é a classificação** - deve acontecer antes de qualquer processamento
2. **ConversationService** já faz roteamento baseado em `IntentType`
3. **Separação clara**: classificação (Intent) vs. execução (Handler)
4. **Permite desacoplar** o LLM do fluxo principal
5. **Facilita testes** - pode mockar apenas a classificação

---

## 5. Quais interfaces devem existir?

### Interfaces obrigatórias para Feature 11:

#### 5.1 IntentEngine (nova)
```java
public interface IntentEngine {
    IntentType classificar(String mensagem);
}
```
- Substitui `IntentService` (refatoração de nomenclatura)
- Responsabilidade única: classificação

#### 5.2 IntentHandler (nova)
```java
public interface IntentHandler {
    Optional<String> processar(String numero, String mensagem, IntentType intentType);
    Set<IntentType> suportados();
}
```
- Cada intenção tem seu handler
- Permite múltiplos handlers no pipeline

#### 5.3 MessageProcessor (refatorar)
```java
public interface MessageProcessor {
    String processar(String numero, String mensagem);
}
```
- Renomear de `ConversationMessageProcessor`
- Mais genérico, não atrelado a "conversa"

#### 5.4 IntentRouter (nova)
```java
public interface IntentRouter {
    Optional<MessageProcessor> selecionar(IntentType intentType, String mensagem);
}
```
- Roteia intenção para o processor correto
- Permite lógica de roteamento complexa

---

## 6. Quais responsabilidades devem permanecer no Domain?

### Permanecem no Domain:

| Classe | Responsabilidade | Justificativa |
|--------|------------------|---------------|
| `Conversation` | Aggregate Root do estado da conversa | Dados estruturais, invariantes |
| `ConversationState` | Estado em memória da conversa | Não é intenção, é contexto |
| `AppointmentDraft` | Draft do agendamento | Dados do agendamento, não intenção |
| `ConversationStep` | Enum de etapas | Estado da conversa, não intenção |
| `ConversationStatus` | Enum de status | Estado da conversa, não intenção |

### NÃO devem estar no Domain:

| Classe | Responsabilidade | Justificativa |
|--------|------------------|---------------|
| `IntentService` | Classificação de intenção | É regra de aplicação, não domínio |
| `QuickResponseService` | Respostas rápidas | É estratégia de resposta, não domínio |
| `ConversationStateService` | Processamento de estado | É lógica de aplicação |

---

## 7. O que NÃO pode entrar no Intent Engine?

### NÃO PODE:

1. **Lógica de negócio**
   - Criar agendamento
   - Verificar disponibilidade
   - Validar conflitos de horário

2. **Estado da conversa**
   - Acessar `ConversationState`
   - Modificar `AppointmentDraft`
   - Navegar entre `ConversationStep`

3. **Histórico de mensagens**
   - `ConversationMemory`
   - Contexto histórico

4. **Respostas rápidas**
   - `QuickResponseService`
   - Deve ser chamado após classificação

5. **LLM/Fallback**
   - `OllamaService`
   - `PromptService`
   - Deve ser fallback, não parte do engine

6. **Detecção de entidades**
   - Serviços, dias, horários
   - Deve ser responsabilidade do `ConversationStateService`

---

## 8. Quais riscos existem para o MVP?

### Riscos críticos:

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| **Duplicação de lógica** | IntentService e ConversationStateService têm lógica de detecção de "agendamento" | Consolidar em IntentEngine |
| **Intenções ambíguas** | "quero agendar" pode ser AGENDAMENTO ou NOVO_AGENDAMENTO | Definir prioridade clara |
| **Falso positivos** | "agora" contém "agendar" | Usar palavras inteiras, não substrings |
| **Acentos e variações** | "agendar", "agendo", "agendei" | Normalizar antes de classificar |
| **Falta de testes** | IntentService sem cobertura | Criar testes de classificação |

### Riscos de arquitetura:

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| **Acoplamento com LLM** | ConversationService depende de OllamaService | Criar fallback opcional |
| **Nomenclatura confusa** | `ai.intent` sugere IA | Mover para `application.intent` |
| **Pipeline complexo** | Múltiplos handlers podem ser overkill | Começar com router simples |

---

## 9. Quais testes devem existir antes de aceitar essa Feature?

### Testes obrigatórios:

#### 9.1 IntentType - Gap Analysis

**IntentType existente (já no código):**
- LEMBRAR_CLIENTE, SAUDACAO, AGRADECIMENTO, DESPEDIDA, HUMANO, ORCAMENTO
- AGENDAMENTO, CONSULTAR_AGENDAMENTO, CONSULTAR_DIA_AGENDADO, CONSULTAR_HORARIO_AGENDADO
- CONSULTAR_SERVICO_AGENDADO, CONSULTAR_NOME, CONSULTAR_SERVICOS, NOVO_AGENDAMENTO, DESCONHECIDO

**IntentType FALTANTE para Feature 11:**
- `CANCELAR` - "Cancelar horário", "Quero cancelar", "Anular agendamento"
- `REMARCAR` - "Remarcar", "Mudar horário", "Quero outro horário"
- `VER_HORARIOS` - "Ver horários", "Quais horários disponíveis?"

#### 9.2 IntentEngineTest
```java
@Test
void classificarSaudacao() {
    assertEquals(IntentType.SAUDACAO, engine.classificar("Oi"));
    assertEquals(IntentType.SAUDACAO, engine.classificar("Olá, bom dia!"));
}

@Test
void classificarAgendamento() {
    assertEquals(IntentType.AGENDAMENTO, engine.classificar("Quero agendar"));
    assertEquals(IntentType.AGENDAMENTO, engine.classificar("Marque um horário"));
}

@Test
void classificarCancelar() {
    assertEquals(IntentType.CANCELAR, engine.classificar("Cancelar horário"));
    assertEquals(IntentType.CANCELAR, engine.classificar("Quero cancelar"));
}

@Test
void classificarRemarcar() {
    assertEquals(IntentType.REMARCAR, engine.classificar("Remarcar"));
    assertEquals(IntentType.REMARCAR, engine.classificar("Quero mudar o horário"));
}

@Test
void classificarVerHorarios() {
    assertEquals(IntentType.VER_HORARIOS, engine.classificar("Ver horários"));
    assertEquals(IntentType.VER_HORARIOS, engine.classificar("Quais horários disponíveis?"));
}

@Test
void classificarFalarComAtendente() {
    assertEquals(IntentType.HUMANO, engine.classificar("Falar com atendente"));
    assertEquals(IntentType.HUMANO, engine.classificar("Quero falar com humano"));
}

@Test
void classificarDesconhecido() {
    assertEquals(IntentType.DESCONHECIDO, engine.classificar("xyz123"));
}
```

#### 9.2 IntentEngineEdgeCasesTest
```java
@Test
void mensagemNulaOuVazia() {
    assertEquals(IntentType.DESCONHECIDO, engine.classificar(null));
    assertEquals(IntentType.DESCONHECIDO, engine.classificar(""));
    assertEquals(IntentType.DESCONHECIDO, engine.classificar("   "));
}

@Test
void acentosECaseInsensitive() {
    assertEquals(IntentType.SAUDACAO, engine.classificar("OI"));
    assertEquals(IntentType.SAUDACAO, engine.classificar("Olá"));
    assertEquals(IntentType.SAUDACAO, engine.classificar("OLÁ, BOM DIA!"));
}

@Test
void prioridadeDeIntencoes() {
    // "quero agendar outro" deve ser NOVO_AGENDAMENTO, não AGENDAMENTO
    assertEquals(IntentType.NOVO_AGENDAMENTO, engine.classificar("quero agendar outro"));
}
```

#### 9.3 IntentHandlerTest (para cada handler)
```java
@Test
void handlerSaudacaoRespondeCorretamente() {
    String resposta = handler.processar("5511999999999", "Oi", IntentType.SAUDACAO);
    assertEquals("Boa tarde! Como posso ajudar?", resposta);
}
```

---

## 10. Decisões arquiteturais para Features 12-15?

### Decisões críticas a serem tomadas AGORA:

#### 10.1 Interface IntentEngine
- **DECIDIR AGORA**: Criar interface `IntentEngine` substituindo `IntentService`
- **Impacto**: Permite múltiplas implementações (rule-based, ML-based, híbrido)
- **Features afetadas**: 12 (Intent Engine com ML), 13 (Intent Engine híbrido)

#### 10.2 Pipeline de MessageProcessor
- **DECIDIR AGORA**: Refatorar `ConversationMessageProcessor` para `MessageProcessor`
- **Impacto**: Permite encadeamento de processadores
- **Features afetadas**: 12-15 (qualquer feature de processamento de mensagem)

#### 10.3 Fallback configurável
- **DECIDIR AGORA**: LLM como fallback opcional, não obrigatório
- **Impacto**: Permite desativar LLM sem quebrar o sistema
- **Features afetadas**: 12 (modo offline), 14 (respostas sem LLM)

#### 10.4 IntentType.CANCELAR e IntentType.REMARCAR
- **DECIDIR AGORA**: Adicionar ao enum `IntentType`
- **Impacto**: Necessário para Feature 11
- **Features afetadas**: 11, 13 (cancelamento de agendamento)

#### 10.5 IntentType.VER_HORARIOS
- **DECIDIR AGORA**: Adicionar ao enum `IntentType`
- **Impacto**: Necessário para Feature 11
- **Features afetadas**: 11, 14 (consulta de agenda)

#### 10.6 Estrutura de pacotes
- **DECIDIR AGORA**: Mover `ai.intent` para `application.intent`
- **Impacto**: Clareza arquitetural
- **Features afetadas**: Todas as futuras

### Estrutura de pacotes recomendada:
```
com.troquim_bot.application.intent
    IntentEngine.java
    IntentType.java
    rule/
        RuleBasedIntentEngine.java
    handler/
        IntentHandler.java
        GreetingHandler.java
        AppointmentHandler.java
        CancelHandler.java
        RescheduleHandler.java
        AvailabilityHandler.java
        HumanHandler.java
    router/
        IntentRouter.java
```

---

## Resumo Executivo

| Pergunta | Resposta |
|----------|----------|
| 1. Suporta Intent Engine? | **SIM** - já existe como `IntentService` |
| 2. Ponto de entrada? | `IntentEngine.classificar()` antes do processamento |
| 3. ConversationMessageProcessor? | **DEVE VIRAR PIPELINE** |
| 4. Estratégia? | **ANTES do MessageProcessor** |
| 5. Interfaces? | `IntentEngine`, `IntentHandler`, `IntentRouter` |
| 6. Domain permanece? | `Conversation`, `ConversationState`, `AppointmentDraft` |
| 7. NÃO pode entrar? | Negócio, estado, memória, LLM, detecção de entidades |
| 8. Riscos? | Duplicação, ambiguidade, falso positivos, falta de testes |
| 9. Testes? | IntentEngineTest, EdgeCasesTest, HandlerTest |
| 10. Decisões críticas? | Interface, pipeline, fallback, novos IntentType, pacotes |