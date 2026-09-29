package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.owner.application.OwnerAuthenticationCodeGateway;
import com.troquim_bot.owner.application.OwnerOtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnWhatsAppCloud
public class WhatsAppCloudOwnerAuthenticationCodeGateway implements OwnerAuthenticationCodeGateway {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudOwnerAuthenticationCodeGateway.class);
    private final RestClient restClient;
    private final WhatsAppCloudProperties cloud;
    private final OwnerOtpProperties otp;

    public WhatsAppCloudOwnerAuthenticationCodeGateway(RestClient whatsAppCloudRestClient,
                                                       WhatsAppCloudProperties cloud,
                                                       OwnerOtpProperties otp) {
        this.restClient = whatsAppCloudRestClient;
        this.cloud = cloud;
        this.otp = otp;
    }

    @Override
    public boolean enviar(String phoneE164, String code) {
        if (!otp.configured()) return false;
        Map<String,Object> payload = Map.of(
                "messaging_product","whatsapp",
                "recipient_type","individual",
                "to",phoneE164,
                "type","template",
                "template",Map.of(
                        "name",otp.getTemplateName(),
                        "language",Map.of("code",otp.getTemplateLanguage()),
                        "components",List.of(
                                Map.of("type","body","parameters",List.of(
                                        Map.of("type","text","text",code)
                                )),
                                Map.of("type","button","sub_type","url","index","0","parameters",List.of(
                                        Map.of("type","text","text",code)
                                ))
                        )
                )
        );
        try {
            restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages", cloud.getGraphApiVersion(), cloud.getPhoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION,"Bearer " + cloud.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Owner authentication code sent through WhatsApp Cloud.");
            return true;
        } catch (RuntimeException e) {
            log.warn("Owner authentication code send failed (type={}).", e.getClass().getSimpleName());
            return false;
        }
    }
}
