package com.troquim_bot.repository;

import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * Implementação em memória do ReservationRepository.
 */
@Repository
public class InMemoryReservationRepository implements ReservationRepository {

    private final ConcurrentMap<ReservationId, Reservation> reservations = new ConcurrentHashMap<>();

    @Override
    public Reservation save(Reservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("Reservation não pode ser nulo");
        }
        reservations.put(reservation.getId(), reservation);
        return reservation;
    }

    @Override
    public Reservation findById(ReservationId id) {
        if (id == null) {
            return null;
        }
        return reservations.get(id);
    }

    @Override
    public boolean exists(ReservationId id) {
        if (id == null) {
            return false;
        }
        return reservations.containsKey(id);
    }

    @Override
    public List<Reservation> findAll() {
        return new ArrayList<>(reservations.values());
    }

    @Override
    public List<Reservation> findByProfessionalIdAndDate(ProfessionalId professionalId, LocalDate date) {
        if (professionalId == null || date == null) {
            return List.of();
        }
        return reservations.values().stream()
            .filter(r -> professionalId.equals(r.getProfessionalId()) && date.equals(r.getDate()))
            .collect(Collectors.toList());
    }

    @Override
    public void delete(ReservationId id) {
        if (id != null) {
            reservations.remove(id);
        }
    }
}
