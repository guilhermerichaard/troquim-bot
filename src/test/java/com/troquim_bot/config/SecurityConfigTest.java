package com.troquim_bot.config;

import com.troquim_bot.controller.DevConversationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Seguranca - Default Deny")
class SecurityConfigTest {

    private static final String VALID_TOKEN = "test-admin-key";
    private static final String AUTHORIZATION = "Authorization";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Os dois webhooks atuais sao publicos")
    void webhooksPublicosSemToken() throws Exception {
        mockMvc.perform(post("/webhook/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"test\":true}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/webhook/whatsapp/messages-upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"test\":true}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A allowlist do webhook nao libera subrotas ou outros metodos")
    void webhookAllowlistExata() throws Exception {
        mockMvc.perform(post("/webhook/whatsapp/extra")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/webhook/whatsapp"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Health e publico")
    void actuatorHealthPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("APIs administrativas sem credencial retornam 401")
    void APIsAdministrativasSemTokenRetornam401() throws Exception {
        String[] paths = {
            "/appointments", "/availability", "/business", "/clientes",
            "/conversations", "/customers", "/ordens", "/professionals",
            "/reservations", "/services"
        };

        for (String path : paths) {
            mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("Esquema invalido, token vazio e token incorreto retornam 401")
    void credenciaisInvalidasRetornam401() throws Exception {
        mockMvc.perform(get("/appointments")
                .header(AUTHORIZATION, "Basic dXNlcjpwYXNz"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/appointments")
                .header(AUTHORIZATION, "Bearer "))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/appointments")
                .header(AUTHORIZATION, "Bearer token-incorreto"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Bearer valido cria Authentication ADMIN e libera API administrativa")
    void tokenValidoLiberaAPIAdministrativa() throws Exception {
        mockMvc.perform(get("/appointments")
                .header(AUTHORIZATION, "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rota fora da allowlist permanece fechada")
    void rotaNaoClassificadaPermaneceFechada() throws Exception {
        mockMvc.perform(get("/rota-inexistente"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/rota-inexistente")
                .header(AUTHORIZATION, "Bearer " + VALID_TOKEN))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Controllers auxiliares antigos nao sao publicos")
    void controllersAuxiliaresNaoSaoPublicos() throws Exception {
        mockMvc.perform(get("/hello"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/lead"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/chatbot/mensagem")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DevConversationController nao existe fora do profile dev")
    void devControllerAusenteNoProfileTest() throws Exception {
        assertTrue(applicationContext.getBeansOfType(DevConversationController.class).isEmpty());

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"5511999999999\",\"message\":\"teste\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Actuator sensivel fica fechado mesmo para um token valido")
    void actuatorSensivelFechado() throws Exception {
        mockMvc.perform(get("/actuator/env"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/beans")
                .header(AUTHORIZATION, "Bearer " + VALID_TOKEN))
            .andExpect(status().isForbidden());
    }
}
