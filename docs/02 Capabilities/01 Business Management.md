# Business Management

Estado atual documentado a partir da base operacional já implementada para o salão.

## Escopo atual

Business Management registra a base operacional do salão no MVP:

- `Business`: dados do salão e horário de funcionamento padrão;
- `Service`: serviços oferecidos, duração e preço;
- `Professional`: profissionais que executam serviços.

Essas capacidades existem como cadastros operacionais. O código atual ainda não liga explicitamente Service e Professional a um Business por campo de domínio.

## Business

`Business` é um Aggregate Root em `com.troquim_bot.business`.

Campos principais:

- `id`: `BusinessId`, UUID;
- `nome`;
- `telefone`;
- `endereco`;
- `horarioFuncionamento`: `BusinessHours`;
- `status`: `TRIAL`, `ATIVO`, `INATIVO`, `SUSPENSO`, `DELETADO`;
- `criadoEm`;
- `atualizadoEm`.

Regras atuais:

- nome é obrigatório;
- deve haver telefone ou endereço;
- horário de funcionamento é obrigatório;
- `BusinessHours` exige abertura, fechamento, pelo menos um dia de funcionamento e abertura anterior ao fechamento;
- um Business novo criado pelo serviço de aplicação começa em `TRIAL`;
- o MVP assume um Business atual;
- `GET /business` cria um Business padrão se ainda não existir;
- o horário padrão do MVP é segunda a sexta, de `09:00` a `19:00`;
- `podeCriarAgendamentos()` retorna true apenas quando o status é `ATIVO`.

Endpoints atuais:

| Método | Caminho | Função |
| --- | --- | --- |
| `GET` | `/business` | Retorna o Business atual ou cria o padrão do MVP. |
| `PUT` | `/business` | Atualiza `name`, `phone` e/ou `address`. |

## Services

`Service` é um Aggregate Root em `com.troquim_bot.service`.

Campos principais:

- `id`: `ServiceId`, UUID;
- `nome`;
- `descricao`;
- `duracao`: `ServiceDuration`;
- `preco`: `Money`;
- `status`: `ATIVO`, `INATIVO`;
- `criadoEm`;
- `atualizadoEm`.

Regras atuais:

- nome é obrigatório;
- duração é obrigatória e deve ser maior que zero;
- preço é obrigatório e não pode ser negativo;
- descrição é opcional;
- novo serviço começa com status `ATIVO`;
- `DELETE /services/{id}` inativa o serviço, mantendo o registro acessível pelo MVP;
- `podeSerAgendado()` retorna true quando o serviço está `ATIVO`.

Endpoints atuais:

| Método | Caminho | Função |
| --- | --- | --- |
| `GET` | `/services` | Lista todos os serviços. |
| `GET` | `/services/{id}` | Busca serviço por UUID. |
| `POST` | `/services` | Cria serviço com `name`, `description`, `durationMinutes` e `price`. |
| `PUT` | `/services/{id}` | Atualiza campos informados. |
| `DELETE` | `/services/{id}` | Inativa serviço. |

## Professionals

`Professional` é um Aggregate Root em `com.troquim_bot.professional`.

Campos principais:

- `id`: `ProfessionalId`, UUID;
- `nome`;
- `especialidades`;
- `telefone`;
- `status`: `ATIVO`, `INATIVO`;
- `criadoEm`;
- `atualizadoEm`.

Regras atuais:

- nome é obrigatório;
- deve haver pelo menos uma especialidade;
- telefone é obrigatório;
- novo profissional começa com status `ATIVO`;
- atualizações alteram apenas campos informados;
- `DELETE /professionals/{id}` inativa o profissional.

Endpoints atuais:

| Método | Caminho | Função |
| --- | --- | --- |
| `GET` | `/professionals` | Lista todos os profissionais. |
| `GET` | `/professionals/{id}` | Busca profissional por UUID. |
| `POST` | `/professionals` | Cria profissional com `name`, `specialties` e `phone`. |
| `PUT` | `/professionals/{id}` | Atualiza campos informados. |
| `DELETE` | `/professionals/{id}` | Inativa profissional. |

## Base operacional do salão

No código atual, a operação mínima do salão é formada por:

- um Business atual com dados de contato e horário de funcionamento;
- uma lista de Services com duração, preço e status;
- uma lista de Professionals com especialidades, telefone e status;
- Availabilities associadas a Professionals por `professionalId`.

Esse conjunto permite cadastrar a estrutura básica necessária para operar o salão no MVP, sem cobrir agenda completa.

## Testes

Cobertura atual relacionada a esta base:

- `BusinessControllerTest`
  - retorno do Business atual;
  - criação automática do Business padrão no primeiro `GET /business`;
  - atualização parcial de nome, telefone e endereço;
  - garantia de que `GET /business` não duplica o Business existente.
- `ServiceControllerTest`
  - listagem, busca por ID, criação, atualização parcial e inativação;
  - validação de request nula e IDs inválidos;
  - descrição opcional;
  - serviço inativado fora da lista de ativos na camada de aplicação.
- `ProfessionalControllerTest`
  - listagem, busca por ID, criação, atualização parcial e inativação;
  - validação de nome, especialidades e telefone;
  - garantia de que múltiplos profissionais são listados sem duplicação.

## Limites atuais

- Service não possui `businessId`.
- Professional não possui `businessId`.
- Business não possui endpoint de criação explícito; o MVP cria o padrão no `GET /business`.
- Não há endpoint REST para reativar Service ou Professional, embora as entidades e services possuam métodos de ativação.
