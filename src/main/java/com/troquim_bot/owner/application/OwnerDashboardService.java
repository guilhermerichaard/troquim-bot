package com.troquim_bot.owner.application;

import com.troquim_bot.application.agenda.AgendaDoNegocioService;
import com.troquim_bot.application.customer.CustomerApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.whatsapp.channel.application.ChannelConnectionStatus;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Read-model da área privada do dono: agrega os MESMOS Application Services que já
 * existem (agenda, cliente, canal) — não decide regra nenhuma, só monta o que a tela
 * de /app precisa. Se algum dia esta classe validar disponibilidade, cifrar credencial
 * ou decidir conflito, ela deixou de ser um agregador e virou uma segunda fonte de
 * verdade — o que não pode acontecer.
 *
 * A agenda vem de {@link AgendaDoNegocioService}, a MESMA fronteira que o Flow usa
 * para confirmar agendamento — não existe uma segunda agenda para o /app.
 */
@Service
public class OwnerDashboardService {

    private final AgendaDoNegocioService agendaDoNegocioService;
    private final CustomerApplicationService customerApplicationService;
    private final ConectarWhatsAppChannelService channelService;

    public OwnerDashboardService(AgendaDoNegocioService agendaDoNegocioService,
                                 CustomerApplicationService customerApplicationService,
                                 ConectarWhatsAppChannelService channelService) {
        this.agendaDoNegocioService = agendaDoNegocioService;
        this.customerApplicationService = customerApplicationService;
        this.channelService = channelService;
    }

    @Transactional(readOnly = true)
    public OwnerDashboard montar(AuthenticatedOwner identidade) {
        var businessId = identidade.businessId();
        String nomeNegocio = agendaDoNegocioService.negocio(businessId)
                .map(com.troquim_bot.business.Business::getNome)
                .orElse("Meu negócio");

        List<AgendaItem> agenda = agendaDoNegocioService.proximosAgendamentos(businessId).stream()
                .map(this::paraItem)
                .toList();

        ChannelConnectionStatus statusCanal = channelService.consultar(businessId.getValue())
                .map(c -> c.status())
                .orElse(null);

        return new OwnerDashboard(nomeNegocio, agenda, Optional.ofNullable(statusCanal));
    }

    private AgendaItem paraItem(Appointment a) {
        String nomeCliente = customerApplicationService.buscarPorId(a.getCustomerId())
                .map(c -> c.getName().getFullName())
                .orElse("Cliente");
        return new AgendaItem(a.getDate(), a.getStartTime(), a.getEndTime(),
                a.getStatus().name(), nomeCliente);
    }

    public record AgendaItem(LocalDate data, LocalTime inicio, LocalTime fim,
                             String status, String nomeCliente) {}

    /** {@code statusCanal} vazio = nunca conectou ("não conectado"). */
    public record OwnerDashboard(String nomeNegocio, List<AgendaItem> agenda,
                                 Optional<ChannelConnectionStatus> statusCanal) {}
}
