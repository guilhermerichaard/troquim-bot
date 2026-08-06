# Catálogo e agenda — estado atual e lacunas restantes

Baseline técnica: `a154e10b6791c9f0cf5c299a20431a98552bb959`  
Atualizado em: 6 de agosto de 2026

## Objetivo

Registrar o estado atual do catálogo, expediente, disponibilidade e booking sem repetir a narrativa antiga de que esses componentes ainda são listas globais ou dados simulados.

Este documento descreve capacidades do backend. Não prova que um negócio real foi configurado, publicado e testado ponta a ponta.

## Fonte de verdade

- regras e invariantes: Domain;
- orquestração: Application;
- persistência e locks: Infrastructure;
- exposição pública/WhatsApp Flow: Interface;
- migrations e constraints do PostgreSQL: garantia estrutural entre agregados e requisições concorrentes.

## Capacidades implementadas

### 1. Tenant e raiz do negócio

- `Business` é persistido;
- tabelas tenant-scoped referenciam negócio existente;
- operações administrativas recebem tenant explicitamente;
- booking tipado não deduz tenant de payload público;
- perfil público resolve slug para o `BusinessId` interno sem expô-lo.

Evidência de integração: PR #3 e migrations posteriores à V12.

### 2. Catálogo persistido

- serviços persistidos por negócio;
- profissionais persistidos por negócio;
- vínculo profissional-serviço por IDs canônicos;
- associação cross-tenant protegida por FK composta;
- adapters in-memory restritos a perfis de teste/desenvolvimento;
- catálogo do WhatsApp Flow consulta a fonte persistida em vez de manter lista própria.

Evidência de integração: PR #2, migration V11 e testes PostgreSQL associados.

### 3. Expediente e disponibilidade

- calendário do negócio persistido;
- múltiplos períodos por dia;
- intervalo de almoço representado por composição, sem booleano especial;
- disponibilidade do profissional persistida por tenant;
- horário calculado pela interseção entre expediente do negócio e disponibilidade profissional;
- data passada e estados indisponíveis retornam condição explícita;
- duração do atendimento vem do serviço persistido;
- geração de início de slots usa política de domínio;
- relógio é injetável/testável.

Evidência de integração: PRs #2 e #3, migration V12 e testes de disponibilidade.

### 4. Conflitos e concorrência

- appointments ativos bloqueiam sobreposição parcial ou total;
- intervalos apenas encostados não conflitam;
- uma seção crítica serializa comandos concorrentes no mesmo `(business, professional, date)`;
- PostgreSQL usa lock transacional no ambiente real;
- tenant e profissional diferentes não bloqueiam um ao outro;
- falha dentro da transação reverte receipt e booking;
- duração nula, zero ou negativa é recusada no caminho tipado.

Evidência de integração: PR #6 e testes concorrentes com PostgreSQL.

### 5. API pública

Rotas públicas atuais:

- `GET /api/v1/public/businesses/{slug}`;
- `GET /api/v1/public/businesses/{slug}/availability`;
- `POST /api/v1/public/businesses/{slug}/appointments`.

A API pública:

- não expõe `BusinessId`;
- não diferencia seleção inexistente de seleção de outro tenant;
- retorna códigos públicos estáveis;
- exige `Idempotency-Key` na criação;
- reutiliza o caso de uso canônico de booking;
- não replica disponibilidade ou validação no controller.

Evidência de integração: PRs #5 e #7.

### 6. Idempotência

- a chave HTTP é exclusiva por `(business, Idempotency-Key)`;
- retry com mesma chave e mesmo payload não duplica;
- mesma chave com payload diferente é recusada;
- recusas de seleção também vinculam a chave ao fingerprint;
- a chave pode ser reutilizada independentemente em negócios diferentes;
- a resposta pública não expõe IDs internos, fingerprint ou chave de comando.

Evidência de integração: PR #7 e testes H2/PostgreSQL.

## Componentes legados

`ScheduleService` e caminhos textuais antigos podem continuar no código para compatibilidade, mas não são autoridade do fluxo tipado/público.

A existência de código legado não autoriza:

- converter dados silenciosamente para o caminho novo;
- usar agenda em memória como fallback;
- adicionar regras novas ao legado;
- tratar o legado como fonte de disponibilidade do piloto.

Qualquer remoção deve ocorrer em refatoração separada, com prova de que não existem consumidores necessários.

## O que não está provado apenas pelo backend

As capacidades acima não provam:

- que o Studio Malu Mota está provisionado com dados reais;
- que o perfil público foi publicado;
- que preços, durações e profissionais foram aprovados pelo negócio;
- que o frontend consome os contratos públicos sem mocks;
- que cancelamento e remarcação estão disponíveis no produto do piloto;
- que a empreendedora possui área protegida operacional;
- que notificações chegam ao celular;
- que o número oficial da Meta está pronto para operação comercial;
- que suporte, observabilidade e rollback foram testados.

## Lacunas do piloto a validar

### Configuração do negócio

- [ ] cadastrar o negócio real pelo caso de uso oficial;
- [ ] configurar slug, perfil público e publicação;
- [ ] cadastrar serviços, preços e durações aprovados;
- [ ] cadastrar profissionais e vínculos;
- [ ] configurar expediente e disponibilidade;
- [ ] validar isolamento com outro tenant de teste.

### Experiência pública

- [ ] conectar a landing aprovada ao `GET` de perfil/catálogo;
- [ ] consultar disponibilidade real;
- [ ] criar appointment com `Idempotency-Key`;
- [ ] tratar `404`, `400`, `409`, `422` e `503` sem sucesso falso;
- [ ] testar retry e conflito em celular.

### Operação da empreendedora

- [ ] autenticação real;
- [ ] tenant resolvido pela identidade autenticada;
- [ ] agenda de hoje e próximos horários;
- [ ] cancelamento/remarcação por caso de uso canônico, quando aprovado;
- [ ] notificações deduplicadas por evento;
- [ ] procedimento de suporte e recuperação.

## Decisões de produto ainda necessárias

Decisões não devem ser inventadas pelo frontend, prompt ou documentação. Precisam ser aprovadas e implementadas no domínio quando afetarem regra de negócio:

- política de cancelamento;
- política de remarcação;
- antecedência mínima e máxima;
- feriados e bloqueios excepcionais;
- encaixes e capacidade simultânea;
- recorrência;
- tolerância de atraso/no-show;
- serviços que exigem avaliação antes de confirmar;
- política de preço “a partir de” versus preço fechado.

## Critério de atualização

Atualizar este documento quando uma capacidade estrutural for integrada ou quando uma lacuna deixar de existir. Não atualizar por mudança visual, mock ou promessa comercial.

Cada mudança deve citar PR/commit, migrations e testes relevantes da mesma baseline.
