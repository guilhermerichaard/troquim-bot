package com.troquim_bot.whatsapp.flow.application;

/**
 * Desfecho de uma troca do Flow, no vocabulário do PROTOCOLO (não do HTTP).
 *
 * A Application não conhece códigos HTTP; a borda traduz. Isso mantém o coordenador
 * testável sem servidor e impede que regra de negócio passe a depender de status code.
 */
public record FlowExchangeOutcome(Status status, FlowResponse resposta) {

    public enum Status {
        /** Resposta normal — deve ser cifrada e devolvida. */
        OK,
        /** flow_token desconhecido ou expirado → HTTP 427. */
        TOKEN_INVALIDO,
        /** Requisição fora do protocolo (versão/ação inesperada) → HTTP 400. */
        REQUISICAO_INVALIDA
    }

    public static FlowExchangeOutcome ok(FlowResponse resposta) {
        return new FlowExchangeOutcome(Status.OK, resposta);
    }

    public static FlowExchangeOutcome tokenInvalido() {
        return new FlowExchangeOutcome(Status.TOKEN_INVALIDO, null);
    }

    public static FlowExchangeOutcome requisicaoInvalida() {
        return new FlowExchangeOutcome(Status.REQUISICAO_INVALIDA, null);
    }
}
