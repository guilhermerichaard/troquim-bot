# Domain Model - Troquim

Este documento aponta para o mapa de domínio atual e mantém uma visão curta do estado implementado.

A referência principal do domínio está em [domain/domain-map.md](domain/domain-map.md).

## Aggregates atuais

| Aggregate | Responsabilidade atual |
| --- | --- |
| `Business` | Representa o salão/negócio atual do MVP, com dados de contato, horário de funcionamento e status. |
| `Service` | Representa um serviço oferecido, com descrição, duração, preço e status. |
| `Customer` | Representa o cliente final, com nome, telefone, observações e status. |
| `Professional` | Representa um profissional do salão, com especialidades, telefone e status. |
| `Availability` | Representa uma janela de disponibilidade de um Professional por dia da semana e intervalo de horário. |

## Relações atuais

- `Availability` referencia `Professional` por `professionalId`.
- `Business`, `Service` e `Professional` formam a base operacional do salão no MVP.
- `Customer` está implementado como cadastro independente neste recorte.
- `Service`, `Professional` e `Customer` não possuem `businessId` no aggregate atual.

## Status atuais

| Aggregate | Estados |
| --- | --- |
| `Business` | `TRIAL`, `ATIVO`, `INATIVO`, `SUSPENSO`, `DELETADO` |
| `Service` | `ATIVO`, `INATIVO` |
| `Customer` | `ATIVO`, `INATIVO` |
| `Professional` | `ATIVO`, `INATIVO` |
| `Availability` | `ATIVO`, `INATIVO` |

## Observação

Este resumo substitui a visão antiga deste arquivo por uma visão alinhada ao código atual. Detalhes por capability ficam nos documentos em `docs/02 Capabilities`.
