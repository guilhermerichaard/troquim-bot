package com.troquim_bot.reservation;

import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Aggregate Root que representa uma reserva (bloqueio temporário) de horário.
 * 
 * Responsabilidades:
 * - Gerenciar informações da reserva (cliente, profissional, serviço, horário)
 * - Controlar o ciclo de vida da reserva (ATIVO, CANCELADO)
 * - Proteger invariants de negócio (não permitir duas reservas no mesmo horário)
 * - Representar um bloqueio temporário com expiração
 */
public class Reservation {

    private final ReservationId id;
    private final CustomerId customerId;
    private final ProfessionalId professionalId;
    private final ServiceId serviceId;
    private final AvailabilityId availabilityId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDateTime expiresAt;
    private ReservationStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Reservation.
     * Inicia com status ATIVO.
     */
    public Reservation(ReservationId id, CustomerId customerId, ProfessionalId professionalId,
                       ServiceId serviceId, AvailabilityId availabilityId,
                       LocalDate date, LocalTime startTime, LocalTime endTime,
                       LocalDateTime expiresAt) {
        if (id == null) {
            throw new IllegalArgumentException("ReservationId é obrigatório");
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
        if (expiresAt == null) {
            throw new IllegalArgumentException("Data de expiração é obrigatória");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }

        this.id = id;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.availabilityId = availabilityId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.ATIVO;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Construtor para reconstituição de Reservation existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Reservation(ReservationId id, CustomerId customerId, ProfessionalId professionalId,
                       ServiceId serviceId, AvailabilityId availabilityId,
                       LocalDate date, LocalTime startTime, LocalTime endTime,
                       LocalDateTime expiresAt, ReservationStatus status,
                       LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("ReservationId é obrigatório");
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
        if (expiresAt == null) {
            throw new IllegalArgumentException("Data de expiração é obrigatória");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.customerId = customerId;
        this.professionalId = professionalId;
        this.serviceId = serviceId;
        this.availabilityId = availabilityId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.expiresAt = expiresAt;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public ReservationId getId() {
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

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public ReservationStatus getStatus() {
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
     * Verifica se a Reservation está ativa.
     */
    public boolean isAtivo() {
        return status == ReservationStatus.ATIVO;
    }

    /**
     * Verifica se a reserva está expirada.
     */
    public boolean isExpirada() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Verifica se esta reserva conflita com outra.
     * Duas reservas conflitam se são do mesmo profissional, mesma data e horários se sobrepõem.
     */
    public boolean conflitaCom(Reservation other) {
        if (!this.professionalId.equals(other.professionalId)) {
            return false;
        }
        if (!this.date.equals(other.date)) {
            return false;
        }
        // Verifica sobreposição: startA < endB && startB < endA
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }

    /**
     * Atualiza a data da reserva.
     */
    public void atualizarData(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Data não pode ser nula");
        }
        this.date = date;
        tocar();
    }

    /**
     * Atualiza o horário de início.
     */
    public void atualizarStartTime(LocalTime startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Horário de início não pode ser nulo");
        }
        if (!startTime.isBefore(this.endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }
        this.startTime = startTime;
        tocar();
    }

    /**
     * Atualiza o horário de fim.
     */
    public void atualizarEndTime(LocalTime endTime) {
        if (endTime == null) {
            throw new IllegalArgumentException("Horário de fim não pode ser nulo");
        }
        if (!this.startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }
        this.endTime = endTime;
        tocar();
    }

    /**
     * Atualiza a data de expiração.
     */
    public void atualizarExpiresAt(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("Data de expiração não pode ser nula");
        }
        this.expiresAt = expiresAt;
        tocar();
    }

    /**
     * Cancela a Reservation (transição para CANCELADO).
     */
    public void cancelar() {
        if (status == ReservationStatus.ATIVO) {
            this.status = ReservationStatus.CANCELADO;
            tocar();
        }
    }

    /**
     * Reativa a Reservation (transição para ATIVO).
     */
    public void reativar() {
        if (status == ReservationStatus.CANCELADO) {
            this.status = ReservationStatus.ATIVO;
            tocar();
        }
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}