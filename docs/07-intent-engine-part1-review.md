# Feature 11 Parte 1 - Intent Engine Foundation Review

## STATUS: **REPROVADO**

---

## 1. Interface IntentEngine - Análise

### ✅ APROVADO
- Interface simples e coesa: `IntentResult classify(String message)`
- Retorna `IntentResult` (record imutável)
- Não tem acoplamento com LLM ou outras dependências

### ⚠️ AJUSTE OPCIONAL
- Nome `classify` em inglês, mas o projeto usa português (`criarConversa`, `buscarPorId`)
- Sugestão: `classificar` para consistência

---

## 2. IntentResult - Análise

### ✅ APROVADO
- Record simples com apenas `IntentType type`
- Validação de null no compact constructor

### ⚠️ AJUSTE OPCIONAL
- Poderia ter `confidence` (0.0-1.0) para futuro uso com ML
- Poderia ter `matchedTerm` para debug

---

## 3. IntentType - Análise

### ✅ APROVADO
- Cobertura mínima para MVP: GREETING, BOOK_APPOINTMENT, CANCEL_APPOINTMENT, RESCHEDULE_APPOINTMENT, CHECK_AVAILABILITY, ASK_SERVICES, HUMAN_ATTENDANT, UNKNOWN

### ❌ AJUSTE OBRIGATÓRIO
- **FALTA intenções já existentes no `ai.intent.IntentType`**:
  - `AGRADECIMENTO` - "obrigado", "valeu"
  - `DESPEDIDA` - "tchau", "até logo"
  - `LEMBRAR_CLIENTE` - "lembra de mim", "me conhece"
  - `CONSULTAR_AGENDAMENTO` - "qual agendamento", "o que agendei"
  - `CONSULTAR_DIA_AGENDADO` - "qual dia agendei"
  - `CONSULTAR_HORARIO_AGENDADO` - "qual horário"
  - `CONSULTAR_SERVICO_AGENDADO` - "qual serviço"
  - `CONSULTAR_NOME` - "qual meu nome"

### ⚠️ AJUSTE OPCIONAL
- Nomes em inglês (`GREETING`) vs. português (`SAUDACAO`) - inconsistência com o resto do projeto

---

## 4. RuleBasedIntentEngine - Análise

### ✅ APROVADO
- Regras simples e legíveis
- Priorização correta (CANCEL > REMARCAR > CHECK_AVAILABILITY > HUMAN > ASK_SERVICES > BOOK > GREETING)
- Normalização de acentos e case-insensitive
- Uso de `hasTerm` com espaços evita substring matching

### ❌ AJUSTE OBRIGATÓRIO - RISCO DE FALSO POSITIVO CRÍTICO
```java
if (hasAny(text, "agendar", "marcar", "horario")) {
    return result(IntentType.BOOK_APPOINTMENT);
}
```

**Problema**: `"horario"` é muito genérico. Exemplos de falso positivo:
- "Que horário é a reunião?" → BOOK_APPOINTMENT (deveria ser UNKNOWN ou outro)
- "Agora" → não bate (OK, mas "horario" sozinho é problema)
- "Preciso saber o horário" → BOOK_APPOINTMENT (deveria ser CHECK_AVAILABILITY)

**Correção necessária**:
```java
if (hasAny(text, "agendar", "marcar", "marcar horario", "agendar horario")) {
    return result(IntentType.BOOK_APPOINTMENT);
}
```

### ⚠️ AJUSTE OPCIONAL
- "pessoa" é muito genérico - pode bater em "pessoa jurídica", "pessoa física"
- Sugestão: "falar com pessoa" ou "atendente pessoa"

---

## 5. Testes - Análise

### ✅ APROVADO
- `IntentEngineTest` cobre todas as intenções principais
- `IntentEngineEdgeCasesTest` cobre null, vazio, acentos, priorização

### ⚠️ AJUSTE OPCIONAL
- Falta teste para falso positivo "horario" isolado
- Falta teste para "agora" (não deve bater em "agendar")

---

## 6. Integração com ConversationOrchestrator - Análise

### ✅ APROVADO
- A interface `IntentEngine` pode ser injetada no `ConversationOrchestrator`
- O método `classify` retorna `IntentResult` que pode ser convertido para `IntentType`

### ⚠️ AJUSTE OPCIONAL
- Será necessário adaptar o código existente que usa `ai.intent.IntentType`
- Ou manter dois enums (não recomendado)

---

## AJUSTES OBRIGATÓRIOS

1. **Adicionar intenções faltantes ao IntentType**
   - AGRADECIMENTO, DESPEDIDA, LEMBRAR_CLIENTE
   - CONSULTAR_AGENDAMENTO, CONSULTAR_DIA_AGENDADO, CONSULTAR_HORARIO_AGENDADO
   - CONSULTAR_SERVICO_AGENDADO, CONSULTAR_NOME

2. **Corrigir regra de "horario" no RuleBasedIntentEngine**
   - Mudar de `"horario"` para `"marcar horario"` ou `"agendar horario"`
   - Evitar falso positivo crítico

3. **Adicionar regras para intenções faltantes**
   - AGRADECIMENTO: "obrigado", "obrigada", "valeu", "agradeco"
   - DESPEDIDA: "tchau", "até logo", "até mais"
   - LEMBRAR_CLIENTE: "lembra de mim", "me conhece", "sabe quem eu sou"

---

## AJUSTES OPCIONAIS

1. Renomear `classify` para `classificar` (consistência)
2. Adicionar `confidence` em `IntentResult`
3. Refinar termo "pessoa" para "falar com pessoa"
4. Adicionar testes para falso positivo

---

## DECISÃO FINAL

### ❌ **NÃO PODE INTEGRAR NA PARTE 2**
- Falta intenções críticas (AGRADECIMENTO, DESPEDIDA, LEMBRAR_CLIENTE)
- Risco de falso positivo com "horario"

### ❌ **NÃO PODE COMMITAR PARTE 1 ISOLADA**
- Quebra de funcionalidade existente (perde intenções já implementadas)
- Risco de classificação incorreta em produção

---

## Recomendação

1. **Primeiro**: Adicionar intenções faltantes ao `IntentType`
2. **Segundo**: Corrigir regra de "horario" no `RuleBasedIntentEngine`
3. **Terceiro**: Adicionar regras para novas intenções
4. **Quarto**: Executar todos os testes existentes (340 testes) para garantir não-regressão
5. **Quinto**: Commitar como Parte 1 completa