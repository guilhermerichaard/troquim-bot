package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.whatsapp.flow.application.availability.FlowAvailabilityQuery;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowProfessionalOption;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowServiceOption;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Revalida, a cada evento, TODA a cadeia de escolhas reenviada pela tela.
 *
 * Como o Flow é stateless no servidor, a tela devolve os campos que enviamos — mas o
 * cliente controla o dispositivo, e a Meta não garante integridade do que vem em
 * {@code data}. O prefixo inteiro (serviço → profissional → data → horário) é
 * reconstruído do zero contra o catálogo e a disponibilidade, nunca copiado.
 *
 * Cada método devolve o contexto validado OU a tela para onde o cliente deve voltar,
 * com a mensagem correspondente ao campo que realmente falhou.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowContextoResolver {

    private final FlowDataParser parser;
    private final FlowScreenPresenter presenter;
    private final FlowAvailabilityQuery disponibilidade;

    public FlowContextoResolver(FlowDataParser parser, FlowScreenPresenter presenter,
                                FlowAvailabilityQuery disponibilidade) {
        this.parser = parser;
        this.presenter = presenter;
        this.disponibilidade = disponibilidade;
    }

    public FlowContextoResolvido ateServico(FlowRequest request) {
        Optional<FlowServiceOption> servico = parser.servico(request);
        if (servico.isEmpty()) {
            return FlowContextoResolvido.falhou(presenter.servico(false,
                    "Esse serviço não está disponível. Escolha uma das opções."));
        }
        return FlowContextoResolvido.ok(FlowContexto.de(servico.get()));
    }

    /** Profissional é OPCIONAL: ausente → padrão do catálogo; inválido → erro. */
    public FlowContextoResolvido ateProfissional(FlowRequest request) {
        FlowContextoResolvido anterior = ateServico(request);
        if (!anterior.valido()) {
            return anterior;
        }

        FlowContexto ctx = anterior.contexto();
        Optional<FlowProfessionalOption> profissional = parser.profissional(request, ctx.servico());
        if (profissional.isEmpty()) {
            return FlowContextoResolvido.falhou(presenter.servico(true,
                    "Esse profissional não atende o serviço escolhido. Escolha outro."));
        }
        return FlowContextoResolvido.ok(ctx.com(profissional.get()));
    }

    public FlowContextoResolvido ateData(FlowRequest request) {
        FlowContextoResolvido anterior = ateProfissional(request);
        if (!anterior.valido()) {
            return anterior;
        }

        FlowContexto ctx = anterior.contexto();
        Optional<LocalDate> data = parser.data(request);
        if (data.isEmpty()) {
            return FlowContextoResolvido.falhou(
                    presenter.agenda(ctx, "Escolha uma data válida."));
        }
        return FlowContextoResolvido.ok(ctx.com(data.get()));
    }

    /**
     * Valida o horário contra a disponibilidade ATUAL. É aqui que uma escolha que ficou
     * obsoleta entre telas é detectada e o cliente volta à lista atualizada.
     */
    public FlowContextoResolvido ateHorario(FlowRequest request) {
        FlowContextoResolvido resolvido = ateHorarioSemChecarDisponibilidade(request);
        if (!resolvido.valido()) {
            return resolvido;
        }

        FlowContexto ctx = resolvido.contexto();
        if (!disponibilidade.estaLivre(ctx.data(), ctx.horario(), ctx.profissional().professionalId())) {
            return FlowContextoResolvido.falhou(presenter.agenda(ctx,
                    "Esse horário acabou de ser ocupado. Escolha outro, por favor."));
        }
        return resolvido;
    }

    /**
     * Valida a cadeia até o horário SEM consultar a disponibilidade.
     *
     * Existe para a CONFIRMAÇÃO. Ali a checagem antecipada é ativamente errada: um retry
     * de comando JÁ confirmado encontraria o slot ocupado — pelo próprio agendamento que
     * ele criou — e receberia "escolha outro horário" em vez do sucesso que já aconteceu.
     * Na confirmação, o comando é identificado primeiro e o conflito real fica com o
     * domínio, dentro da transação que grava.
     */
    public FlowContextoResolvido ateHorarioSemChecarDisponibilidade(FlowRequest request) {
        FlowContextoResolvido anterior = ateData(request);
        if (!anterior.valido()) {
            return anterior;
        }

        FlowContexto ctx = anterior.contexto();
        Optional<LocalTime> horario = parser.horario(request);
        if (horario.isEmpty()) {
            return FlowContextoResolvido.falhou(
                    presenter.agenda(ctx, "Escolha um horário da lista."));
        }
        return FlowContextoResolvido.ok(ctx.com(horario.get()));
    }
}
