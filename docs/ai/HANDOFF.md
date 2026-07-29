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

## Fase 2 — identidade do dono  [CONCLUÍDA]
Módulo `owner/{domain,application,infrastructure,api}`. `OwnerUser` pertence a 1
`BusinessId`. Login e-mail+senha (BCrypt) → cookie HttpOnly/Secure/SameSite=Strict com
token opaco 256 bits; sessão persistida só como SHA-256 do token. `OwnerSessionCookieFilter`
(mesmo slot do `BearerTokenFilter`) injeta `ROLE_OWNER` + `AuthenticatedOwner` como atributo
do request. `/app/**` e `/api/v1/app/**` agora exigem `ROLE_OWNER` em
`SecurityConfigDefaultDeny`. V9: `owner_users`, `owner_sessions` (PK = hash do token).
- Achado: eu mesmo dupliquei o serviço numa resend de instruções no meio da sessão
  (`OwnerAuthenticationService` + `V9__owner_users_and_sessions.sql`, órfãos, removidos
  antes do commit — colidiriam na versão do Flyway).
- Testes: `OwnerAuthIsolationTest` (8) verde.
- SHA local: 3e34216
