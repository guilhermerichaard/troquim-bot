package com.troquim_bot.application.reservation;

import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.ReservationRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;
import com.troquim_bot.service.ServiceId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Application Service para gerenciar Reservations.
 * 
 * Responsabilidades:
 * - Criar reservas
 * - Listar reservas
 * - Buscar reservas
 * - Atualizar reservas
 * - Cancelar reservas
 * - Prevenir reservas em horários conflitantes
 */
@org.springframework.stereotype.Service
public class ReservationApplicationService {

    private final ReservationRepository reservationRepository;

    /**
     * Construtor para MVP com repositório em memória.
     */
    public ReservationApplicationService() {
        this(new InMemoryReservationRepository());
    }

    /**
     * Construtor com injeção de dependência (para testes ou futura implementação JPA).
     */
    @Autowired
    public ReservationApplicationService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    /**
     * Cria uma nova reserva.
     * 
     * @param customerId ID do cliente
     * @param professionalId ID do profissional
     * @param serviceId ID do serviço
     * @param availabilityId ID da disponibilidade
     * @param date Data da reserva
     * @param startTime Horário de início
     * @param endTime Horário de fim
     * @param expiresAt Data de expiração da reserva temporária
     * @return Reservation criada com status ATIVO
     * @throws IllegalArgumentException se houver conflito de horário
     */
    public Reservation criarReserva(CustomerId customerId, ProfessionalId professionalId,
                                     ServiceId serviceId, AvailabilityId availabilityId,
                                     LocalDate date, LocalTime startTime, LocalTime endTime,
                                     LocalDateTime expiresAt) {
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

        ReservationId id = ReservationId.generate();

        Reservation newReservation = new Reservation(id, customerId, professionalId, serviceId,
            availabilityId, date, startTime, endTime, expiresAt);

        // Verifica conflito com reservas ativas existentes do mesmo profissional na mesma data
        List<Reservation> existentes = reservationRepository.findByProfessionalIdAndDate(professionalId, date);
        for (Reservation existing : existentes) {
            if (existing.isAtivo() && newReservation.conflitaCom(existing)) {
                throw new IllegalArgumentException("Já existe uma reserva neste horário para este profissional");
            }
        }

        return reservationRepository.save(newReservation);
    }

    /**
     * Busca reserva por ID.
     * 
     * @param id ID da reserva
     * @return Optional com o Reservation se encontrado
     */
    public Optional<Reservation> buscarPorId(ReservationId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(reservationRepository.findById(id));
    }

    /**
     * Lista todas as reservas.
     * 
     * @return Lista de todas as reservations
     */
    public List<Reservation> listarTodos() {
        return reservationRepository.findAll();
    }

    /**
     * Lista apenas reservas ativas.
     * 
     * @return Lista de reservations ativas
     */
    public List<Reservation> listarAtivos() {
        return reservationRepository.findAll().stream()
            .filter(Reservation::isAtivo)
            .toList();
    }

    /**
     * Atualiza a data da reserva.
     * 
     * @param id ID da reserva
     * @param novaData Nova data
     * @return Reservation atualizado
     */
    public Reservation atualizarData(ReservationId id, LocalDate novaData) {
        Reservation reservation = getReservationOrThrow(id);
        reservation.atualizarData(novaData);
        return reservationRepository.save(reservation);
    }

    /**
     * Atualiza o horário de início.
     * 
     * @param id ID da reserva
     * @param novoStartTime Novo horário de início
     * @return Reservation atualizado
     */
    public Reservation atualizarStartTime(ReservationId id, LocalTime novoStartTime) {
        Reservation reservation = getReservationOrThrow(id);
        reservation.atualizarStartTime(novoStartTime);
        return reservationRepository.save(reservation);
    }

    /**
     * Atualiza o horário de fim.
     * 
     * @param id ID da reserva
     * @param novoEndTime Novo horário de fim
     * @return Reservation atualizado
     */
    public Reservation atualizarEndTime(ReservationId id, LocalTime novoEndTime) {
        Reservation reservation = getReservationOrThrow(id);
        reservation.atualizarEndTime(novoEndTime);
        return reservationRepository.save(reservation);
    }

    /**
     * Atualiza a data de expiração.
     * 
     * @param id ID da reserva
     * @param novoExpiresAt Nova data de expiração
     * @return Reservation atualizado
     */
    public Reservation atualizarExpiresAt(ReservationId id, LocalDateTime novoExpiresAt) {
        Reservation reservation = getReservationOrThrow(id);
        reservation.atualizarExpiresAt(novoExpiresAt);
        return reservationRepository.save(reservation);
    }

    /**
     * Cancela uma reserva.
     * 
     * @param id ID da reserva
     * @return Reservation cancelado
     */
    public Reservation cancelarReserva(ReservationId id) {
        Reservation reservation = getReservationOrThrow(id);
        reservation.cancelar();
        return reservationRepository.save(reservation);
    }

    /**
     * Reativa uma reserva cancelada.
     * 
     * @param id ID da reserva
     * @return Reservation reativado
     */
    public Reservation reativarReserva(ReservationId id) {
        Reservation reservation = getReservationOrThrow(id);
        reservation.reativar();
        return reservationRepository.save(reservation);
    }

    /**
     * Verifica se uma reserva existe.
     * 
     * @param id ID da reserva
     * @return true se existe
     */
    public boolean existe(ReservationId id) {
        if (id == null) {
            return false;
        }
        return reservationRepository.exists(id);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private Reservation getReservationOrThrow(ReservationId id) {
        Reservation reservation = reservationRepository.findById(id);
        if (reservation == null) {
            throw new IllegalArgumentException("Reserva não encontrada");
        }
        return reservation;
    }
}
