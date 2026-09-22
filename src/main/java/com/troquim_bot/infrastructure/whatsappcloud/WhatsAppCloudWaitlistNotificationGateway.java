package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.application.waitlist.WaitlistNotificationGateway;
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

/**
 * Aviso proativo da waitlist via template aprovado da Meta.
 *
 * Sem template configurado, retorna false e a entrada permanece ACTIVE para retry futuro.
 * Não há fallback para texto livre fora da janela de atendimento.
 */
@Component
@ConditionalOnWhatsAppCloud
public class WhatsAppCloudWaitlistNotificationGateway implements WaitlistNotificationGateway {

    private static final Logger log =
            LoggerFactory.getLogger(WhatsAppCloudWaitlistNotificationGateway.class);

    private final RestClient restClient;
    private final WhatsAppCloudProperties properties;

    public WhatsAppCloudWaitlistNotificationGateway(RestClient whatsAppCloudRestClient,
                                                    WhatsAppCloudProperties properties) {
        this.restClient = whatsAppCloudRestClient;
        this.properties = properties;
    }

    @Override
    public boolean notifySlotAvailable(String phoneE164,
                                       String serviceName,
                                       LocalDate date,
                                       LocalTime time) {
        String template = properties.getWaitlistTemplateName();
        if (template == null || template.isBlank()) {
            return false;
        }

        String language = properties.getWaitlistTemplateLanguage();
        if (language == null || language.isBlank()) {
            return false;
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", phoneE164.startsWith("+") ? phoneE164.substring(1) : phoneE164,
                "type", "template",
                "template", Map.of(
                        "name", template,
                        "language", Map.of("code", language),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", List.of(
                                        Map.of("type", "text", "text", serviceName),
                                        Map.of("type", "text", "text", formatDate(date)),
                                        Map.of("type", "text", "text", formatTime(time))
                                )))));

        try {
            restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages",
                            properties.getGraphApiVersion(), properties.getPhoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Waitlist WhatsApp notification sent.");
            return true;
        } catch (RuntimeException error) {
            log.warn("Waitlist WhatsApp notification failed (type={}).",
                    error.getClass().getSimpleName());
            return false;
        }
    }

    private static String formatDate(LocalDate date) {
        return String.format("%02d/%02d/%04d",
                date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private static String formatTime(LocalTime time) {
        return time.getMinute() == 0
                ? time.getHour() + "h"
                : time.getHour() + ":" + String.format("%02d", time.getMinute());
    }
}
