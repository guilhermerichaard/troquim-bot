# Tenancy Customer — Estado e pendências

## Identificação

- **branch atual:** feat/tenancy-customer-foundation
- **caminho do repositório:** C:/Projetos/troquim/troquim-bot
- **último commit:** 74f6ebf (HEAD -> feat/tenancy-customer-foundation, feature/conversation-engine-v2, feat/mvp-security-boundary) fix: enforce JPA customer wiring and isolate test repositories
- **confirmação de alterações sem commit:** sim — `git status --short` lista 24 arquivos modificados (M) e 11 arquivos não rastreados (??), incluindo migrations, código produtivo e testes.
- **confirmação de push/deploy:** nenhum push ou deploy foi executado nesta tarefa.

## Implementado

Confirmação baseada no working tree e diff:

- **integração do Flyway:** presente em `pom.xml` e configurações de aplicação.
- **migrations V1 e V2:** `src/main/resources/db/migration/V1__baseline_schema.sql` e `V2__customer_tenancy.sql` existem e estão não rastreados.
- **BusinessId obrigatório no Customer:** `Customer.java` e `CustomerId.java` refletem a alteração.
- **CustomerId surrogate:** `CustomerId.java` e `CustomerJpaEntity.java` implementam surrogate key.
- **normalização E.164:** `PhoneNumber.java` contém a lógica de normalização.
- **isolamento por BusinessId:** `CustomerRepository`, `JpaCustomerRepository` e `CustomerApplicationService` aplicam filtro por `BusinessId`.
- **unicidade business_id + phone_e164:** refletida na entidade JPA e repositórios.
- **CustomerRepository tenant-aware:** interface e implementações Spring Data/JPA incluem `BusinessId` no contrato.
- **TenantProvider e PilotTenantProvider:** `TenantProvider.java` (porta) e `PilotTenantProvider.java` (implementação MVP) existem em `src/main/java/com/troquim_bot/business/`.
- **alterações no CustomerController:** `CustomerController.java` modificado para usar tenant-aware repository.
- **testes PostgreSQL/Testcontainers:** `CustomerPostgresPersistenceTest.java`, `CustomerTenancyMigrationPostgresTest.java` e demais testes de persistência/tenancy existem em `src/test/`.
- **documentação TENANCY_CUSTOMER_SLICE.md:** `docs/architecture/TENANCY_CUSTOMER_SLICE.md` existe e está não rastreado.

## Testes informados

- **baseline anterior:** 611 testes.
- **último resultado informado pelo Claude:** 626 testes, 0 falhas.
- **nota:** esta tarefa de checkpoint/documentação não executa novamente a suíte de testes.

## Bloqueadores pendentes

Registrados exatamente como identificados:

1. **TROQUIM_PILOT_BUSINESS_ID como fonte única.** Atualmente o UUID do piloto está hardcoded em `PilotTenantProvider` e repetido na migration V2; precisa ser centralizado em uma única configuração/profile.
2. **Remover UUID literal duplicado do código produtivo e migrations.** O mesmo UUID aparece em `PilotTenantProvider` e em `V2__customer_tenancy.sql`; deve haver uma única fonte de verdade.
3. **Passar o BusinessId ao Flyway por placeholder.** A migration V2 deve receber o `BusinessId` via placeholder/configuração do Flyway, não por valor hardcoded.
4. **Fazer o profile azure falhar quando o UUID estiver ausente ou inválido.** `application-azure.properties` deve validar a presença e formato de `troquim.tenant.pilot-business-id`.
5. **Revisar integralmente V1 e V2 para o primeiro deploy.** Validar nomes de colunas, constraints, índices e compatibilidade com o schema real.
6. **Criar runbook da primeira migration em produção.** Documentar passo a passo de execução, rollback e validação pós-migração.
7. **Validar migrations contra uma cópia do schema real da Droplet.** Garantir que V1+V2 aplicam corretamente sobre o schema existente.
8. **Documentar explicitamente a divergência temporária:**
   - `Customer` usa `CustomerId` surrogate;
   - `Appointment`, `Reservation` e fluxos legados ainda podem usar `CustomerId.fromPhone`;
   - atualmente esses IDs não representam necessariamente a mesma identidade.
9. **Adicionar testes da configuração do profile azure.** Validar carregamento de `troquim.tenant.pilot-business-id` e falhas controladas.
10. **Não autorizar deploy antes de concluir esses bloqueadores.**

## Próximo passo seguro

Após o limite do Claude ser restabelecido, o trabalho deve continuar:

- na mesma pasta (`docs/architecture/` e código relacionado);
- na mesma branch (`feat/tenancy-customer-foundation`);
- sem resetar ou descartar o working tree;
- usando este documento como checkpoint.

## Validação final

- [x] Documento criado em `docs/architecture/TENANCY_CUSTOMER_PENDING.md`.
- [x] Nenhum outro arquivo alterado por esta tarefa.