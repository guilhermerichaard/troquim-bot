package com.troquim_bot.controller;

import com.troquim_bot.application.booking.CriarAgendamentoPublico;
import com.troquim_bot.controller.dto.PublicBookingRequest;
import com.troquim_bot.controller.dto.PublicBookingResponse;
import com.troquim_bot.controller.dto.PublicErrorResponse;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * API pública de AGENDAMENTO — único endpoint de escrita da API pública.
 *
 * Nenhum método aqui consulta um Repository diretamente: tudo passa por
 * {@link CriarAgendamentoPublico}, que reusa o caminho canônico de confirmação
 * ({@code ConfirmarAgendamentoDoCatalogo} → {@code BookingApplicationService}) ponta a
 * ponta. Este controller só faz duas coisas: (1) validar a FORMA da requisição HTTP
 * (cabeçalho, UUID, data, horário) e (2) traduzir o resultado neutro da Application em
 * código HTTP + corpo estável — nunca decide regra de negócio.
 *
 * Idempotência é OBRIGATÓRIA e vem do cabeçalho {@code Idempotency-Key}, nunca do corpo.
 */
@RestController
@RequestMapping("/api/v1/public/businesses")
public class PublicBookingController {

    /** Opaco, case-sensitive: letras, dígitos e {@code . _ : -}. */
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,80}$");

    private static final PublicErrorResponse ERRO_NEGOCIO_NAO_ENCONTRADO =
            new PublicErrorResponse("BUSINESS_NOT_FOUND", "Negócio não encontrado.");
    private static final PublicErrorResponse ERRO_PEDIDO_INVALIDO =
            new PublicErrorResponse("INVALID_REQUEST", "Dados do agendamento inválidos.");
    private static final PublicErrorResponse ERRO_SLOT_INDISPONIVEL =
            new PublicErrorResponse("SLOT_UNAVAILABLE", "Esse horário não está mais disponível.");
    private static final PublicErrorResponse ERRO_CHAVE_REUTILIZADA =
            new PublicErrorResponse("IDEMPOTENCY_KEY_REUSED",
                    "Esta Idempotency-Key já foi usada com dados diferentes.");
    private static final PublicErrorResponse ERRO_SELECAO_INDISPONIVEL =
            new PublicErrorResponse("SELECTION_UNAVAILABLE",
                    "Serviço ou profissional indisponível para este negócio.");
    private static final PublicErrorResponse ERRO_FALHA_TEMPORARIA =
            new PublicErrorResponse("BOOKING_TEMPORARILY_UNAVAILABLE",
                    "Não foi possível concluir o agendamento agora. Tente novamente em instantes.");

    private final CriarAgendamentoPublico criarAgendamentoPublico;

    public PublicBookingController(CriarAgendamentoPublico criarAgendamentoPublico) {
        this.criarAgendamentoPublico = criarAgendamentoPublico;
    }

    /**
     * POST /api/v1/public/businesses/{slug}/appointments
     *
     * Cabeçalho {@code Idempotency-Key} OBRIGATÓRIO — nunca logado. Retry com o MESMO
     * cabeçalho e o MESMO corpo devolve 201 de novo, sem duplicar. Retry com o MESMO
     * cabeçalho e corpo DIFERENTE devolve 409 IDEMPOTENCY_KEY_REUSED.
     */
    @PostMapping("/{slug}/appointments")
    public ResponseEntity<?> agendar(@PathVariable String slug,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                     @RequestBody(required = false) PublicBookingRequest corpo) {

        if (idempotencyKey == null || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            return erro(HttpStatus.BAD_REQUEST, ERRO_PEDIDO_INVALIDO);
        }
        if (corpo == null) {
            return erro(HttpStatus.BAD_REQUEST, ERRO_PEDIDO_INVALIDO);
        }

        ServiceId servico;
        ProfessionalId profissional;
        LocalDate data;
        LocalTime horario;
        try {
            servico = ServiceId.from(UUID.fromString(exigirTexto(corpo.getServiceId())));
            profissional = ProfessionalId.from(UUID.fromString(exigirTexto(corpo.getProfessionalId())));
            data = LocalDate.parse(exigirTexto(corpo.getDate()));
            horario = LocalTime.parse(exigirTexto(corpo.getTime()));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return erro(HttpStatus.BAD_REQUEST, ERRO_PEDIDO_INVALIDO);
        }

        if (corpo.getCustomerName() == null || corpo.getCustomerName().isBlank()
                || corpo.getCustomerPhone() == null || corpo.getCustomerPhone().isBlank()) {
            return erro(HttpStatus.BAD_REQUEST, ERRO_PEDIDO_INVALIDO);
        }

        CriarAgendamentoPublico.Pedido pedido = new CriarAgendamentoPublico.Pedido(
                slug, idempotencyKey, servico, profissional, data, horario,
                corpo.getCustomerName().trim(), corpo.getCustomerPhone().trim());

        CriarAgendamentoPublico.Resultado resultado = criarAgendamentoPublico.criar(pedido);

        return switch (resultado.status()) {
            case CONFIRMADO -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(PublicBookingResponse.from(resultado));
            case NEGOCIO_NAO_ENCONTRADO -> erro(HttpStatus.NOT_FOUND, ERRO_NEGOCIO_NAO_ENCONTRADO);
            case PEDIDO_INVALIDO -> erro(HttpStatus.BAD_REQUEST, ERRO_PEDIDO_INVALIDO);
            case HORARIO_INDISPONIVEL -> erro(HttpStatus.CONFLICT, ERRO_SLOT_INDISPONIVEL);
            case CHAVE_IDEMPOTENCIA_REUTILIZADA -> erro(HttpStatus.CONFLICT, ERRO_CHAVE_REUTILIZADA);
            case SELECAO_INDISPONIVEL -> erro(HttpStatus.UNPROCESSABLE_ENTITY, ERRO_SELECAO_INDISPONIVEL);
            case FALHA_TEMPORARIA -> erro(HttpStatus.SERVICE_UNAVAILABLE, ERRO_FALHA_TEMPORARIA);
        };
    }

    /** Corpo malformado (JSON inválido): 400, forma estável de erro — nunca 500 nem stack trace. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<PublicErrorResponse> corpoMalformado() {
        return erro(HttpStatus.BAD_REQUEST, ERRO_PEDIDO_INVALIDO);
    }

    private static String exigirTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("campo obrigatório ausente");
        }
        return valor.trim();
    }

    private static ResponseEntity<PublicErrorResponse> erro(HttpStatus status, PublicErrorResponse corpo) {
        return ResponseEntity.status(status).body(corpo);
    }
}
