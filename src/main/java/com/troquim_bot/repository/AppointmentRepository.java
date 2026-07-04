package com.troquim_bot.repository;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository abstraction para persistência de Appointment.
 * 
 * Esta é uma interface pura sem dependência de frameworks.
 */
public interface AppointmentRepository {

    Appointment save(Appointment appointment);

    Appointment findById(AppointmentId id);

    boolean exists(AppointmentId id);

    List<Appointment> findAll();

    List<Appointment> findByProfessionalIdAndDate(ProfessionalId professionalId, LocalDate date);

    List<Appointment> findByCustomerId(CustomerId customerId);

    void delete(AppointmentId id);
}
