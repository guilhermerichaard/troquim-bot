package com.troquim_bot.application.appointment;

import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.application.catalog.ConfirmarAgendamentoDoCatalogo;
import com.troquim_bot.application.customer.CustomerApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.customer.Customer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
public class AppointmentRescheduleApplicationService {

    private final AppointmentApplicationService appointments;
    private final ConfirmarAgendamentoDoCatalogo confirmar;
    private final CustomerApplicationService customers;

    public AppointmentRescheduleApplicationService(AppointmentApplicationService appointments,
                                                   ConfirmarAgendamentoDoCatalogo confirmar,
                                                   CustomerApplicationService customers) {
        this.appointments=appointments;
        this.confirmar=confirmar;
        this.customers=customers;
    }

    @Transactional
    public Result reschedule(BusinessId businessId,
                             AppointmentId appointmentId,
                             LocalDate date,
                             LocalTime time,
                             String idempotencyKey) {
        if(businessId==null||appointmentId==null||date==null||time==null
                ||idempotencyKey==null||idempotencyKey.isBlank()){
            throw new IllegalArgumentException("Dados de reagendamento incompletos");
        }

        Appointment old=appointments.buscarPorId(appointmentId)
                .filter(a->a.pertenceAoTenant(businessId))
                .orElseThrow(()->new IllegalArgumentException("Agendamento não encontrado"));

        Customer customer=customers.buscarPorId(old.getCustomerId())
                .filter(c->c.getBusinessId().equals(businessId))
                .orElseThrow(()->new IllegalArgumentException("Cliente não encontrado"));

        appointments.cancelarAgendamento(old.getId());

        BookingCommandKey key=BookingCommandKey.deChaveExclusiva(
                businessId,idempotencyKey,customer.getPhone().getValue(),
                old.getServiceId(),old.getProfessionalId(),date,time);

        var result=confirmar.confirmar(new ConfirmarAgendamentoDoCatalogo.Pedido(
                businessId,
                old.getServiceId(),
                old.getProfessionalId(),
                customer.getPhone().getValue(),
                customer.getName().getFullName(),
                date,
                time,
                key));

        BookingResult booking=result.agendamento().orElse(null);
        if(result.foiRecusado()||booking==null||!booking.isConfirmado()){
            String message=booking!=null&&booking.mensagem()!=null
                    ?booking.mensagem()
                    :"Novo horário indisponível. O agendamento original foi preservado.";
            throw new RescheduleRejectedException(message);
        }

        return new Result(true,"Agendamento reagendado.");
    }

    public record Result(boolean ok,String message){}

    public static class RescheduleRejectedException extends RuntimeException {
        public RescheduleRejectedException(String message){super(message);}
    }
}
