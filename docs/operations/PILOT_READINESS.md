# Prontidão do piloto — baseline atual

Baseline técnica: `a154e10b6791c9f0cf5c299a20431a98552bb959`  
Atualizado em: 6 de agosto de 2026

## Leitura obrigatória

Esta matriz separa três coisas diferentes:

1. capacidade implementada e testada no backend;
2. configuração real do negócio e dos canais;
3. validação operacional com pessoas e dispositivos.

Uma suíte verde prova somente o comportamento implementado. Não prova credenciais, configuração comercial, dados aprovados pelo cliente, entrega de push, treinamento, suporte ou operação em produção.

## Classificações

- **Pronto tecnicamente:** capacidade implementada e coberta por evidência automatizada.
- **Parcial:** existe fundação, mas falta integração, configuração ou validação real.
- **Bloqueado:** depende de decisão, credencial ou capacidade ainda ausente.
- **Não iniciado:** não há entrega verificável na baseline.

## Matriz

| Área | Estado | Evidência ou bloqueio concreto |
|---|---|---|
| `Business` persistido | Pronto tecnicamente | PR #3; raiz do tenant e FKs estruturais |
| Isolamento de catálogo por negócio | Pronto tecnicamente | PR #2; IDs tenant-scoped e FKs compostas |
| Serviços persistidos | Pronto tecnicamente | PR #2; migration V11 |
| Profissionais persistidos | Pronto tecnicamente | PR #2; migration V11 |
| Vínculo profissional-serviço | Pronto tecnicamente | PR #2; associação por IDs canônicos |
| Expediente persistido | Pronto tecnicamente | PR #2; migration V12 |
| Disponibilidade profissional persistida | Pronto tecnicamente | PR #2; migration V12 |
| Duração real por serviço | Pronto tecnicamente | usada na disponibilidade e booking |
| Conflito por sobreposição | Pronto tecnicamente | testes de intervalos e appointments ativos |
| Concorrência no mesmo slot | Pronto tecnicamente | PR #6; lock transacional e testes PostgreSQL |
| Perfil público por slug | Pronto tecnicamente | PR #4; slug único e publicação explícita |
| API pública de perfil/catálogo | Pronto tecnicamente | PR #5 |
| API pública de disponibilidade | Pronto tecnicamente | PR #5 |
| API pública de booking | Pronto tecnicamente | PR #7 |
| Idempotência HTTP | Pronto tecnicamente | PR #7; mesma chave/payload não duplica |
| Segurança deny-by-default | Pronto tecnicamente | allowlist explícita em `SecurityConfigDefaultDeny` |
| Suíte da baseline | Pronto tecnicamente | 1.183 testes, zero falhas/erros/pulados no PR #7 |
| Dados reais do Studio configurados | Não iniciado | não há evidência na baseline do backend de provisionamento real aprovado |
| Perfil do Studio publicado | Não iniciado | slug/estado real do piloto não confirmado neste documento |
| Landing consumindo API pública | Bloqueado | responsabilidade do frontend `studio-malu-mota`; integração ainda em execução |
| Fluxo público ponta a ponta em celular | Bloqueado | depende do frontend conectado e do ambiente acessível |
| Área protegida da empreendedora | Parcial | existem capacidades de owner/admin no backend, mas experiência do piloto ainda precisa ser validada |
| Agenda operacional da empreendedora | Parcial | dados de booking existem; consulta/UI real do piloto não comprovada |
| Cancelamento | Parcial | capacidade deve ser auditada contra casos de uso e contrato do piloto |
| Remarcação | Parcial | capacidade deve ser auditada contra casos de uso e contrato do piloto |
| Notificação de novo agendamento | Não iniciado | nenhum canal de push do piloto validado nesta baseline |
| Notificação de cancelamento/remarcação | Não iniciado | política de urgência e entrega ainda não validadas |
| Web Push/PWA em dois celulares | Não iniciado | teste doméstico ainda não executado |
| WhatsApp Cloud — fundação técnica | Parcial | integração e runbook existem; operação depende de flag, credenciais e configuração Meta |
| Callback Meta verificada no ambiente atual | Bloqueado | precisa de evidência operacional datada |
| Campo `messages` assinado | Bloqueado | precisa de evidência no WABA atual |
| Número oficial pronto | Bloqueado | situação comercial/operacional não comprovada pela baseline |
| Templates aprovados | Bloqueado | depende de configuração Meta e necessidade fora da janela |
| Embedded Signup | Parcial | fundação existe, mas onboarding real do piloto precisa ser validado |
| Redis saudável no ambiente atual | Bloqueado | requer verificação operacional datada; não inferir do código |
| Backup Redis verificado | Bloqueado | requer arquivo, SHA-256 e teste não destrutivo de inspeção |
| Restore Redis validado | Não iniciado | não executar sem perda comprovada, janela e rollback |
| PostgreSQL backup/restore | Bloqueado | procedimento e evidência operacional não avaliados nesta recuperação |
| Observabilidade de booking | Parcial | testes e erros públicos existem; logs, métricas e alertas do piloto precisam ser definidos |
| Suporte operacional | Não iniciado | falta roteiro de triagem, escalonamento e recuperação do piloto |
| Consentimento e política de dados | Parcial | requisitos precisam ser confirmados na experiência e operação real |
| Política de cancelamento | Bloqueado | decisão de produto do negócio piloto |
| Política de remarcação | Bloqueado | decisão de produto do negócio piloto |
| Preço e duração aprovados | Bloqueado | dependem da confirmação do Studio |
| Teste com cinco pessoas | Não iniciado | depende do fluxo público integrado |
| Dez agendamentos sem duplicidade | Não iniciado | meta operacional, não prova automatizada |
| Disposição de pagamento | Não iniciado | depende de teste comercial com empreendedor |

