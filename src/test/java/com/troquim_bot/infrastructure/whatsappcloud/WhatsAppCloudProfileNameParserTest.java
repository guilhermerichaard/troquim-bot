package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.application.messaging.InboundTextMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhatsAppCloudProfileNameParserTest {

    @Test
    void preservaNomeDoPerfilDoContatoNaMensagemNeutra() {
        WhatsAppCloudProperties properties = new WhatsAppCloudProperties();
        properties.setPhoneNumberId("phone-1");

        WhatsAppCloudMessageParser parser = new WhatsAppCloudMessageParser(properties);

        String payload = """
                {
                  "object":"whatsapp_business_account",
                  "entry":[{
                    "changes":[{
                      "value":{
                        "metadata":{"phone_number_id":"phone-1"},
                        "contacts":[{"profile":{"name":"Gui"},"wa_id":"5511999999999"}],
                        "messages":[{
                          "id":"wamid.1",
                          "from":"5511999999999",
                          "timestamp":"1700000000",
                          "type":"text",
                          "text":{"body":"Oi"}
                        }]
                      }
                    }]
                  }]
                }
                """;

        InboundTextMessage message = parser.parse(payload.getBytes(StandardCharsets.UTF_8))
                .textMessages().getFirst();

        assertEquals("Gui", message.profileName());
        assertEquals("5511999999999", message.fromPhone());
        assertEquals("Oi", message.text());
    }
}
