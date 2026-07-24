package com.troquim_bot.whatsapp.flow.application.handler;

import com.troquim_bot.whatsapp.flow.application.FlowAction;
import com.troquim_bot.whatsapp.flow.application.FlowRequest;
import com.troquim_bot.whatsapp.flow.application.FlowResponse;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;

/**
 * Trata UM evento canônico do agendamento.
 *
 * Um handler por evento, descoberto pelo Spring e indexado pelo registry — não existe
 * um switch central que precise crescer a cada passo novo. Adicionar um evento é
 * adicionar uma classe.
 *
 * Cada handler valida a entrada que consome: nada do que chega em
 * {@link FlowRequest#data()} é confiável, mesmo tendo sido enviado por nós na resposta
 * anterior. Handlers NUNCA decidem disponibilidade, conflito, preço ou idempotência —
 * isso é do domínio, alcançado via Application.
 */
public interface FlowActionHandler {

    /** Evento que este handler trata. */
    FlowAction acao();

    /**
     * @param request dados da tela (não confiáveis)
     * @param session sessão resolvida pelo flow_token — fonte confiável do cliente
     */
    FlowResponse tratar(FlowRequest request, FlowSession session);
}
