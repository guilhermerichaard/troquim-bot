# HANDOFF — /app + Embedded Signup do dono

Contexto para continuar sem reler todo o histórico. Atualizado ao fim de cada fase.

## Estado base
- Produção: `84f2073` saudável. Preview do Flow ATIVO (`DRAFT=true`), não agenda. **Não tocar.**
- Embedded Signup backend genérico já existia em `2ba693f` (`whatsapp/channel/**`): cifragem
  AES-GCM, `ChannelConnection` (PENDENTE/CONECTADO/FALHOU), `JpaChannelConnectionStore`,
  `GraphApiMetaOAuthGateway`, migration V7, endpoints `/api/v1/admin/whatsapp/connections/*`
  sob Bearer admin. **Aproveitar, não recriar.** Lacunas: state opaco (não assinado), preso a
  tenant fixo, sem revogar, sob Bearer estático em vez de sessão de dono.

## Achados de arquitetura (fixos, não repetir descoberta)
- Catálogo do Flow é FIXO em código (`FlowCatalogProvider`): `professional_id` sintético
  compartilhado entre negócios. Por isso agenda TEM de ser escopada por `business_id`.
- Só `appointments`/`reservations` foram tenantizados (V8). `services/professionals/
  availability` não alimentam o Flow (catálogo fixo) → ficam fora até virarem dado.
- Tenant vem SEMPRE de fonte autoritativa: sessão do Flow / command key / usuário autenticado.
  Nunca do payload do cliente.
- `TenantProvider` (`PilotTenantProvider`) é fixo no piloto → serve caminhos legados
  (webhook/conversa), NUNCA o `/app`.

---

## Fase 1 — tenancy da agenda  [CONCLUÍDA]
Agenda multi-tenant. `BusinessId` obrigatório em `Appointment`/`Reservation`; leituras e
conflito escopados por `(business_id, professional_id, date)`; V8 com backfill pelo
placeholder Flyway. `AgendaDoNegocioService` (leitura da agenda do dono, delega ao mesmo
Application Service do Flow). Bug corrigido: construtor no-arg de `BusinessApplicationService`
(mesmo defeito de `/customers`).
- Testes: `AppointmentTenantIsolationTest` (3) + suíte 810 verde.
- SHA local: c47fcc2
