package com.troquim_bot.repository;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.professional.ProfessionalId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * Implementação em memória do AppointmentRepository.
 */
@Repository
public class InMemoryAppointmentRepository implements AppointmentRepository {

    private final ConcurrentMap<AppointmentId, Appointment> appointments = new ConcurrentHashMap<>();

    @Override
    public Appointment save(Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment não pode ser nulo");
        }
        appointments.put(appointment.getId(), appointment);
        return appointment;
    }

    @Override
    public Appointment findById(AppointmentId id) {
        if (id == null) {
            return null;
        }
        return appointments.get(id);
    }

    @Override
    public boolean exists(AppointmentId id) {
        if (id == null) {
            return false;
        }
        return appointments.containsKey(id);
    }

    @Override
    public List<Appointment> findAll() {
        return new ArrayList<>(appointments.values());
    }

    @Override
    public List<Appointment> findByProfessionalIdAndDate(ProfessionalId professionalId, LocalDate date) {
        if (professionalId == null || date == null) {
            return List.of();
        }
        return appointments.values().stream()
            .filter(a -> professionalId.equals(a.getProfessionalId()) && date.equals(a.getDate()))
            .collect(Collectors.toList());
    }

    @Override
    public void delete(AppointmentId id) {
        if (id != null) {
            appointments.remove(id);
        }
    }
}
