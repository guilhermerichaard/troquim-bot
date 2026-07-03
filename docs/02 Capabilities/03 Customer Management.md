# Customer Management

Estado atual documentado a partir da Feature 03 - Customer MVP.

## Escopo atual

Customer Management cobre o cadastro e o ciclo de vida básico de clientes finais do Troquim. O código atual expõe operações REST para criar, listar, buscar, atualizar e inativar clientes.

Não há, no código atual desta feature, vínculo direto de Customer com Business, Appointment ou qualquer regra de unicidade por salão.

## Customer Aggregate

`Customer` é um Aggregate Root em `com.troquim_bot.customer`.

Responsabilidades atuais:

- manter os dados básicos do cliente;
- validar identificador, nome e telefone;
- controlar o status `ATIVO` ou `INATIVO`;
- atualizar `atualizadoEm` quando dados ou status mudam.

## Campos

| Campo | Tipo no domínio | Obrigatório | Observações |
| --- | --- | --- | --- |
| `id` | `CustomerId` | Sim | UUID gerado na criação. |
| `name` | `CustomerName` | Sim | Criado a partir de nome completo. O value object separa primeiro nome e sobrenome. |
| `phone` | `PhoneNumber` | Sim | Remove espaços, hífens e parênteses antes de validar. |
| `notes` | `String` | Não | Observações opcionais; quando informadas, são aparadas. |
| `status` | `CustomerStatus` | Sim | Valores atuais: `ATIVO`, `INATIVO`. |
| `criadoEm` | `LocalDateTime` | Sim | Definido na criação. |
| `atualizadoEm` | `LocalDateTime` | Sim | Atualizado em mudanças de dados ou status. |

## Endpoints

Base path: `/customers`.

| Método | Caminho | Função | Corpo esperado | Respostas atuais |
| --- | --- | --- | --- | --- |
| `GET` | `/customers` | Lista todos os clientes. | Nenhum. | `200` com array, incluindo ativos e inativos. |
| `GET` | `/customers/{id}` | Busca cliente por UUID. | Nenhum. | `200` quando encontra, `404` quando não encontra, `400` para UUID inválido. |
| `POST` | `/customers` | Cria cliente. | `name`, `phone`, `notes`. | `201` quando cria, `400` para request inválida ou dados inválidos. |
| `PUT` | `/customers/{id}` | Atualiza campos informados. | `name`, `phone`, `notes`. | `200` quando atualiza, `400` para ID/dados inválidos ou cliente inexistente. |
| `DELETE` | `/customers/{id}` | Inativa cliente. | Nenhum. | `204` quando inativa, `400` para UUID inválido ou cliente inexistente. |

### Request de criação

```json
{
  "name": "Joao Silva",
  "phone": "+5511999999999",
  "notes": "Cliente VIP"
}
```

### Response

```json
{
  "id": "uuid",
  "name": "Joao Silva",
  "phone": "+5511999999999",
  "notes": "Cliente VIP",
  "status": "ATIVO",
  "criadoEm": "data-hora",
  "atualizadoEm": "data-hora"
}
```

## Regras

- `name` é obrigatório na criação.
- `phone` é obrigatório na criação.
- `CustomerName.of(...)` exige primeiro nome e sobrenome válidos.
- `PhoneNumber` aceita número com dígitos e `+` opcional após limpar espaços, hífens e parênteses.
- `notes` é opcional.
- Novos clientes são criados com status `ATIVO`.
- Atualização via REST altera apenas campos informados e não vazios para `name` e `phone`.
- Atualização de `notes` via REST ocorre quando o campo é enviado com valor não nulo.
- O Application Service também possui métodos para listar ativos, ativar e inativar clientes.

## Soft Delete

`DELETE /customers/{id}` não remove o cliente da coleção em uso pelo MVP. A operação chama `inativarCliente`, que muda o status para `INATIVO`.

Consequências atuais:

- o cliente inativado continua existindo e pode ser buscado por ID;
- `GET /customers` continua retornando clientes inativos;
- `listarAtivos()` existe na camada de aplicação e filtra clientes com `status == ATIVO`;
- não existe endpoint REST separado para listar apenas clientes ativos.

## Testes

Cobertura atual relacionada a Customer:

- `CustomerApplicationServiceTest`
  - criação com sucesso;
  - criação sem observações;
  - validação de nome e telefone obrigatórios;
  - busca por ID;
  - listagem de todos;
  - listagem de ativos;
  - atualização de nome, telefone e observações;
  - limpeza de observações na camada de aplicação;
  - inativação e ativação;
  - verificação de existência.
- `CustomerControllerTest`
  - `GET /customers`;
  - `GET /customers/{id}`;
  - `POST /customers`;
  - `PUT /customers/{id}`;
  - `DELETE /customers/{id}`;
  - respostas para ID inválido, request nula e entidades inexistentes;
  - confirmação de soft delete por status `INATIVO`.

## Limites atuais

- Customer não possui `businessId`.
- Customer não possui e-mail.
- Customer não possui status de bloqueio.
- Não há regra implementada de telefone único por Business.
- Não há endpoint REST para reativar Customer, embora a camada de aplicação tenha `ativarCliente`.
