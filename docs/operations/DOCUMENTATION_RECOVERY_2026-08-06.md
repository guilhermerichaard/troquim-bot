# Recuperação documental — encerramento do “versionamento sem fim”

Data da recuperação: 6 de agosto de 2026  
Baseline técnico: `main` em `a154e10b6791c9f0cf5c299a20431a98552bb959`

## Objetivo

Encerrar o ciclo de ZIPs `v2`, `v3`, `v4`, `v5...` e transformar a documentação operacional em arquivos canônicos versionados junto do código que descrevem.

Este documento não autoriza deploy, alteração de produção, ativação da Cloud API, restore de Redis ou configuração comercial da Meta.

## Causa raiz

O trabalho anterior produziu quatro arquivos locais não rastreados:

- `docs/architecture/CATALOGO_E_AGENDA_PENDENTE.md`;
- `docs/operations/CLOUD_API_RUNBOOK.md`;
- `docs/operations/PILOT_READINESS.md`;
- `docs/operations/REDIS_BACKUP_RESTORE_AOF.md`.

Eles foram revisados e empacotados repetidamente, mas nunca entraram em uma branch e nunca acompanharam a evolução real da `main`. Enquanto isso, o backend avançou de `dca43ea` para `a154e10`.

Consequência: corrigir o mesmo ZIP outra vez não produziria documentação confiável. O problema não era falta de uma nova versão; era ausência de fonte canônica versionada.

## Decisão

1. Os ZIPs antigos passam a ser **artefatos históricos**, não candidatos a commit.
2. Nenhum arquivo recebe sufixo `v6`, `v7` ou equivalente.
3. A documentação operacional passa a ser atualizada diretamente em branch, por PR, ligada a um SHA-base explícito.
4. Afirmações de estado de ambiente devem ser datadas e reconfirmadas antes de operação.
5. Fatos do código devem apontar para classes, migrations, contratos ou testes da mesma baseline.
6. Documentação obsoleta deve ser marcada ou substituída; não pode coexistir como se fosse atual.

## O que mudou desde o snapshot antigo

O diagnóstico antigo dizia que catálogo, agenda, tenant e API pública eram bloqueadores principais. Essa leitura não pode mais ser copiada para a documentação atual.

Desde `dca43ea`, foram integrados:

- catálogo tenant-scoped e persistido;
- profissionais e vínculos profissional-serviço persistidos;
- expediente e disponibilidade reais persistidos;
- duração real por serviço;
- conflito por sobreposição;
- `Business` persistido como raiz do tenant;
- calendário do negócio centralizado;
- identidade pública por slug;
- catálogo e disponibilidade públicos por slug;
- serialização atômica de slots;
- tenant explícito no booking;
- endpoint público idempotente de criação de agendamento.

A validação registrada no PR mais recente da baseline é `./mvnw -B -ntp clean verify` com **1.183 testes**, sem falhas, erros ou testes pulados.

## Destino dos quatro temas originais

### 1. WhatsApp Cloud API

Arquivo canônico existente:

- `docs/operations/WHATSAPP_CLOUD_INTEGRATION.md`.

Não criar um segundo runbook concorrente chamado `CLOUD_API_RUNBOOK.md`.

Revisão necessária no arquivo canônico:

- deixar explícito que um `401` isolado não prova se a integração está ligada ou desligada;
- distinguir rota sem handler, dispatch para `/error` negado e assinatura HMAC inválida;
- separar teste técnico, ativação permanente e operação comercial;
- exigir verificação de flag na fonte de configuração e no container em execução;
- exigir status não zero em falhas de ativação/desativação;
- preservar placeholders e proibição de segredos.

A allowlist atual libera GET e POST exatos de `/webhook/whatsapp/cloud`; todas as demais rotas caem em `denyAll()` e o `authenticationEntryPoint` devolve `401`. Portanto, status HTTP sem contexto não é evidência suficiente do estado da feature flag.

### 2. Redis backup/restore

Não há, na baseline analisada, um runbook canônico equivalente ao antigo `REDIS_BACKUP_RESTORE_AOF.md`.

O novo documento deve:

