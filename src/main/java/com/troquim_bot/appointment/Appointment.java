package com.troquim_bot.appointment;

import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.reservation.ReservationId;
import com.troquim_bot.service.ServiceId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Aggregate Root que representa um agendamento confirmado.
 *
 * Responsabilidades:
 * - Gerenciar informações do agendamento (cliente, profissional, serviço, horário)
 * - Controlar o ciclo de vida (PENDENTE, CONFIRMADO, CANCELADO, CONCLUIDO)
 * - Proteger invariants de negócio (não criar no passado, não permitir conflitos)
 *
 * O {@link BusinessId} é obrigatório e imutável: um agendamento pertence a um negócio,
 * e é esse vínculo que impede a agenda de um tenant aparecer para outro. Como o
 * catálogo do Flow usa um professional_id sintético compartilhado, sem ele a checagem
 * de disponibilidade cruzaria agendamentos de negócios diferentes.
 */
public class Appointment {

    private final AppointmentId id;
    private final BusinessId businessId;
    private final CustomerId customerId;
    private final ProfessionalId professionalId;
    private final ServiceId serviceId;
    private final AvailabilityId availabilityId;
    private final ReservationId reservationId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private AppointmentStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Appointment (sem reservation).
     * Inicia com status PENDENTE.
     */
    public Appointment(AppointmentId id, BusinessId businessId, CustomerId customerId,
                       ProfessionalId professionalId,
                       ServiceId serviceId, AvailabilityId availabilityId,
                       LocalDate date, LocalTime startTime, LocalTime endTime) {
        this(id, businessId, customerId, professionalId, serviceId, availabilityId, null,
             date, startTime, endTime, AppointmentStatus.PENDENTE,
             LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * Construtor para criação de novo Appointment a partir de Reservation.
     * Inicia com status PENDENTE.
     */
    public Appointment(AppointmentId id, BusinessId businessId, CustomerId customerId,
                       ProfessionalId professionalId,
                       ServiceId serviceId, AvailabilityId availabilityId, ReservationId reservationId,
                       LocalDate date, LocalTime startTime, LocalTime endTime) {
        this(id, businessId, customerId, professionalId, serviceId, availabilityId, reservationId,
             date, startTime, endTime, AppointmentStatus.PENDENTE,
             LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * Construtor para reconstituição de Appointment existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Appointment(AppointmentId id, BusinessId businessId, CustomerId customerId,
                       ProfessionalId professionalId,
                       ServiceId serviceId, AvailabilityId availabilityId, ReservationId reservationId,
                       LocalDate date, LocalTime startTime, LocalTime endTime,
                       AppointmentStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("AppointmentId é obrigatório");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("CustomerId é obrigatório");
        }
        if (professionalId == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório");
        }
        if (serviceId == null) {
            throw new IllegalArgumentException("ServiceId é obrigatório");
        }
        if (availabilityId == null) {
            throw new IllegalArgumentException("AvailabilityId é obrigatório");
        }
        if (date == null) {
            throw new IllegalArgumentException("Data é obrigatória");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Horário de início é obrigatório");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("Horário de fim é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }

        this.id = id;
        this.businessId = businessId;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.availabilityId = availabilityId;
        this.reservationId = reservationId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public BusinessId getBusinessId() {
        return businessId;
    }

    /** O agendamento pertence a este negócio? */
    public boolean pertenceAoTenant(BusinessId businessId) {
        return businessId != null && businessId.equals(this.businessId);
    }

    public AppointmentId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public ProfessionalId getProfessionalId() {
        return professionalId;
    }

    public ServiceId getServiceId() {
        return serviceId;
    }

    public AvailabilityId getAvailabilityId() {
        return availabilityId;
    }

    public ReservationId getReservationId() {
        return reservationId;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    // ==================== MÉTODOS DE NEGÓCIO ====================

    /**
     * Verifica se o Appointment está ativo (não cancelado).
     */
    public boolean isAtivo() {
        return status != AppointmentStatus.CANCELADO;
    }

    /**
     * Verifica se o Appointment pode ser confirmado.
     */
    public boolean podeConfirmar() {
        return status == AppointmentStatus.PENDENTE;
    }

    /**
     * Verifica se o Appointment pode ser cancelado.
     */
    public boolean podeCancelar() {
        return status == AppointmentStatus.PENDENTE || status == AppointmentStatus.CONFIRMADO;
    }

    /**
     * Verifica se o Appointment pode ser concluído.
     */
    public boolean podeConcluir() {
        return status == AppointmentStatus.CONFIRMADO;
    }

    /**
     * Verifica se este agendamento conflita com outro.
     */
    public boolean conflitaCom(Appointment other) {
        if (!this.professionalId.equals(other.professionalId)) {
            return false;
        }
        if (!this.date.equals(other.date)) {
            return false;
        }
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }

    /**
     * Confirma o Appointment (PENDENTE -> CONFIRMADO).
     */
    public void confirmar() {
        if (!podeConfirmar()) {
            throw new IllegalStateException("Apenas agendamentos pendentes podem ser confirmados");
        }
        this.status = AppointmentStatus.CONFIRMADO;
        tocar();
    }

    /**
     * Cancela o Appointment (PENDENTE/CONFIRMADO -> CANCELADO).
     */
    public void cancelar() {
        if (!podeCancelar()) {
            throw new IllegalStateException("Apenas agendamentos pendentes ou confirmados podem ser cancelados");
        }
        this.status = AppointmentStatus.CANCELADO;
        tocar();
    }

    /**
     * Conclui o Appointment (CONFIRMADO -> CONCLUIDO).
     */
    public void concluir() {
        if (!podeConcluir()) {
            throw new IllegalStateException("Apenas agendamentos confirmados podem ser concluídos");
        }
        this.status = AppointmentStatus.CONCLUIDO;
        tocar();
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}