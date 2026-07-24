package com.troquim_bot.whatsapp.flow.application.session;

/**
 * Estados de uma sessão de Flow.
 *
 * Enum em vez de booleanos soltos porque os estados não são independentes: "expirada"
 * e "finalizada" e "invalidada" são mutuamente exclusivos, e um par de flags permitiria
 * combinações sem sentido (finalizada + invalidada) que o código teria de tratar.
 */
public enum FlowSessionStatus {

    /** Sessão válida e em uso. Único estado que aceita novas trocas. */
    ABERTA,

    /** O agendamento foi confirmado. A sessão não pode gerar um segundo agendamento. */
    CONCLUIDA,

    /**
     * Passou do {@code expiraEm}. Marcado na leitura, não por rotina agendada: depender
     * de limpeza periódica deixaria uma janela em que a sessão vencida ainda funciona.
     */
    EXPIRADA,

    /**
     * Anulada deliberadamente — hoje, quando o envio da mensagem do Flow falha depois de
     * a sessão já ter sido gravada. Sem isto ficaria um token válido que ninguém recebeu.
     */
    INVALIDADA;

    /** Só a sessão aberta aceita trocas; a leitura ainda precisa checar o vencimento. */
    public boolean aceitaTroca() {
        return this == ABERTA;
    }
}
