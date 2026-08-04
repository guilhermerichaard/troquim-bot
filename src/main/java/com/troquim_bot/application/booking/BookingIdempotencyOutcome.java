package com.troquim_bot.application.booking;

/**
 * Desfecho NEUTRO de um comando de booking já concluído, persistido no recibo de
 * idempotência ({@link BookingIdempotencyRecord}).
 *
 * Existe separado de {@link BookingResult.Status} porque nem todo desfecho que o recibo
 * precisa representar nasce de {@link BookingApplicationService} — a recusa de catálogo
 * ({@code SELECAO_INDISPONIVEL}), registrada por {@link RegistrarDesfechoDeBookingSemAgendamento}
 * para o canal HTTP público, nunca chega a criar Reservation/Appointment e por isso não tem
 * equivalente em {@link BookingResult.Status}. Acoplar o recibo diretamente ao enum do
 * resultado de booking obrigaria a inventar um {@code BookingResult} falso para um caso que
 * não é booking nenhum.
 *
 * Conversões para/de {@link BookingResult.Status} são EXPLÍCITAS, nos próprios chamadores
 * ({@link BookingApplicationService}, {@link BookingIdempotencyRecord#comoResultado()}) —
 * este enum não sabe nada sobre HTTP nem sobre o caminho canônico de booking.
 */
public enum BookingIdempotencyOutcome {
    /** Customer, Reservation e Appointment criados/persistidos com sucesso. */
    CONFIRMADO,
    /** Conflito real de agenda: o horário deixou de estar livre. */
    HORARIO_INDISPONIVEL,
    /** Dados do comando não puderam ser interpretados pelo caminho canônico. */
    PEDIDO_INVALIDO,
    /** A base do comando (Flow) já concluiu outro agendamento. */
    SESSAO_JA_CONFIRMADA,
    /**
     * Serviço ou profissional indisponível para o catálogo do negócio — recusado ANTES de
     * qualquer escrita de agendamento. Nunca produzido por {@link BookingApplicationService}.
     */
    SELECAO_INDISPONIVEL,
    /**
     * Falha técnica — NUNCA gravada de propósito (some no rollback junto com o resto, para
     * que o retry reivindique do zero). Existe aqui só como valor de leitura defensivo para
     * uma linha reivindicada e ainda sem desfecho.
     */
    FALHA_TECNICA
}