- tratar restore como procedimento de emergência, nunca teste de revisão;
- separar snapshot observado de garantia permanente;
- exigir SHA-256 do backup, janela acordada, serviços parados e rollback definido;
- validar AOF antes de reiniciar;
- evitar concluir que restaurar apenas `dump.rdb` é suficiente quando AOF é a fonte efetiva de carga;
- não executar nenhum comando de restore durante a elaboração documental.

Todo dado sobre container, imagem, `DBSIZE`, PID, AOF ou caminhos de volume precisa ser reconfirmado no ambiente antes de entrar como “estado atual”.

### 3. Prontidão do piloto

O antigo `PILOT_READINESS.md` não pode ser reaproveitado como matriz atual porque seus bloqueadores centrais foram alterados pelos PRs #2 a #7.

A nova matriz deve avaliar a baseline atual e separar:

- capacidade técnica implementada;
- configuração real do negócio piloto;
- frontend conectado à API pública;
- autenticação e área da empreendedora;
- notificações;
- observabilidade;
- consentimento e políticas;
- número real e operação comercial da Meta;
- procedimentos de suporte e rollback.

Teste verde prova o comportamento implementado. Não prova configuração comercial, credenciais, catálogo do cliente, disponibilidade publicada, consentimento ou operação humana.

### 4. Catálogo e agenda

O antigo `CATALOGO_E_AGENDA_PENDENTE.md` deve ser substituído por um documento de **estado atual e lacunas restantes**.

Não são mais pendências estruturais:

- persistência do catálogo;
- tenant em serviços e profissionais;
- vínculo profissional-serviço;
- expediente persistido;
- disponibilidade persistida;
- duração real;
- conflito por sobreposição;
- identidade pública;
- API pública de leitura e booking.

Lacunas atuais devem ser verificadas contra o piloto, sem inferência automática. Exemplos a auditar:

- provisionamento de dados reais do Studio;
- frontend consumindo os contratos públicos;
- área protegida do empreendedor;
- cancelamento e remarcação públicos/operacionais;
- notificações;
- observabilidade e suporte;
- configuração comercial do canal.

## Documento obsoleto identificado

`docs/05-technical-roadmap.md` contém uma auditoria antiga que ainda afirma, entre outros pontos, ausência de testes de integração, ausência de `@Transactional`, dependência generalizada de adapters in-memory e falta de separação arquitetural.

Essas afirmações não representam a baseline `a154e10` e não devem continuar sendo usadas como roadmap vigente.

Tratamento recomendado:

- preservar o histórico no Git;
- substituir o conteúdo por um aviso de documento histórico e links para fontes atuais, ou reescrever integralmente;
- não corrigir item por item como remendo, porque a estrutura inteira parte de um snapshot antigo.

## Ordem de implementação documental

1. Corrigir `WHATSAPP_CLOUD_INTEGRATION.md` no ponto do `401` e dos gates operacionais.
2. Criar `REDIS_BACKUP_RESTORE_AOF.md` com pré-condições, validação e rollback, sem executar restore.
3. Criar nova `PILOT_READINESS.md` baseada em `a154e10` e no estado real do piloto.
4. Criar `CATALOGO_E_AGENDA_STATUS.md`, substituindo a narrativa de pendência estrutural.
5. Marcar `docs/05-technical-roadmap.md` como histórico ou substituí-lo por roadmap vigente.
6. Executar revisão de segredos, links, comandos shell, coerência entre documentos e `git diff --check`.
7. Abrir um único PR documental. Não gerar outro ZIP intermediário.

## Gate de conclusão

A recuperação documental só termina quando:

- cada afirmação técnica relevante aponta para evidência da baseline;
- snapshots operacionais estão datados;
- nenhum segredo foi incluído;
- comandos destrutivos têm pré-condições, validação e rollback;
- não existem dois documentos canônicos para a mesma responsabilidade;
- o roadmap antigo não aparece como verdade atual;
- o PR altera apenas documentação;
- nenhuma ação de produção foi executada.

## Estado desta branch

Esta branch inicia a recuperação. Ela não declara os demais documentos prontos e não autoriza merge até que os cinco passos documentais sejam concluídos e revisados.
