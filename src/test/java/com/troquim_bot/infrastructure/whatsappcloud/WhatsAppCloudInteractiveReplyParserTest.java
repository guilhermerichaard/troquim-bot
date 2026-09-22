package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.application.messaging.ParsedInboundPayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppCloudInteractiveReplyParserTest {

    @Test
    void buttonReplyViraComandoCanonico() {
        WhatsAppCloudMessageParser parser = parser();

        ParsedInboundPayload parsed = parser.parse(payload("""
                "type":"interactive",
                "interactive":{"type":"button_reply","button_reply":{
                  "id":"menu_meus_agendamentos","title":"Meus agendamentos"
                }}
                """));

        assertEquals(1, parsed.textMessages().size());
        assertEquals("2", parsed.textMessages().get(0).text());
        assertTrue(parsed.flowCompletions().isEmpty());
    }

    @Test
    void listReplyPreservaIndiceCanonico() {
        WhatsAppCloudMessageParser parser = parser();

        ParsedInboundPayload parsed = parser.parse(payload("""
                "type":"interactive",
                "interactive":{"type":"list_reply","list_reply":{
                  "id":"5","title":"Sexta"
                }}
                """));

        assertEquals(1, parsed.textMessages().size());
        assertEquals("5", parsed.textMessages().get(0).text());
        assertTrue(parsed.flowCompletions().isEmpty());
    }

    @Test
    void templateQuickReplyPreservaPayloadDaWaitlist() {
        WhatsAppCloudMessageParser parser = parser();
        String claim = "waitlist_claim_123e4567-e89b-12d3-a456-426614174000_2026-09-25_1700";

        ParsedInboundPayload parsed = parser.parse(payload("""
                "type":"button",
                "button":{"payload":"%s","text":"Quero esse horário"}
                """.formatted(claim)));

        assertEquals(1, parsed.textMessages().size());
        assertEquals(claim, parsed.textMessages().get(0).text());
        assertTrue(parsed.flowCompletions().isEmpty());
    }

    private WhatsAppCloudMessageParser parser() {
        WhatsAppCloudProperties properties = new WhatsAppCloudProperties();
        properties.setPhoneNumberId("test-phone-number-id");
        return new WhatsAppCloudMessageParser(properties);
    }

    private byte[] payload(String messageFields) {
        return ("""
                {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
                "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"test-phone-number-id"},
                "messages":[{"id":"wamid.INTERACTIVE","from":"5511999990001","timestamp":"1700000000",
                %s}]}}]}]}
                """.formatted(messageFields)).getBytes(StandardCharsets.UTF_8);
    }
}
