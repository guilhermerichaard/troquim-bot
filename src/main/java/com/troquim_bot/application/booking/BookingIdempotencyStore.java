package com.troquim_bot.application.booking;

import com.troquim_bot.appointment.AppointmentId;

import java.util.Optional;

/**
 * Porta de idempotência por COMANDO de confirmação.
 *
 * A Application define o contrato; a Infrastructure escolhe o mecanismo atômico. Todas as
 * operações participam da transação do caso de uso — é isso que faz o agendamento e o
 * recibo comitarem ou desaparecerem juntos.
 *
 * O protocolo é claim-then-complete, e a ordem importa:
 * <ol>
 *   <li>{@link #reivindicar} insere a linha da chave ANTES de qualquer escrita de negócio.
 *       Se outra transação já a inseriu, esta chamada devolve o desfecho existente (ou
 *       espera, se aquela ainda estiver aberta);</li>
 *   <li>o caso de uso executa e chama {@link #concluir} na MESMA transação;</li>
 *   <li>commit: chave e agendamento aparecem juntos. Rollback: somem juntos, e o retry
 *       reivindica de novo do zero.</li>
 * </ol>
 */
public interface BookingIdempotencyStore {

    /**
     * Resultado da reivindicação.
     *
     * @param reivindicada {@code true} = esta transação é a dona do comando e deve executá-lo
     * @param existente    desfecho já commitado por outra execução, quando houver
     */
    record Claim(boolean reivindicada,
                 Optional<BookingIdempotencyRecord> existente,
                 Optional<BookingIdempotencyRecord> baseJaConfirmada) {

        public Claim(boolean reivindicada, Optional<BookingIdempotencyRecord> existente) {
            this(reivindicada, existente, Optional.empty());
        }

        public static Claim nova() {
            return new Claim(true, Optional.empty());
        }

        public static Claim jaConcluida(BookingIdempotencyRecord registro) {
            return new Claim(false, Optional.of(registro));
        }

        /**
         * Chave tomada por uma execução que ainda não gravou desfecho. Não deve ocorrer
         * em PostgreSQL (a inserção concorrente bloqueia até o commit), mas é possível em
         * bancos sem esse comportamento — e nunca pode virar agendamento duplicado.
         */
        public static Claim emAndamento() {
            return new Claim(false, Optional.empty());
        }

        /**
         * A BASE do comando já concluiu um agendamento, e este comando é outro.
         * Regra do MVP: um Flow aberto vale por um agendamento.
         *
         * @param jaConfirmado o agendamento que a base produziu, para diagnóstico
         */
        public static Claim baseJaConfirmada(BookingIdempotencyRecord jaConfirmado) {
            return new Claim(false, Optional.empty(), Optional.of(jaConfirmado));
        }
    }

    /**
     * Reivindica a chave. Deve ser ATÔMICA: duas transações simultâneas com a mesma chave
     * não podem ambas receber {@code reivindicada = true}.
     *
     * @param fingerprint SHA-256 do payload canônico; conferido contra o registro
     *                    existente para que um acerto indevido de chave falhe alto em vez
     *                    de devolver o agendamento de outro comando
     */
    Claim reivindicar(BookingCommandKey chave);

    /** Grava o desfecho do comando reivindicado, na mesma transação do agendamento. */
    void concluir(BookingCommandKey chave, AppointmentId appointmentId,
                  BookingResult.Status status, String servico, String dataIso,
                  String horario, String nome);

    /** Consulta direta, sem reivindicar. Usada por testes e diagnóstico. */
    Optional<BookingIdempotencyRecord> buscar(String commandKey);
}
