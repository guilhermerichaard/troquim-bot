package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.business.BusinessId;
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
    private final TenantProvider tenantProvider;

    public FlowContextoResolver(TenantProvider tenantProvider,
                                FlowDataParser parser, FlowScreenPresenter presenter,
                                FlowAvailabilityQuery disponibilidade) {
        this.parser = parser;
        this.presenter = presenter;
        this.disponibilidade = disponibilidade;
        this.tenantProvider = tenantProvider;
    }

    public FlowContextoResolvido ateServico(FlowRequest request, FlowSession session) {
        Optional<FlowServiceOption> servico = parser.servico(request);
        if (servico.isEmpty()) {
            return FlowContextoResolvido.falhou(presenter.servico(false,
                    "Esse serviço não está disponível. Escolha uma das opções."));
        }
        return FlowContextoResolvido.ok(FlowContexto.de(tenantDe(session), servico.get()));
    }

    /** Profissional é OPCIONAL: ausente → padrão do catálogo; inválido → erro. */
    public FlowContextoResolvido ateProfissional(FlowRequest request, FlowSession session) {
        FlowContextoResolvido anterior = ateServico(request, session);
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

    public FlowContextoResolvido ateData(FlowRequest request, FlowSession session) {
        FlowContextoResolvido anterior = ateProfissional(request, session);
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
    public FlowContextoResolvido ateHorario(FlowRequest request, FlowSession session) {
        FlowContextoResolvido resolvido = ateHorarioSemChecarDisponibilidade(request, session);
        if (!resolvido.valido()) {
            return resolvido;
        }

        FlowContexto ctx = resolvido.contexto();
        if (!disponibilidade.estaLivre(ctx.businessId(), ctx.data(), ctx.horario(),
                ctx.profissional().professionalId())) {
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
    public FlowContextoResolvido ateHorarioSemChecarDisponibilidade(FlowRequest request, FlowSession session) {
        FlowContextoResolvido anterior = ateData(request, session);
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

    /**
     * Tenant da troca. Vem SEMPRE da sessão — o payload do cliente nunca escolhe negócio.
     *
     * A sessão de preview do editor da Meta não tem tenant por construção; para ela cai
     * no negócio corrente apenas para EXIBIR a agenda (leitura pública). O preview segue
     * incapaz de agendar: a divergência de escrita continua no handler de confirmação.
     */
    private BusinessId tenantDe(FlowSession session) {
        if (session != null && session.businessId() != null) {
            return BusinessId.from(session.businessId());
        }
        return tenantProvider.currentBusinessId();
    }
}
