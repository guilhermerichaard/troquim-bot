package com.troquim_bot.application.booking;

/**
 * Resultado da tentativa de confirmar um agendamento.
 *
 * Carrega apenas dados; a decisão de como responder ao cliente é da camada
 * de conversa (StrictMvpMenuService), que traduz o status em uma mensagem.
 */
public record BookingResult(Status status, String mensagem,
                            String servico, String dia, String horario, String nome) {

    public enum Status {
        /** Customer, Reservation e Appointment criados/persistidos com sucesso. */
        CONFIRMADO,
        /** Horário indisponível (conflito). Nenhum dado parcial foi criado. */
        INDISPONIVEL,
        /** Dados do rascunho não puderam ser interpretados (dia/horário inválido). */
        INVALIDO
    }

    public boolean isConfirmado() {
        return status == Status.CONFIRMADO;
    }

    public static BookingResult confirmado(String servico, String dia, String horario, String nome) {
        return new BookingResult(Status.CONFIRMADO, null, servico, dia, horario, nome);
    }

    public static BookingResult indisponivel(String mensagem) {
        return new BookingResult(Status.INDISPONIVEL, mensagem, null, null, null, null);
    }

    public static BookingResult invalido(String mensagem) {
        return new BookingResult(Status.INVALIDO, mensagem, null, null, null, null);
    }
}
