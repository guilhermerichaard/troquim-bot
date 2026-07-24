package com.troquim_bot.whatsapp.flow.application.handler;

import com.troquim_bot.customer.CustomerProfileService;
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
 * HORARIO_SELECIONADO — footer da tela AGENDA.
 *
 * Revalida a cadeia inteira INCLUINDO disponibilidade atual (uma escolha que ficou
 * obsoleta entre telas é detectada aqui) e navega para CLIENTE. O nome vem
 * pré-preenchido a partir do cliente já conhecido — resolvido pelo TELEFONE DA SESSÃO,
 * nunca por telefone vindo do payload. Ids canônicos são preservados no echo da tela.
 */
@Component
@ConditionalOnWhatsAppFlow
public class HorarioSelecionadoHandler implements FlowActionHandler {

    private final FlowContextoResolver resolver;
    private final FlowScreenPresenter presenter;
    private final CustomerProfileService customerProfileService;

    public HorarioSelecionadoHandler(FlowContextoResolver resolver, FlowScreenPresenter presenter,
                                     CustomerProfileService customerProfileService) {
        this.resolver = resolver;
        this.presenter = presenter;
        this.customerProfileService = customerProfileService;
    }

    @Override
    public FlowAction acao() {
        return FlowAction.HORARIO_SELECIONADO;
    }

    @Override
    public FlowResponse tratar(FlowRequest request, FlowSession session) {
        FlowContextoResolvido resolvido = resolver.ateHorario(request);
        if (!resolvido.valido()) {
            return resolvido.falha();
        }

        String nomeConhecido = customerProfileService.nomePreferido(session.telefone()).orElse("");
        return presenter.cliente(resolvido.contexto(), nomeConhecido, null);
    }
}
