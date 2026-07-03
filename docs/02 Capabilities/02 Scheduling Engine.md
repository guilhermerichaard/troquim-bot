# Scheduling Engine

Estado atual documentado a partir da Feature 05 - Availability MVP.

## Escopo atual

Este documento registra apenas o recorte implementado de disponibilidade de profissionais. Ele não descreve um motor completo de agendamento, cálculo de slots, reservas, confirmação de agendamentos ou integração com canais externos.

## Availability MVP

`Availability` é um Aggregate Root em `com.troquim_bot.availability` e representa uma janela recorrente de disponibilidade de um profissional em um dia da semana.

Responsabilidades atuais:

- guardar o profissional associado por `professionalId`;
- guardar dia da semana e intervalo de horário;
- garantir que `startTime` seja anterior a `endTime`;
- detectar conflito com outra disponibilidade do mesmo profissional e mesmo dia;
- controlar status `ATIVO` e `INATIVO`.

## Campos

| Campo | Tipo no domínio | Obrigatório | Observações |
| --- | --- | --- | --- |
| `id` | `AvailabilityId` | Sim | UUID gerado na criação. |
| `professionalId` | `ProfessionalId` | Sim | Referência por ID ao profissional. |
| `dayOfWeek` | `DiaSemana` | Sim | Valores: `SEGUNDA`, `TERCA`, `QUARTA`, `QUINTA`, `SEXTA`, `SABADO`, `DOMINGO`. |
| `startTime` | `LocalTime` | Sim | Horário inicial. Exemplo REST: `08:00`. |
| `endTime` | `LocalTime` | Sim | Horário final. Exemplo REST: `12:00`. |
| `status` | `AvailabilityStatus` | Sim | Valores atuais: `ATIVO`, `INATIVO`. |
| `criadoEm` | `LocalDateTime` | Sim | Definido na criação. |
| `atualizadoEm` | `LocalDateTime` | Sim | Atualizado em mudanças de horário ou status. |

## Endpoints

Base path: `/availability`.

| Método | Caminho | Função | Corpo esperado | Respostas atuais |
| --- | --- | --- | --- | --- |
| `GET` | `/availability` | Lista todas as disponibilidades. | Nenhum. | `200` com array, incluindo ativas e inativas. |
| `GET` | `/availability/{id}` | Busca disponibilidade por UUID. | Nenhum. | `200` quando encontra, `404` quando não encontra, `400` para UUID inválido. |
| `POST` | `/availability` | Cria disponibilidade. | `professionalId`, `dayOfWeek`, `startTime`, `endTime`. | `201` quando cria, `400` para request inválida, dados inválidos ou sobreposição. |
| `PUT` | `/availability/{id}` | Atualiza dia e/ou horários. | `dayOfWeek`, `startTime`, `endTime`. | `200` quando atualiza, `400` para ID/dados inválidos ou disponibilidade inexistente. |
| `DELETE` | `/availability/{id}` | Inativa disponibilidade. | Nenhum. | `204` quando inativa, `400` para UUID inválido ou disponibilidade inexistente. |

### Request de criação

```json
{
  "professionalId": "uuid",
  "dayOfWeek": "SEGUNDA",
  "startTime": "08:00",
  "endTime": "12:00"
}
```

### Response

```json
{
  "id": "uuid",
  "professionalId": "uuid",
  "dayOfWeek": "SEGUNDA",
  "startTime": "08:00",
  "endTime": "12:00",
  "status": "ATIVO",
  "criadoEm": "data-hora",
  "atualizadoEm": "data-hora"
}
```

## Regra de não sobreposição

A criação de Availability valida conflito contra disponibilidades existentes.

Uma disponibilidade conflita quando:

- tem o mesmo `professionalId`;
- tem o mesmo `dayOfWeek`;
- a disponibilidade existente está `ATIVO`;
- os intervalos se sobrepõem pela regra `startA < endB && startB < endA`.

Comportamento atual:

- horários sobrepostos para o mesmo profissional e mesmo dia são rejeitados;
- horários não sobrepostos são permitidos;
- horários sobrepostos em dias diferentes são permitidos;
- horários sobrepostos para profissionais diferentes são permitidos;
- uma disponibilidade `INATIVO` não bloqueia a criação de outra no mesmo horário;
- intervalos encostados, como `08:00-12:00` e `12:00-13:00`, não são considerados sobreposição por essa regra.

## Soft Delete

`DELETE /availability/{id}` não remove a disponibilidade da coleção em uso pelo MVP. A operação chama `inativarDisponibilidade`, que muda o status para `INATIVO`.

Consequências atuais:

- a disponibilidade inativada continua existindo e pode ser buscada por ID;
- `GET /availability` continua retornando disponibilidades inativas;
- `listarAtivos()` existe na camada de aplicação e filtra disponibilidades com `status == ATIVO`;
- uma disponibilidade inativa não participa da validação de sobreposição na criação.

## Testes

Cobertura atual relacionada a Availability:

- `AvailabilityApplicationServiceTest`
  - criação com sucesso;
  - validação de `professionalId`, `dayOfWeek`, `startTime` e `endTime` obrigatórios;
  - validação de `startTime < endTime`;
  - rejeição de horário sobreposto para mesmo profissional e mesmo dia;
  - permissão de horário não sobreposto;
  - permissão de sobreposição para profissionais diferentes;
  - permissão de sobreposição em dias diferentes;
  - busca, listagem geral, listagem por profissional e listagem de ativos;
  - atualização de dia e horários;
  - inativação, ativação e existência;
  - criação em horário de disponibilidade já inativada.
- `AvailabilityControllerTest`
  - `GET /availability`;
  - `GET /availability/{id}`;
  - `POST /availability`;
  - `PUT /availability/{id}`;
  - `DELETE /availability/{id}`;
  - respostas para ID inválido, request nula, `professionalId` inválido, `dayOfWeek` inválido e sobreposição.

## Limites atuais

- `professionalId` é validado como UUID no controller, mas a criação não consulta se o Professional existe.
- `UpdateAvailabilityRequest` possui campo `professionalId`, mas o controller atual não altera o profissional de uma disponibilidade.
- A regra de não sobreposição é aplicada na criação. As atualizações de horário validam `startTime < endTime`, mas não reexecutam a checagem de conflito contra outras disponibilidades.
- Não há endpoint REST separado para listar apenas disponibilidades ativas.
- Não há cálculo de slots disponíveis ou criação de Appointment documentado neste arquivo.
