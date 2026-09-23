package com.troquim_bot.owner.application;

import com.troquim_bot.application.agenda.AgendaDoNegocioService;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.customer.CustomerApplicationService;
import com.troquim_bot.application.professional.ProfessionalApplicationService;
import com.troquim_bot.application.service.ServiceApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.professional.Professional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Read model do console do owner.
 *
 * Não contém regra de negócio nem usa tenant implícito: o businessId vem da sessão
 * autenticada do owner e é repassado explicitamente para todas as leituras.
 */
@Service
public class OwnerConsoleQueryService {

    private final AgendaDoNegocioService agenda;
    private final AppointmentApplicationService appointments;
    private final CustomerApplicationService customers;
    private final ServiceApplicationService services;
    private final ProfessionalApplicationService professionals;
    private final OwnerDashboardService dashboard;

    public OwnerConsoleQueryService(AgendaDoNegocioService agenda,
                                    AppointmentApplicationService appointments,
                                    CustomerApplicationService customers,
                                    ServiceApplicationService services,
                                    ProfessionalApplicationService professionals,
                                    OwnerDashboardService dashboard) {
        this.agenda = agenda;
        this.appointments = appointments;
        this.customers = customers;
        this.services = services;
        this.professionals = professionals;
        this.dashboard = dashboard;
    }

    @Transactional(readOnly = true)
    public Overview overview(AuthenticatedOwner owner) {
        BusinessId businessId = owner.businessId();
        var base = dashboard.montar(owner);
        int clientes = customers.listarAtivos(businessId).size();
        int servicos = services.listarAtivos(businessId).size();
        int equipe = professionals.listarAtivos(businessId).size();
        int proximos = appointments.listarAtivos(businessId).size();

        return new Overview(
                base.nomeNegocio(),
                base.statusCanal().map(Enum::name).orElse("NAO_CONECTADO"),
                proximos,
                clientes,
                servicos,
                equipe);
    }

    @Transactional(readOnly = true)
    public List<AppointmentItem> appointments(AuthenticatedOwner owner, LocalDate date) {
        BusinessId businessId = owner.businessId();
        List<Appointment> source = date == null
                ? agenda.proximosAgendamentos(businessId, LocalDate.now(), 100)
                : agenda.agendaDoDia(businessId, date);

        return source.stream().map(a -> new AppointmentItem(
                a.getId().getValue().toString(),
                a.getDate(),
                a.getStartTime(),
                a.getEndTime(),
                a.getStatus().name(),
                customers.buscarPorId(a.getCustomerId())
                        .filter(c -> c.getBusinessId().equals(businessId))
                        .map(c -> c.getName().getFullName())
                        .orElse("Cliente"),
                services.buscarPorId(businessId, a.getServiceId())
                        .map(com.troquim_bot.service.Service::getNome)
                        .orElse("Serviço"),
                professionals.buscarPorId(businessId, a.getProfessionalId())
                        .map(Professional::getNome)
                        .orElse("Profissional")
        )).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerItem> customers(AuthenticatedOwner owner) {
        return customers.listarTodos(owner.businessId()).stream()
                .map(this::customerItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceItem> services(AuthenticatedOwner owner) {
        return services.listarTodos(owner.businessId()).stream()
                .map(s -> new ServiceItem(
                        s.getId().getValue().toString(),
                        s.getNome(),
                        s.getDescricao(),
                        s.getDuracao().getMinutes(),
                        s.getPreco().map(p -> p.getAmount().doubleValue()).orElse(null),
                        s.getStatus().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProfessionalItem> professionals(AuthenticatedOwner owner) {
        return professionals.buscarTodos(owner.businessId()).stream()
                .map(p -> new ProfessionalItem(
                        p.getId().getValue().toString(),
                        p.getNome(),
                        p.getTelefone(),
                        p.getEspecialidades().stream().sorted().toList(),
                        p.getServicosHabilitados().stream()
                                .map(id -> id.getValue().toString())
                                .toList(),
                        p.getStatus().name()))
                .toList();
    }

    private CustomerItem customerItem(Customer c) {
        return new CustomerItem(
                c.getId().getValue().toString(),
                c.getName().getFullName(),
                c.getPhone().getValue(),
                c.getNotes(),
                c.getStatus().name());
    }

    public record Overview(
            String businessName,
            String whatsappStatus,
            int activeAppointments,
            int activeCustomers,
            int activeServices,
            int activeProfessionals) {}

    public record AppointmentItem(
            String id,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String status,
            String customerName,
            String serviceName,
            String professionalName) {}

    public record CustomerItem(
            String id,
            String name,
            String phone,
            String notes,
            String status) {}

    public record ServiceItem(
            String id,
            String name,
            String description,
            int durationMinutes,
            Double price,
            String status) {}

    public record ProfessionalItem(
            String id,
            String name,
            String phone,
            List<String> specialties,
            List<String> enabledServiceIds,
            String status) {}
}
