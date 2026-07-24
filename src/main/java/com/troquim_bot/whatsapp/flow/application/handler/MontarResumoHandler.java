package com.troquim_bot.whatsapp.flow.application.handler;

import com.troquim_bot.whatsapp.flow.application.FlowAction;
import com.troquim_bot.whatsapp.flow.application.FlowContexto;
import com.troquim_bot.whatsapp.flow.application.FlowContextoResolvido;
import com.troquim_bot.whatsapp.flow.application.FlowContextoResolver;
import com.troquim_bot.whatsapp.flow.application.FlowDataParser;
import com.troquim_bot.whatsapp.flow.application.FlowRequest;
import com.troquim_bot.whatsapp.flow.application.FlowResponse;
import com.troquim_bot.whatsapp.flow.application.FlowScreenPresenter;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

/**
 * MONTAR_RESUMO — footer da tela CLIENTE.
 *
 * Última parada antes de escrever: monta o resumo com nomes, duração (e preço quando o
 * domínio o tiver) resolvidos pelo SERVIDOR — o texto enviado pelo cliente nunca é fonte
 * de verdade de preço ou duração. Nada é persistido aqui.
 */
@Component
@ConditionalOnWhatsAppFlow
public class MontarResumoHandler implements FlowActionHandler {

    private final FlowContextoResolver resolver;
    private final FlowDataParser parser;
    private final FlowScreenPresenter presenter;

    public MontarResumoHandler(FlowContextoResolver resolver, FlowDataParser parser,
                               FlowScreenPresenter presenter) {
        this.resolver = resolver;
        this.parser = parser;
        this.presenter = presenter;
    }

    @Override
    public FlowAction acao() {
        return FlowAction.MONTAR_RESUMO;
    }

    @Override
    public FlowResponse tratar(FlowRequest request, FlowSession session) {
        FlowContextoResolvido resolvido = resolver.ateHorario(request);
        if (!resolvido.valido()) {
            return resolvido.falha();
        }

        FlowContexto ctx = resolvido.contexto();
        String nome = parser.nome(request).orElse(null);
        if (nome == null) {
            return presenter.cliente(ctx, "", "Informe seu nome para continuar.");
        }

        return presenter.confirmacao(
                ctx.comDadosPessoais(nome, parser.observacoes(request).orElse(null)), null);
    }
}
