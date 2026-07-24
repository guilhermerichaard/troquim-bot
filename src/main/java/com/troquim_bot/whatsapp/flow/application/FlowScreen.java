package com.troquim_bot.whatsapp.flow.application;

/**
 * Telas DECLARADAS do Flow de agendamento. Os ids são o contrato com o Flow JSON
 * publicado na Meta — renomear qualquer um quebra Flows já publicados, por isso ficam
 * centralizados aqui e nunca aparecem como literal solto nos handlers.
 *
 * Máximo de quatro telas declaradas (contrato canônico do MVP):
 * SERVICO → AGENDA → CLIENTE → CONFIRMACAO(terminal).
 *
 * O encerramento NÃO é uma tela declarada: o endpoint responde com a tela RESERVADA
 * {@code "SUCCESS"} da Meta ({@link FlowResponse#SCREEN_SUCCESS_RESERVADA}), que fecha o
 * Flow e injeta a resposta na conversa.
 */
public enum FlowScreen {

    /** Escolha do serviço + profissional (opcional, habilitado após o serviço). Entrada. */
    SERVICO,

    /** Escolha de data (dropdown de datas elegíveis) + horário (habilitado após a data). */
    AGENDA,

    /** Nome (pré-preenchido quando conhecido) e observações opcionais. */
    CLIENTE,

    /** Resumo + aceite. Marcada {@code terminal:true} no JSON; o footer dispara o CONFIRMAR. */
    CONFIRMACAO;

    public String id() {
        return name();
    }
}
