package com.troquim_bot.automation;

import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.business.BusinessId;

public interface ReminderReceiptRepository {
    boolean alreadySent(AppointmentId appointmentId, String kind);
    boolean markSent(AppointmentId appointmentId, BusinessId businessId, String kind);
}
