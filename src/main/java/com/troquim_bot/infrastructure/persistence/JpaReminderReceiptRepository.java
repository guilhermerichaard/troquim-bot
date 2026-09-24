package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.automation.ReminderReceiptRepository;
import com.troquim_bot.business.BusinessId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaReminderReceiptRepository implements ReminderReceiptRepository {
    private final SpringDataAppointmentReminderReceiptRepository repository;

    public JpaReminderReceiptRepository(SpringDataAppointmentReminderReceiptRepository repository) {
        this.repository=repository;
    }

    @Override
    @Transactional(readOnly=true)
    public boolean alreadySent(AppointmentId appointmentId,String kind){
        if(appointmentId==null||kind==null)return false;
        return repository.existsById(new AppointmentReminderReceiptJpaEntity.Key(appointmentId.getValue(),kind));
    }

    @Override
    @Transactional
    public boolean markSent(AppointmentId appointmentId,BusinessId businessId,String kind){
        try{
            repository.saveAndFlush(new AppointmentReminderReceiptJpaEntity(
                    appointmentId.getValue(),businessId.getValue(),kind));
            return true;
        }catch(DataIntegrityViolationException duplicate){
            return false;
        }
    }
}
