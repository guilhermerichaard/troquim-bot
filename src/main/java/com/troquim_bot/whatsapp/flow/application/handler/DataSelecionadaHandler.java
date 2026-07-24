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
 * DATA_SELECIONADA — on-select do dropdown de data na tela AGENDA.
 *
 * Re-renderiza a MESMA tela AGENDA com os horários REALMENTE livres do dia escolhido
 * (via AvailabilityApplicationService) e o dropdown de horários habilitado. Nenhum
 * agendamento é criado aqui.
 */
@Component
@ConditionalOnWhatsAppFlow
public class DataSelecionadaHandler implements FlowActionHandler {

    private final FlowContextoResolver resolver;
    private final FlowScreenPresenter presenter;

    public DataSelecionadaHandler(FlowContextoResolver resolver, FlowScreenPresenter presenter) {
        this.resolver = resolver;
        this.presenter = presenter;
    }

    @Override
    public FlowAction acao() {
        return FlowAction.DATA_SELECIONADA;
    }

    @Override
    public FlowResponse tratar(FlowRequest request, FlowSession session) {
        FlowContextoResolvido resolvido = resolver.ateData(request);
        return resolvido.valido()
                ? presenter.agenda(resolvido.contexto(), null)
                : resolvido.falha();
    }
}
