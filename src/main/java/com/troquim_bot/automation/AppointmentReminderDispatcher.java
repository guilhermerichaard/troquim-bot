package com.troquim_bot.automation;

import com.troquim_bot.application.customer.CustomerApplicationService;
import com.troquim_bot.application.professional.ProfessionalApplicationService;
import com.troquim_bot.application.service.ServiceApplicationService;
import com.troquim_bot.availability.RelogioDoNegocio;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AppointmentReminderDispatcher {
    private final ReminderCandidateRepository candidates;
    private final ReminderReceiptRepository receipts;
    private final BookingAutomationPolicyRepository policies;
    private final CustomerApplicationService customers;
    private final ServiceApplicationService services;
    private final ProfessionalApplicationService professionals;
    private final java.util.List<AppointmentReminderGateway> gateways;
    private final RelogioDoNegocio clock;

    public AppointmentReminderDispatcher(ReminderCandidateRepository candidates,
                                         ReminderReceiptRepository receipts,
                                         BookingAutomationPolicyRepository policies,
                                         CustomerApplicationService customers,
                                         ServiceApplicationService services,
                                         ProfessionalApplicationService professionals,
                                         java.util.List<AppointmentReminderGateway> gateways,
                                         RelogioDoNegocio clock) {
        this.candidates=candidates;
        this.receipts=receipts;
        this.policies=policies;
        this.customers=customers;
        this.services=services;
        this.professionals=professionals;
        this.gateways=java.util.List.copyOf(gateways);
        this.clock=clock;
    }

    @Scheduled(fixedDelayString="${troquim.automation.reminders.poll-ms:300000}")
    public void dispatchDue(){
        LocalDateTime now=LocalDateTime.of(clock.hoje(),clock.agora());
        for(var candidate:candidates.upcoming(now.toLocalDate(),now.toLocalDate().plusDays(8))){
            var policy=policies.get(candidate.businessId());
            if(!policy.reminderEnabled())continue;

            LocalDateTime start=LocalDateTime.of(candidate.date(),candidate.startTime());
            LocalDateTime due=policy.instanteDoLembrete(start);
            if(now.isBefore(due)||!now.isBefore(start))continue;

            String kind="REMINDER_"+policy.reminderHoursBefore()+"H";
            if(receipts.alreadySent(candidate.appointmentId(),kind))continue;

            var customer=customers.buscarPorId(candidate.customerId())
                    .filter(c->c.getBusinessId().equals(candidate.businessId())).orElse(null);
            var service=services.buscarPorId(candidate.businessId(),candidate.serviceId()).orElse(null);
            var professional=professionals.buscarPorId(candidate.businessId(),candidate.professionalId()).orElse(null);
            if(customer==null||service==null||professional==null)continue;

            var reminder=new AppointmentReminderGateway.Reminder(
                    candidate.appointmentId(),
                    customer.getPhone().getE164(),
                    customer.getName().getFullName(),
                    service.getNome(),
                    professional.getNome(),
                    candidate.date(),
                    candidate.startTime());

            boolean sent=false;
            for(var gateway:gateways){
                if(gateway.send(reminder)){sent=true;break;}
            }
            if(sent)receipts.markSent(candidate.appointmentId(),candidate.businessId(),kind);
        }
    }
}
