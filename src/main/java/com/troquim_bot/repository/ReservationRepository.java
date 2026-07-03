package com.troquim_bot.repository;

import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository abstraction para persistência de Reservation.
 * 
 * Esta é uma interface pura sem dependência de frameworks.
 * A implementação concreta será definida na camada de infraestrutura.
 */
public interface ReservationRepository {

    /**
     * Salva uma Reservation (cria ou atualiza).
     */
    Reservation save(Reservation reservation);

    /**
     * Busca uma Reservation por ID.
     * 
     * @return Reservation se encontrado, null caso contrário
     */
    Reservation findById(ReservationId id);

    /**
     * Verifica se existe uma Reservation com o ID informado.
     */
    boolean exists(ReservationId id);

    /**
     * Busca todas as Reservations.
     */
    List<Reservation> findAll();

    /**
     * Busca todas as Reservations de um profissional em uma data.
     */
    List<Reservation> findByProfessionalIdAndDate(ProfessionalId professionalId, LocalDate date);

    /**
     * Remove uma Reservation por ID.
     */
    void delete(ReservationId id);
}