package com.troquim_bot.owner.application;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.application.catalog.ConfirmarAgendamentoDoCatalogo;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.customer.CustomerApplicationService;
import com.troquim_bot.application.professional.ProfessionalApplicationService;
import com.troquim_bot.application.service.ServiceApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.Money;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OwnerConsoleCommandService {

    private final AppointmentApplicationService appointments;
    private final AvailabilityApplicationService availability;
    private final ConfirmarAgendamentoDoCatalogo confirmar;
    private final ConsultarCatalogo catalogo;
    private final CustomerApplicationService customers;
    private final ServiceApplicationService services;
    private final ProfessionalApplicationService professionals;

    public OwnerConsoleCommandService(AppointmentApplicationService appointments,
                                      AvailabilityApplicationService availability,
                                      ConfirmarAgendamentoDoCatalogo confirmar,
                                      ConsultarCatalogo catalogo,
                                      CustomerApplicationService customers,
                                      ServiceApplicationService services,
                                      ProfessionalApplicationService professionals) {
        this.appointments = appointments;
        this.availability = availability;
        this.confirmar = confirmar;
        this.catalogo = catalogo;
        this.customers = customers;
        this.services = services;
        this.professionals = professionals;
    }

    @Transactional(readOnly = true)
    public List<CatalogItem> catalog(AuthenticatedOwner owner) {
        return catalogo.consultar(owner.businessId()).itens().stream()
                .map(item -> new CatalogItem(
                        item.id().getValue().toString(),
                        item.nome(),
                        item.descricao(),
                        item.duracao().toMinutes(),
                        item.preco().map(p -> p.getAmount().doubleValue()).orElse(null),
                        item.profissionais().stream()
                                .map(p -> new ProfessionalOption(p.id().getValue().toString(), p.nome()))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> slots(AuthenticatedOwner owner, String serviceId,
                              String professionalId, LocalDate date) {
        BusinessId businessId = owner.businessId();
        ServiceId servico = ServiceId.from(UUID.fromString(serviceId));
        ProfessionalId profissional = ProfessionalId.from(UUID.fromString(professionalId));
        garantirServicoEProfissional(businessId, servico, profissional);
        return availability.horariosLivres(businessId, servico, profissional, date).stream()
                .map(LocalTime::toString)
                .toList();
    }

    @Transactional
    public ActionResult createAppointment(AuthenticatedOwner owner, CreateAppointment command) {
        BusinessId businessId = owner.businessId();
        Customer customer = exigirCustomerDoTenant(businessId, command.customerId());
        ServiceId serviceId = ServiceId.from(UUID.fromString(command.serviceId()));
        ProfessionalId professionalId = ProfessionalId.from(UUID.fromString(command.professionalId()));
        garantirServicoEProfissional(businessId, serviceId, professionalId);

        BookingCommandKey key = BookingCommandKey.deChaveExclusiva(
                businessId,
                command.idempotencyKey(),
                customer.getPhone().getValue(),
                serviceId,
                professionalId,
                command.date(),
                command.time());

        var result = confirmar.confirmar(new ConfirmarAgendamentoDoCatalogo.Pedido(
                businessId,
                serviceId,
                professionalId,
                customer.getPhone().getValue(),
                customer.getName().getFullName(),
                command.date(),
                command.time(),
                key));

        if (result.foiRecusado()) {
            return ActionResult.error("Catálogo ou profissional indisponível.");
        }

        BookingResult booking = result.agendamento().orElse(BookingResult.falhaTecnica());
        return booking.isConfirmado()
                ? ActionResult.ok("Agendamento criado.")
                : ActionResult.error(booking.mensagem() == null ? "Não foi possível criar o agendamento." : booking.mensagem());
    }

    @Transactional
    public ActionResult cancelAppointment(AuthenticatedOwner owner, String appointmentId) {
        Appointment appointment = exigirAppointmentDoTenant(owner.businessId(), appointmentId);
        appointments.cancelarAgendamento(appointment.getId());
        return ActionResult.ok("Agendamento cancelado.");
    }

    /**
     * Reagendamento atômico: cancela o antigo e cria o novo na MESMA transação.
     * Se a nova confirmação não concluir, lança para provocar rollback da cancelamento.
     */
    @Transactional
    public ActionResult reschedule(AuthenticatedOwner owner, String appointmentId, Reschedule command) {
        Appointment antigo = exigirAppointmentDoTenant(owner.businessId(), appointmentId);
        Customer customer = exigirCustomerDoTenant(owner.businessId(),
                antigo.getCustomerId().getValue().toString());

        services.buscarPorId(owner.businessId(), antigo.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Serviço do agendamento não está mais disponível"));
        professionals.buscarPorId(owner.businessId(), antigo.getProfessionalId())
                .orElseThrow(() -> new IllegalArgumentException("Profissional do agendamento não está mais disponível"));

        appointments.cancelarAgendamento(antigo.getId());

        BookingCommandKey key = BookingCommandKey.deChaveExclusiva(
                owner.businessId(),
                command.idempotencyKey(),
                customer.getPhone().getValue(),
                antigo.getServiceId(),
                antigo.getProfessionalId(),
                command.date(),
                command.time());

        var result = confirmar.confirmar(new ConfirmarAgendamentoDoCatalogo.Pedido(
                owner.businessId(),
                antigo.getServiceId(),
                antigo.getProfessionalId(),
                customer.getPhone().getValue(),
                customer.getName().getFullName(),
                command.date(),
                command.time(),
                key));

        BookingResult booking = result.agendamento().orElse(null);
        if (result.foiRecusado() || booking == null || !booking.isConfirmado()) {
            throw new RescheduleRejectedException(
                    booking != null && booking.mensagem() != null
                            ? booking.mensagem()
                            : "Novo horário indisponível. O agendamento original foi preservado.");
        }
        return ActionResult.ok("Agendamento reagendado.");
    }

    @Transactional
    public CustomerItem createCustomer(AuthenticatedOwner owner, CreateCustomer command) {
        Customer customer = customers.criarCliente(owner.businessId(), command.name(), command.phone(), command.notes());
        return new CustomerItem(
                customer.getId().getValue().toString(),
                customer.getName().getFullName(),
                customer.getPhone().getValue());
    }

    @Transactional
    public ActionResult createService(AuthenticatedOwner owner, ServiceCommand command) {
        Money price = command.price() == null ? null : Money.of(command.price(), "BRL");
        services.criarServico(owner.businessId(), command.name(), command.description(),
                command.durationMinutes(), price);
        return ActionResult.ok("Serviço criado.");
    }

    @Transactional
    public ActionResult updateService(AuthenticatedOwner owner, String id, ServiceCommand command) {
        ServiceId serviceId = ServiceId.from(UUID.fromString(id));
        BusinessId businessId = owner.businessId();
        services.atualizarNome(businessId, serviceId, command.name());
        services.atualizarDescricao(businessId, serviceId, command.description());
        services.atualizarDuracao(businessId, serviceId, command.durationMinutes());
        if (command.price() == null) {
            services.removerPreco(businessId, serviceId);
        } else {
            services.atualizarPreco(businessId, serviceId, Money.of(command.price(), "BRL"));
        }
        return ActionResult.ok("Serviço atualizado.");
    }

    @Transactional
    public ActionResult setServiceStatus(AuthenticatedOwner owner, String id, boolean active) {
        ServiceId serviceId = ServiceId.from(UUID.fromString(id));
        if (active) services.ativarServico(owner.businessId(), serviceId);
        else services.inativarServico(owner.businessId(), serviceId);
        return ActionResult.ok(active ? "Serviço ativado." : "Serviço pausado.");
    }

    @Transactional
    public ActionResult createProfessional(AuthenticatedOwner owner, ProfessionalCommand command) {
        Set<ServiceId> enabled = command.enabledServiceIds() == null ? Set.of()
                : command.enabledServiceIds().stream()
                .map(UUID::fromString).map(ServiceId::from).collect(java.util.stream.Collectors.toSet());
        for (ServiceId serviceId : enabled) {
            services.buscarPorId(owner.businessId(), serviceId)
                    .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado"));
        }
        professionals.criarProfissional(owner.businessId(), command.name(), enabled,
                command.specialties() == null ? Set.of() : Set.copyOf(command.specialties()),
                command.phone());
        return ActionResult.ok("Profissional criado.");
    }

    @Transactional
    public ActionResult updateProfessional(AuthenticatedOwner owner, String id, ProfessionalCommand command) {
        BusinessId businessId = owner.businessId();
        ProfessionalId professionalId = ProfessionalId.from(UUID.fromString(id));

        professionals.atualizarProfissional(businessId, professionalId, command.name(),
                command.specialties() == null || command.specialties().isEmpty()
                        ? null : Set.copyOf(command.specialties()),
                command.phone());

        var current = professionals.buscarPorId(businessId, professionalId)
                .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado"));
        Set<ServiceId> desired = command.enabledServiceIds() == null ? Set.of()
                : command.enabledServiceIds().stream()
                .map(UUID::fromString).map(ServiceId::from).collect(java.util.stream.Collectors.toSet());

        for (ServiceId serviceId : desired) {
            services.buscarPorId(businessId, serviceId)
                    .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado"));
            if (!current.getServicosHabilitados().contains(serviceId)) {
                professionals.habilitarPara(businessId, professionalId, serviceId);
            }
        }
        for (ServiceId serviceId : current.getServicosHabilitados()) {
            if (!desired.contains(serviceId)) {
                professionals.desabilitarPara(businessId, professionalId, serviceId);
            }
        }
        return ActionResult.ok("Profissional atualizado.");
    }

    @Transactional
    public ActionResult setProfessionalStatus(AuthenticatedOwner owner, String id, boolean active) {
        ProfessionalId professionalId = ProfessionalId.from(UUID.fromString(id));
        if (active) professionals.ativarProfissional(owner.businessId(), professionalId);
        else professionals.inativarProfissional(owner.businessId(), professionalId);
        return ActionResult.ok(active ? "Profissional ativado." : "Profissional pausado.");
    }

    private void garantirServicoEProfissional(BusinessId businessId, ServiceId serviceId,
                                              ProfessionalId professionalId) {
        var item = catalogo.porServico(businessId, serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Serviço indisponível"));
        boolean enabled = item.profissionais().stream().anyMatch(p -> p.id().equals(professionalId));
        if (!enabled) throw new IllegalArgumentException("Profissional não atende este serviço");
    }

    private Customer exigirCustomerDoTenant(BusinessId businessId, String customerId) {
        Customer customer = customers.buscarPorId(CustomerId.from(UUID.fromString(customerId)))
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
        if (!customer.getBusinessId().equals(businessId)) {
            throw new IllegalArgumentException("Cliente não encontrado");
        }
        return customer;
    }

    private Appointment exigirAppointmentDoTenant(BusinessId businessId, String appointmentId) {
        Appointment appointment = appointments.buscarPorId(AppointmentId.from(UUID.fromString(appointmentId)))
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado"));
        if (!appointment.pertenceAoTenant(businessId)) {
            throw new IllegalArgumentException("Agendamento não encontrado");
        }
        return appointment;
    }

    public record ActionResult(boolean ok, String message) {
        public static ActionResult ok(String message) { return new ActionResult(true, message); }
        public static ActionResult error(String message) { return new ActionResult(false, message); }
    }

    public record CatalogItem(String id, String name, String description, long durationMinutes,
                              Double price, List<ProfessionalOption> professionals) {}
    public record ProfessionalOption(String id, String name) {}
    public record CustomerItem(String id, String name, String phone) {}
    public record CreateCustomer(String name, String phone, String notes) {}
    public record CreateAppointment(String customerId, String serviceId, String professionalId,
                                    LocalDate date, LocalTime time, String idempotencyKey) {}
    public record Reschedule(LocalDate date, LocalTime time, String idempotencyKey) {}
    public record ServiceCommand(String name, String description, int durationMinutes, Double price) {}
    public record ProfessionalCommand(String name, String phone, List<String> specialties,
                                      List<String> enabledServiceIds) {}

    public static class RescheduleRejectedException extends RuntimeException {
        public RescheduleRejectedException(String message) { super(message); }
    }
}
