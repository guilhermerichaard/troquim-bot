package com.troquim_bot.automation;

import com.troquim_bot.appointment.AppointmentId;
import java.time.LocalDate;
import java.time.LocalTime;

public interface AppointmentReminderGateway {
    boolean send(Reminder reminder);

    record Reminder(AppointmentId appointmentId,
                    String phoneE164,
                    String customerName,
                    String serviceName,
                    String professionalName,
                    LocalDate date,
                    LocalTime time){}
}
