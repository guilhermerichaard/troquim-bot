package com.troquim_bot.application.booking;

/**
 * Falha de PERSISTÊNCIA durante a confirmação — nunca uma regra de agenda.
 *
 * Existe para que a falha técnica seja sempre uma exceção, e nunca um retorno normal.
 * A razão é a idempotência: quando esta exceção sobe, a reivindicação do comando, a
 * Reservation e o Appointment sofrem rollback JUNTOS. Um retorno normal comitaria uma
 * chave reivindicada e sem desfecho, e todo retry dela ficaria preso em "em andamento".
 *
 * Quem consome traduz para a mensagem neutra de falha técnica. A mensagem desta exceção
 * é diagnóstica: nunca é exibida ao cliente e nunca carrega payload ou dado pessoal.
 */
public class BookingPersistenceException extends RuntimeException {

    public BookingPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
