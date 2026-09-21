package com.troquim_bot.infrastructure.whatsappcloud;

import com.troquim_bot.application.messaging.ParsedInboundPayload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppCloudFlowCompletionParserTest {

    @Test
    void nfmReplyViraConclusaoDeFlowSemVirarTextoFake() {
        WhatsAppCloudProperties properties = new WhatsAppCloudProperties();
        properties.setPhoneNumberId("test-phone-number-id");
        WhatsAppCloudMessageParser parser = new WhatsAppCloudMessageParser(properties);

        byte[] raw = """
                {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
                "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"test-phone-number-id"},
                "messages":[{"id":"wamid.FLOW1","from":"5511999990001","timestamp":"1700000000",
                "type":"interactive","interactive":{"type":"nfm_reply","nfm_reply":{
                "response_json":"{\\\"flow_token\\\":\\\"token-123\\\",\\\"servico\\\":\\\"NAO_CONFIAR\\\"}"}}}]}}]}]}
                """.getBytes(StandardCharsets.UTF_8);

        ParsedInboundPayload parsed = parser.parse(raw);

        assertTrue(parsed.textMessages().isEmpty(), "nfm_reply nao pode ser mascarado como texto");
        assertEquals(1, parsed.flowCompletions().size());
        var completion = parsed.flowCompletions().get(0);
        assertEquals("whatsapp_cloud", completion.provider());
        assertEquals("wamid.FLOW1", completion.externalMessageId());
        assertEquals("5511999990001", completion.fromPhone());
        assertEquals("token-123", completion.flowToken());
    }

    @Test
    void nfmReplySemFlowTokenEhIgnorado() {
        WhatsAppCloudProperties properties = new WhatsAppCloudProperties();
        properties.setPhoneNumberId("test-phone-number-id");
        WhatsAppCloudMessageParser parser = new WhatsAppCloudMessageParser(properties);

        byte[] raw = """
                {"object":"whatsapp_business_account","entry":[{"id":"WABA","changes":[{"field":"messages",
                "value":{"messaging_product":"whatsapp","metadata":{"phone_number_id":"test-phone-number-id"},
                "messages":[{"id":"wamid.FLOW2","from":"5511999990001","timestamp":"1700000000",
                "type":"interactive","interactive":{"type":"nfm_reply","nfm_reply":{
                "response_json":"{\\\"servico\\\":\\\"Manicure\\\"}"}}}]}}]}]}
                """.getBytes(StandardCharsets.UTF_8);

        ParsedInboundPayload parsed = parser.parse(raw);
        assertTrue(parsed.flowCompletions().isEmpty());
    }
}
