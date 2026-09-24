package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.automation.AppointmentReminderGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnWhatsAppCloud
public class WhatsAppCloudAppointmentReminderGateway implements AppointmentReminderGateway {

    private static final Logger log =
            LoggerFactory.getLogger(WhatsAppCloudAppointmentReminderGateway.class);

    private final RestClient restClient;
    private final WhatsAppCloudProperties properties;

    public WhatsAppCloudAppointmentReminderGateway(RestClient whatsAppCloudRestClient,
                                                   WhatsAppCloudProperties properties) {
        this.restClient=whatsAppCloudRestClient;
        this.properties=properties;
    }

    @Override
    public boolean send(Reminder reminder) {
        String template=properties.getReminderTemplateName();
        String language=properties.getReminderTemplateLanguage();
        if(template==null||template.isBlank()||language==null||language.isBlank())return false;

        String appointmentId=reminder.appointmentId().getValue().toString();
        Map<String,Object> payload=Map.of(
                "messaging_product","whatsapp",
                "recipient_type","individual",
                "to",normalizePhone(reminder.phoneE164()),
                "type","template",
                "template",Map.of(
                        "name",template,
                        "language",Map.of("code",language),
                        "components",List.of(
                                Map.of(
                                        "type","body",
                                        "parameters",List.of(
                                                text(reminder.customerName()),
                                                text(reminder.serviceName()),
                                                text(formatDate(reminder.date())),
                                                text(formatTime(reminder.time())),
                                                text(reminder.professionalName())
                                        )),
                                quickReply(0,"reminder_confirm_"+appointmentId),
                                quickReply(1,"reminder_reschedule_"+appointmentId),
                                quickReply(2,"reminder_cancel_"+appointmentId)
                        )
                )
        );

        try{
            restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages",
                            properties.getGraphApiVersion(),properties.getPhoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION,"Bearer "+properties.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Appointment reminder WhatsApp template sent.");
            return true;
        }catch(RuntimeException error){
            log.warn("Appointment reminder WhatsApp send failed (type={}).",
                    error.getClass().getSimpleName());
            return false;
        }
    }

    private static Map<String,Object> text(String value){
        return Map.of("type","text","text",value==null?"":value);
    }

    private static Map<String,Object> quickReply(int index,String payload){
        return Map.of(
                "type","button",
                "sub_type","quick_reply",
                "index",Integer.toString(index),
                "parameters",List.of(Map.of("type","payload","payload",payload))
        );
    }

    private static String normalizePhone(String phone){
        return phone!=null&&phone.startsWith("+")?phone.substring(1):phone;
    }

    private static String formatDate(LocalDate date){
        return String.format("%02d/%02d/%04d",date.getDayOfMonth(),date.getMonthValue(),date.getYear());
    }

    private static String formatTime(LocalTime time){
        return String.format("%02d:%02d",time.getHour(),time.getMinute());
    }
}
