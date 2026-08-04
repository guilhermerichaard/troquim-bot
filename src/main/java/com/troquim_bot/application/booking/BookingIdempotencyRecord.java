package com.troquim_bot.application.booking;

import com.troquim_bot.appointment.AppointmentId;

import java.util.Optional;

/**
 * Desfecho persistido de um comando de confirmação já concluído.
 *
 * Guarda o suficiente para RECONSTRUIR a resposta sem reexecutar o negócio: a referência
 * ao Appointment criado e os rótulos que a tela de sucesso precisa. Não é a fonte da
 * verdade do agendamento — essa é o próprio Appointment; isto é o recibo do comando.
 *
 * @param appointmentId agendamento criado; vazio quando o comando terminou sem criar
 *                      (ex.: conflito de horário, registrado para não reprocessar)
 * @param status        desfecho NEUTRO reproduzido em um retry — ver {@link BookingIdempotencyOutcome}
 */
public record BookingIdempotencyRecord(String commandKey,
                                       String fingerprint,
                                       Optional<AppointmentId> appointmentId,
                                       BookingIdempotencyOutcome status,
                                       String servico,
                                       String dataIso,
                                       String horario,
                                       String nome) {

    /**
     * Reconstrói o resultado do caminho canônico de booking a partir do recibo.
     *
     * {@link BookingIdempotencyOutcome#SELECAO_INDISPONIVEL} não tem {@link BookingResult}
     * equivalente (nunca é gravado por {@link BookingApplicationService}) — mapeado para
     * falha técnica de forma defensiva, caso este método seja chamado sobre um recibo
     * escrito por {@link RegistrarDesfechoDeBookingSemAgendamento} em vez do caminho
     * canônico.
     */
    public BookingResult comoResultado() {
        return switch (status) {
            case CONFIRMADO -> BookingResult.confirmado(servico, dataIso, horario, nome);
            case HORARIO_INDISPONIVEL -> BookingResult.indisponivel("Esse horario ja esta ocupado.");
            case PEDIDO_INVALIDO -> BookingResult.invalido("Dados do agendamento invalidos.");
            case SESSAO_JA_CONFIRMADA -> BookingResult.sessaoJaConfirmada();
            // Falha técnica NUNCA é gravada: ela some no rollback junto com o resto,
            // justamente para que o retry possa tentar de novo.
            case FALHA_TECNICA, SELECAO_INDISPONIVEL -> BookingResult.falhaTecnica();
        };
    }
}
