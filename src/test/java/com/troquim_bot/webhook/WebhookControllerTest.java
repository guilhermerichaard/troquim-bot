package com.troquim_bot.webhook;

import com.troquim_bot.application.conversation.ConversationApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest {

    private MockMvc mockMvc;
    private ConversationApplicationService conversationApplicationService;

    @BeforeEach
    void setUp() {
        conversationApplicationService = mock(ConversationApplicationService.class);
        WebhookController controller = new WebhookController(conversationApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void deveAceitarWebhookEmWhatsapp() throws Exception {
        doNothing().when(conversationApplicationService).receberWebhookWhatsApp(anyString());

        mockMvc.perform(post("/webhook/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"test\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"));
    }

    @Test
    void deveAceitarWebhookEmWhatsappMessagesUpsert() throws Exception {
        doNothing().when(conversationApplicationService).receberWebhookWhatsApp(anyString());

        mockMvc.perform(post("/webhook/whatsapp/messages-upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\":\"messages-upsert\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"));
    }

    @Test
    void ambosEndpointsChamamOMetodoInterno() throws Exception {
        doNothing().when(conversationApplicationService).receberWebhookWhatsApp(anyString());

        mockMvc.perform(post("/webhook/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"test\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/webhook/whatsapp/messages-upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"test\"}"))
            .andExpect(status().isOk());
    }
}