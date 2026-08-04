package com.troquim_bot.application.booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra, de forma ATÔMICA e idempotente, o desfecho de um comando que NUNCA vai virar
 * agendamento — hoje, exclusivamente {@link BookingIdempotencyOutcome#SELECAO_INDISPONIVEL}
 * (serviço/profissional indisponível no catálogo, recusado por
 * {@code ConfirmarAgendamentoDoCatalogo} ANTES de {@link BookingApplicationService} ser
 * chamado).
 *
 * RAZÃO DE EXISTIR: sem isto, uma {@link BookingCommandKey} cuja seleção de catálogo foi
 * recusada NUNCA passa por {@link BookingIdempotencyStore#reivindicar}, então nada vincula
 * aquela {@code Idempotency-Key} externa ao payload recusado — um retry com um payload
 * DIFERENTE sob a MESMA chave seria tratado como comando novo, violando o contrato HTTP
 * ("mesma chave, payload diferente" tem de ser 409, mesmo quando a primeira resposta foi um
 * erro de negócio, não só em caminhos que criam agendamento).
 *
 * RESPONSABILIDADE ÚNICA: reivindicar a chave e concluir com
 * {@code SELECAO_INDISPONIVEL} — nunca cria {@code Customer}, {@code Reservation} ou
 * {@code Appointment}, nunca conhece HTTP, slug, Controller ou DTO. Reusa o MESMO mecanismo
 * atômico ({@code INSERT ... ON CONFLICT} + comparação de fingerprint) que
 * {@link BookingApplicationService} usa para o caminho de agendamento — nenhuma tabela ou
 * lock novo.
 *
 * A transação aqui é PRÓPRIA e pequena — cobre só reivindicar+concluir deste registro. Não
 * envolve {@link BookingApplicationService} nem {@code ConfirmarAgendamentoDoCatalogo}: a
 * decisão de recusar o catálogo já foi tomada (fora de transação, é leitura) antes de chegar
 * aqui.
 */
@Service
public class RegistrarDesfechoDeBookingSemAgendamento {

    private final BookingIdempotencyStore idempotencyStore;

    public RegistrarDesfechoDeBookingSemAgendamento(BookingIdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    /**
     * Reivindica {@code chave} e grava {@code SELECAO_INDISPONIVEL}, sem tocar em nenhuma
     * entidade de negócio.
     *
     * @return o desfecho a considerar: {@code SELECAO_INDISPONIVEL} tanto na reivindicação
     *         fresca quanto no retry de um comando idêntico já concluído; o desfecho já
     *         gravado por OUTRO caminho (ex.: {@code CONFIRMADO}, caso a mesma chave e o
     *         mesmo fingerprint já tenham sido concluídos pelo caminho canônico antes desta
     *         chamada — cenário só possível em corrida, tratado aqui em vez de deixar o
     *         chamador reimplementar a leitura do recibo)
     * @throws BookingCommandKeyReutilizadaException a MESMA chave já foi reivindicada ou
     *         concluída com um fingerprint DIFERENTE — reuso da Idempotency-Key com outro
     *         payload
     */
    @Transactional
    public BookingIdempotencyOutcome registrarSelecaoIndisponivel(BookingCommandKey chave) {
        BookingIdempotencyStore.Claim claim = idempotencyStore.reivindicar(chave);

        if (claim.reivindicada()) {
            idempotencyStore.concluir(chave, null, BookingIdempotencyOutcome.SELECAO_INDISPONIVEL,
                    null, null, null, null);
            return BookingIdempotencyOutcome.SELECAO_INDISPONIVEL;
        }

        if (claim.existente().isPresent()) {
            // Mesma chave, mesmo fingerprint, já concluída — retry seguro do MESMO comando,
            // qualquer que tenha sido o desfecho gravado (fingerprint divergente já teria
            // lançado BookingCommandKeyReutilizadaException dentro de reivindicar()).
            return claim.existente().get().status();
        }

        // Reivindicada por outra execução, ainda sem desfecho visível (ou regra do MVP do
        // Flow, que não se aplica a este keyspace — ver BookingCommandKey.deChaveExclusiva).
        // Sem evidência para afirmar nada: estado indeterminado, retry seguro.
        return BookingIdempotencyOutcome.FALHA_TECNICA;
    }
}
