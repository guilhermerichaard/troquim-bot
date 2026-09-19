# Technical Roadmap — documento histórico

> **Status:** substituído em 6 de agosto de 2026.
>
> O conteúdo anterior deste arquivo foi produzido sobre uma versão antiga do projeto e não representa a arquitetura, os testes ou a prontidão da baseline atual.
>
> O histórico completo continua disponível no Git antes do commit desta substituição.

## Por que foi substituído

A auditoria anterior afirmava, entre outros pontos:

- ausência de testes de integração;
- ausência de transações relevantes;
- dependência generalizada de adapters in-memory;
- catálogo e agenda ainda simulados;
- falta de persistência da raiz do negócio;
- ausência de API pública de catálogo, disponibilidade e booking.

Essas afirmações deixaram de representar o sistema depois das integrações realizadas entre `dca43ea` e `a154e10`.

Corrigir o texto antigo item por item criaria um documento híbrido, difícil de auditar e propenso a novas contradições. Por isso, o diagnóstico antigo foi preservado apenas no histórico e substituído por fontes atuais separadas por responsabilidade.

## Fontes vigentes

### Arquitetura e estado técnico

- `docs/architecture/ARCHITECTURE_V2.md` — arquitetura geral existente; deve ser confrontada com a baseline antes de decisões estruturais.
- `docs/architecture/CATALOGO_E_AGENDA_STATUS.md` — estado atual de catálogo, expediente, disponibilidade e booking.

### Operação

- `docs/operations/WHATSAPP_CLOUD_INTEGRATION.md` — integração oficial da WhatsApp Cloud API.
- `docs/operations/REDIS_BACKUP_RESTORE_AOF.md` — backup, inspeção e restore controlado do Redis.
- `docs/operations/PILOT_READINESS.md` — matriz de prontidão da baseline e do piloto.
- `docs/operations/DOCUMENTATION_RECOVERY_2026-08-06.md` — causa raiz e plano de recuperação documental.

### Execução atual do produto

A meta operacional viva do primeiro piloto é mantida no `troquim-hq`, não neste arquivo.

## Baseline da substituição

- `main`: `a154e10b6791c9f0cf5c299a20431a98552bb959`;
- catálogo e agenda persistidos;
- `Business` persistido;
- perfil público por slug;
- API pública de leitura;
- booking público idempotente;
- serialização concorrente de slots;
- validação registrada no PR #7: 1.183 testes, sem falhas, erros ou testes pulados.

## Regra para próximos roadmaps

Um roadmap vigente deve:

1. apontar para uma baseline explícita;
2. separar fato, risco, decisão e hipótese;
3. citar PRs, migrations e testes;
4. não repetir estado técnico mantido por documentos especializados;
5. priorizar o maior bloqueio do piloto, não uma lista genérica de melhorias;
6. ser atualizado ou substituído quando a baseline mudar de forma estrutural.

## Prioridade atual

A fundação principal de catálogo, agenda e booking não é mais o bloqueador central.

O caminho crítico atual é:

1. provisionar um negócio piloto real;
2. conectar a experiência pública aos contratos existentes;
3. criar a área mínima e protegida da empreendedora;
4. validar notificações nos celulares disponíveis;
5. testar o fluxo completo com usuários externos;
6. medir valor e decidir cobrança.

Novas funcionalidades do backend só entram quando um bloqueio real desse fluxo for comprovado.
