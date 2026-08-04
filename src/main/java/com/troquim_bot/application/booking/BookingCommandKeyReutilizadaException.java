package com.troquim_bot.application.booking;

/**
 * Uma {@link BookingCommandKey} foi reivindicada (ou já concluída) com um fingerprint
 * DIFERENTE do que está gravado — ou seja, a mesma chave de idempotência externa (ex.: o
 * cabeçalho {@code Idempotency-Key} de um canal HTTP) foi reutilizada com um payload
 * diferente do da primeira tentativa.
 *
 * TIPADA de propósito: quem consome (hoje, a API pública de agendamento) precisa distinguir
 * este caso de uma falha técnica genérica sem depender de {@code getMessage()}. O canal
 * traduz para o código de erro estável que expõe (ex.: HTTP 409 IDEMPOTENCY_KEY_REUSED).
 *
 * NÃO é usada pelo caminho {@link BookingCommandKey#de}, cujo {@code valor} já incorpora o
 * fingerprint — lá, "mesma chave, fingerprint diferente" só ocorreria por colisão de SHA-256.
 * É o caminho {@link BookingCommandKey#deChaveExclusiva}, cujo {@code valor} é derivado SÓ de
 * (businessId, chave externa), que torna essa divergência um caso legítimo e esperado.
 */
public class BookingCommandKeyReutilizadaException extends RuntimeException {

    public BookingCommandKeyReutilizadaException(String message) {
        super(message);
    }
}
