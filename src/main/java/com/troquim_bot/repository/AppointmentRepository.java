package com.troquim_bot.repository;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.business.BusinessId;
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

    /**
     * Agendamentos do negócio para um profissional numa data.
     *
     * É esta a consulta que a disponibilidade e a checagem de conflito usam: o
     * professional_id do catálogo do Flow é sintético e compartilhado entre negócios,
     * então filtrar apenas por profissional faria a agenda de um tenant ocupar o
     * horário de outro.
     */
    List<Appointment> findByBusinessIdAndProfessionalIdAndDate(
            BusinessId businessId, ProfessionalId professionalId, LocalDate date);

    /** Toda a agenda de um negócio. Base da agenda do dono em /app. */
    List<Appointment> findByBusinessId(BusinessId businessId);

    List<Appointment> findByCustomerId(CustomerId customerId);

    void delete(AppointmentId id);
}
