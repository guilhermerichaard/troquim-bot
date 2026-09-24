package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.appointment.AppointmentStatus;
import com.troquim_bot.automation.ReminderCandidateRepository;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JpaReminderCandidateRepository implements ReminderCandidateRepository {
    private final SpringDataAppointmentRepository repository;

    public JpaReminderCandidateRepository(SpringDataAppointmentRepository repository) {
        this.repository=repository;
    }

    @Override
    public List<Candidate> upcoming(LocalDate from,LocalDate to){
        if(from==null||to==null||to.isBefore(from))return List.of();
        return repository.findByDateBetweenAndStatusIn(
                from,to,List.of(AppointmentStatus.PENDENTE.name(),AppointmentStatus.CONFIRMADO.name()))
                .stream()
                .map(e->new Candidate(
                        AppointmentId.from(e.getId()),
                        BusinessId.from(e.getBusinessId()),
                        CustomerId.from(e.getCustomerId()),
                        ServiceId.from(e.getServiceId()),
                        ProfessionalId.from(e.getProfessionalId()),
                        e.getDate(),e.getStartTime()))
                .toList();
    }
}
