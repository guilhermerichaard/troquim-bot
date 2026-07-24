package com.troquim_bot.whatsapp.flow.application.handler;

import com.troquim_bot.whatsapp.flow.application.FlowAction;
import com.troquim_bot.whatsapp.flow.application.FlowContextoResolvido;
import com.troquim_bot.whatsapp.flow.application.FlowContextoResolver;
import com.troquim_bot.whatsapp.flow.application.FlowRequest;
import com.troquim_bot.whatsapp.flow.application.FlowResponse;
import com.troquim_bot.whatsapp.flow.application.FlowScreenPresenter;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

/**
 * BUSCAR_DATAS — footer da tela SERVICO.
 *
 * Revalida serviço + profissional (opcional) e navega para AGENDA com as datas
 * ELEGÍVEIS. As datas vêm da fronteira única de disponibilidade (Application → Domain),
 * nunca calculadas aqui: o handler não sabe o que é um slot.
 */
@Component
@ConditionalOnWhatsAppFlow
public class BuscarDatasHandler implements FlowActionHandler {

    private final FlowContextoResolver resolver;
    private final FlowScreenPresenter presenter;

    public BuscarDatasHandler(FlowContextoResolver resolver, FlowScreenPresenter presenter) {
        this.resolver = resolver;
        this.presenter = presenter;
    }

    @Override
    public FlowAction acao() {
        return FlowAction.BUSCAR_DATAS;
    }

    @Override
    public FlowResponse tratar(FlowRequest request, FlowSession session) {
        FlowContextoResolvido resolvido = resolver.ateProfissional(request);
        return resolvido.valido()
                ? presenter.agenda(resolvido.contexto(), null)
                : resolvido.falha();
    }
}
