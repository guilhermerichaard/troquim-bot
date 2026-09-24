package com.troquim_bot.automation;

import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ReminderCandidateRepository {
    List<Candidate> upcoming(LocalDate from, LocalDate to);

    record Candidate(AppointmentId appointmentId,
                     BusinessId businessId,
                     CustomerId customerId,
                     ServiceId serviceId,
                     ProfessionalId professionalId,
                     LocalDate date,
                     LocalTime startTime){}
}
