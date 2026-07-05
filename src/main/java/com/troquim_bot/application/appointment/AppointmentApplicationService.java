package com.troquim_bot.application.appointment;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;
import com.troquim_bot.repository.ReservationRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.service.ServiceId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Application Service para gerenciar Appointments.
 * 
 * Responsabilidades:
 * - Criar agendamentos
 * - Criar agendamentos a partir de reservas
 * - Listar agendamentos
 * - Buscar agendamentos
 * - Confirmar, cancelar, concluir agendamentos
 * - Prevenir conflitos de horário
 */
@org.springframework.stereotype.Service
public class AppointmentApplicationService {

    private final AppointmentRepository appointmentRepository;
    private final ReservationRepository reservationRepository;

    /**
     * Construtor para MVP com repositórios em memória.
     */
    public AppointmentApplicationService() {
        this(new InMemoryAppointmentRepository(), new InMemoryReservationRepository());
    }

    /**
     * Construtor com injeção de dependência.
     */
    @Autowired
    public AppointmentApplicationService(AppointmentRepository appointmentRepository,
                                          ReservationRepository reservationRepository) {
        this.appointmentRepository = appointmentRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Cria um novo agendamento.
     */
    public Appointment criarAgendamento(CustomerId customerId, ProfessionalId professionalId,
                                         ServiceId serviceId, AvailabilityId availabilityId,
                                         LocalDate date, LocalTime startTime, LocalTime endTime) {
        validateCriacao(customerId, professionalId, serviceId, availabilityId, date, startTime, endTime);

        // Verifica se a data não está no passado
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Não é possível criar agendamento no passado");
        }

        AppointmentId id = AppointmentId.generate();
        Appointment newAppointment = new Appointment(id, customerId, professionalId, serviceId,
            availabilityId, date, startTime, endTime);

        checkConflito(professionalId, date, startTime, endTime);

        return appointmentRepository.save(newAppointment);
    }

    /**
     * Cria um agendamento a partir de uma reserva existente.
     */
    public Appointment criarAgendamentoDeReserva(ReservationId reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("ReservationId é obrigatório");
        }

        Reservation reservation = reservationRepository.findById(reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("Reserva não encontrada");
        }
        if (!reservation.isAtivo()) {
            throw new IllegalArgumentException("Reserva não está ativa");
        }

        AppointmentId id = AppointmentId.generate();
        Appointment appointment = new Appointment(
            id,
            reservation.getCustomerId(),
            reservation.getProfessionalId(),
            reservation.getServiceId(),
            reservation.getAvailabilityId(),
            reservation.getId(),
            reservation.getDate(),
            reservation.getStartTime(),
            reservation.getEndTime()
        );

        // Cancela a reserva original
        reservation.cancelar();
        reservationRepository.save(reservation);

        return appointmentRepository.save(appointment);
    }

    /**
     * Busca agendamento por ID.
     */
    public Optional<Appointment> buscarPorId(AppointmentId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(appointmentRepository.findById(id));
    }

    /**
     * Lista todos os agendamentos.
     */
    public List<Appointment> listarTodos() {
        return appointmentRepository.findAll();
    }

    /**
     * Lista apenas agendamentos ativos (não cancelados).
     */
    public List<Appointment> listarAtivos() {
        return appointmentRepository.findAll().stream()
            .filter(Appointment::isAtivo)
            .toList();
    }

    /**
     * Lista agendamentos ativos de um cliente.
     */
    public List<Appointment> listarAtivosPorCliente(CustomerId customerId) {
        if (customerId == null) {
            return List.of();
        }

        return appointmentRepository.findByCustomerId(customerId).stream()
            .filter(Appointment::isAtivo)
            .sorted(Comparator.comparing(Appointment::getDate)
                    .thenComparing(Appointment::getStartTime))
            .toList();
    }

    /**
     * Busca o proximo agendamento ativo de um cliente.
     */
    public Optional<Appointment> buscarAtivoPorCliente(CustomerId customerId) {
        return listarAtivosPorCliente(customerId).stream().findFirst();
    }

    /**
     * Confirma um agendamento (PENDENTE -> CONFIRMADO).
     */
    public Appointment confirmarAgendamento(AppointmentId id) {
        Appointment appointment = getAppointmentOrThrow(id);
        appointment.confirmar();
        return appointmentRepository.save(appointment);
    }

    /**
     * Cancela um agendamento (PENDENTE/CONFIRMADO -> CANCELADO).
     */
    public Appointment cancelarAgendamento(AppointmentId id) {
        Appointment appointment = getAppointmentOrThrow(id);
        appointment.cancelar();
        return appointmentRepository.save(appointment);
    }

    /**
     * Conclui um agendamento (CONFIRMADO -> CONCLUIDO).
     */
    public Appointment concluirAgendamento(AppointmentId id) {
        Appointment appointment = getAppointmentOrThrow(id);
        appointment.concluir();
        return appointmentRepository.save(appointment);
    }

    /**
     * Verifica se um agendamento existe.
     */
    public boolean existe(AppointmentId id) {
        if (id == null) {
            return false;
        }
        return appointmentRepository.exists(id);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void validateCriacao(CustomerId customerId, ProfessionalId professionalId,
                                  ServiceId serviceId, AvailabilityId availabilityId,
                                  LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (customerId == null) throw new IllegalArgumentException("CustomerId é obrigatório");
        if (professionalId == null) throw new IllegalArgumentException("ProfessionalId é obrigatório");
        if (serviceId == null) throw new IllegalArgumentException("ServiceId é obrigatório");
        if (availabilityId == null) throw new IllegalArgumentException("AvailabilityId é obrigatório");
        if (date == null) throw new IllegalArgumentException("Data é obrigatória");
        if (startTime == null) throw new IllegalArgumentException("Horário de início é obrigatório");
        if (endTime == null) throw new IllegalArgumentException("Horário de fim é obrigatório");
        if (!startTime.isBefore(endTime)) throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
    }

    private void checkConflito(ProfessionalId professionalId, LocalDate date,
                                LocalTime startTime, LocalTime endTime) {
        Appointment temp = new Appointment(AppointmentId.generate(), CustomerId.from(java.util.UUID.randomUUID()),
            professionalId, ServiceId.from(java.util.UUID.randomUUID()),
            AvailabilityId.from(java.util.UUID.randomUUID()),
            date, startTime, endTime);

        List<Appointment> existentes = appointmentRepository.findByProfessionalIdAndDate(professionalId, date);
        for (Appointment existing : existentes) {
            if (existing.isAtivo() && temp.conflitaCom(existing)) {
                throw new IllegalArgumentException("Já existe um agendamento neste horário para este profissional");
            }
        }
    }

    private Appointment getAppointmentOrThrow(AppointmentId id) {
        Appointment appointment = appointmentRepository.findById(id);
        if (appointment == null) {
            throw new IllegalArgumentException("Agendamento não encontrado");
        }
        return appointment;
    }
}
