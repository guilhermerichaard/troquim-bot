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
 * SERVICO_SELECIONADO — on-select do dropdown de serviço.
 *
 * Re-renderiza a MESMA tela SERVICO com o dropdown de profissional habilitado (padrão
 * de habilitação progressiva do exemplo oficial da Meta). Valida o serviço; NÃO decide
 * disponibilidade — isso só acontece em BUSCAR_DATAS/DATA_SELECIONADA, via Application.
 */
@Component
@ConditionalOnWhatsAppFlow
public class ServicoSelecionadoHandler implements FlowActionHandler {

    private final FlowContextoResolver resolver;
    private final FlowScreenPresenter presenter;

    public ServicoSelecionadoHandler(FlowContextoResolver resolver, FlowScreenPresenter presenter) {
        this.resolver = resolver;
        this.presenter = presenter;
    }

    @Override
    public FlowAction acao() {
        return FlowAction.SERVICO_SELECIONADO;
    }

    @Override
    public FlowResponse tratar(FlowRequest request, FlowSession session) {
        FlowContextoResolvido resolvido = resolver.ateServico(request);
        return resolvido.valido()
                ? presenter.servico(true, null)
                : resolvido.falha();
    }
}
