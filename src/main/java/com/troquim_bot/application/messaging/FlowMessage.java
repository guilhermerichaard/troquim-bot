package com.troquim_bot.application.messaging;

/**
 * Mensagem interativa que abre um Flow, descrita de forma NEUTRA de provedor.
 *
 * A Application diz "abra este Flow, com este token, com este rótulo de botão"; traduzir
 * isso para o JSON da Graph API é trabalho do adaptador. Se este record passar a conter
 * campo com nome da Meta, a fronteira vazou.
 *
 * Não há tela inicial aqui de propósito: este Flow é conduzido pelo Data Endpoint, então
 * a primeira tela vem da resposta ao {@code INIT}, e não de um payload embutido na
 * mensagem. Fixar a tela na mensagem duplicaria essa decisão em dois lugares.
 *
 * @param flowId       identificador do Flow publicado (ou {@code null} se usar nome)
 * @param flowName     nome do Flow, alternativa ao id
 * @param flowToken    token opaco da sessão — a amarração com o cliente
 * @param cta          rótulo do botão (a Meta limita a 20 caracteres)
 * @param corpo        texto da mensagem
 * @param modoRascunho {@code true} abre o Flow em modo draft (só para teste interno)
 */
public record FlowMessage(String flowId,
                          String flowName,
                          String flowToken,
                          String cta,
                          String corpo,
                          boolean modoRascunho) {

    /** Limite da Meta para o rótulo do botão. */
    public static final int CTA_MAX = 20;

    public FlowMessage {
        if (flowToken == null || flowToken.isBlank()) {
            throw new IllegalArgumentException("flowToken é obrigatório");
        }
        if ((flowId == null || flowId.isBlank()) && (flowName == null || flowName.isBlank())) {
            throw new IllegalArgumentException("É preciso flowId ou flowName");
        }
        if (cta == null || cta.isBlank() || cta.length() > CTA_MAX) {
            throw new IllegalArgumentException(
                    "cta é obrigatório e limitado a " + CTA_MAX + " caracteres");
        }
        if (corpo == null || corpo.isBlank()) {
            throw new IllegalArgumentException("corpo é obrigatório");
        }
    }
}
