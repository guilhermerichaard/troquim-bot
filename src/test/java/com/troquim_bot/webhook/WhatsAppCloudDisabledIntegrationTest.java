package com.troquim_bot.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quando a integração WhatsApp Cloud está DESLIGADA (enabled=false), o controller
 * e todos os beans da integração NÃO são criados (@ConditionalOnWhatsAppCloud).
 * O endpoint /webhook/whatsapp/cloud deve retornar 404 (não existe).
 * O profile "test" tem enabled=true por padrão; este teste sobrescreve para false.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "troquim.integrations.whatsapp.cloud.enabled=false"
})
@DisplayName("WhatsApp Cloud - integracao desligada (enabled=false)")
class WhatsAppCloudDisabledIntegrationTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    private static final String CLOUD = "/webhook/whatsapp/cloud";

    @Test
    @DisplayName("GET /webhook/whatsapp/cloud retorna 404 quando desligado")
    void getRetorna404QuandoDesligado() throws Exception {
        mockMvc.perform(get(CLOUD)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "qualquer")
                        .param("hub.challenge", "123"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /webhook/whatsapp/cloud retorna 404 quando desligado")
    void postRetorna404QuandoDesligado() throws Exception {
        mockMvc.perform(post(CLOUD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("rotas do webhook legado Evolution permanecem acessiveis quando cloud esta desligado")
    void rotasEvolutionPermanecemAcessiveis() throws Exception {
        // Webhooks Evolution não dependem da feature flag da WhatsApp Cloud API.
        // POST /webhook/whatsapp é público (permitAll no Security).
        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}