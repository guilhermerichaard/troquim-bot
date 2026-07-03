# Changelog

## Atual

### Feature 03 — Customer MVP

- Adicionado Customer como Aggregate Root do cadastro de clientes.
- Registrados campos `id`, `name`, `phone`, `notes`, `status`, `criadoEm` e `atualizadoEm`.
- Expostos endpoints REST em `/customers` para listar, buscar, criar, atualizar e inativar clientes.
- Implementado soft delete por mudança de status para `INATIVO`.
- Adicionados testes de aplicação e controller para fluxo de Customer.

### Feature 04 — Professional MVP

- Adicionado Professional como Aggregate Root do cadastro de profissionais.
- Registrados campos `id`, `nome`, `especialidades`, `telefone`, `status`, `criadoEm` e `atualizadoEm`.
- Expostos endpoints REST em `/professionals` para listar, buscar, criar, atualizar e inativar profissionais.
- Implementado ciclo de vida `ATIVO` e `INATIVO`.
- Adicionados testes de controller para fluxo de Professional.

### Feature 05 — Availability MVP

- Adicionado Availability como Aggregate Root de disponibilidade do profissional.
- Registrados campos `professionalId`, `dayOfWeek`, `startTime` e `endTime`.
- Expostos endpoints REST em `/availability` para listar, buscar, criar, atualizar e inativar disponibilidades.
- Implementada regra de não sobreposição na criação para mesmo profissional, mesmo dia e disponibilidades ativas.
- Implementado soft delete por mudança de status para `INATIVO`.
- Adicionados testes de aplicação e controller para fluxo de Availability.