## Diagnóstico

### Backend

A fundação de catálogo, agenda, tenant e booking público deixou de ser o bloqueador principal. A baseline possui estrutura suficiente para integrar uma experiência real sem criar outro motor de agenda no frontend.

### Produto

O bloqueio atual migrou para integração e operação:

- configurar um negócio real;
- publicar o perfil;
- conectar o frontend aprovado aos contratos públicos;
- criar área mínima da empreendedora;
- entregar notificações usando o celular existente;
- testar com pessoas externas;
- definir políticas do negócio.

### Canal WhatsApp

A existência da integração Cloud não autoriza operação comercial. Devem ser confirmados separadamente:

- feature flag no ambiente;
- credenciais válidas;
- Callback URL;
- verify token;
- assinatura do campo `messages`;
- `phone_number_id` correto;
- WABA e número oficial;
- janela de atendimento/templates;
- rollback para o provider anterior, quando aplicável.

## Gate técnico para iniciar teste doméstico

- [ ] Studio provisionado com dados de teste controlados;
- [ ] perfil público publicado em ambiente de teste;
- [ ] frontend sem catálogo ou horário local no caminho testado;
- [ ] booking real com `Idempotency-Key`;
- [ ] conflito e retry testados;
- [ ] área protegida mostra o appointment criado;
- [ ] evento de notificação possui `eventId` deduplicável;
- [ ] nenhum dado sensível desnecessário aparece na tela bloqueada.

## Gate para piloto com negócio real

- [ ] dados do negócio aprovados;
- [ ] políticas de cancelamento/remarcação aprovadas;
- [ ] termos e consentimento revisados;
- [ ] backup e rollback mínimos confirmados;
- [ ] observabilidade permite reproduzir falhas;
- [ ] suporte possui responsável e canal;
- [ ] teste doméstico concluído;
- [ ] cinco usuários externos concluíram o fluxo;
- [ ] nenhum P0 aberto.

## Decisão atual

**Apto para integração controlada e testes técnicos.**  
**Ainda não declarado apto para piloto comercial autônomo.**

O próximo gate não é ampliar o backend. É provar o fluxo completo com o frontend aprovado, dados reais controlados e visualização/notificação para a empreendedora.
