package com.troquim_bot.controller;

import com.troquim_bot.application.intent.IntentEngine;
import com.troquim_bot.application.intent.IntentResult;
import com.troquim_bot.application.intent.IntentType;
import com.troquim_bot.application.conversation.ConversationApplicationService;
import com.troquim_bot.application.conversation.ConversationInputMapper;
import com.troquim_bot.application.conversation.ConversationOrchestrator;
import com.troquim_bot.application.conversation.ConversationRegistry;
import com.troquim_bot.application.conversation.WhatsAppAdapter;
import com.troquim_bot.conversation.Conversation;
import com.troquim_bot.repository.InMemoryConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationControllerTest {

    private MockMvc mockMvc;
    private ConversationApplicationService conversationApplicationService;

    private String customerId;
    private String serviceId;
    private String professionalId;
    private String reservationId;
    private String appointmentId;

    @BeforeEach
    void setUp() {
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        conversationApplicationService = new ConversationApplicationService(
            new ConversationRegistry(new InMemoryConversationRepository()),
            new ConversationOrchestrator(
                (numero, mensagem) -> "resposta",
                new IgnoringWhatsAppAdapter(),
                intentEngine
            ),
            new ConversationInputMapper()
        );
        ConversationController conversationController = new ConversationController(conversationApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(conversationController).build();

        customerId = UUID.randomUUID().toString();
        serviceId = UUID.randomUUID().toString();
        professionalId = UUID.randomUUID().toString();
        reservationId = UUID.randomUUID().toString();
        appointmentId = UUID.randomUUID().toString();
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistemConversas() throws Exception {
        mockMvc.perform(get("/conversations"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deveListarConversas() throws Exception {
        String outroCustomerId = UUID.randomUUID().toString();
        conversationApplicationService.criarConversa(customerId);
        conversationApplicationService.criarConversa(outroCustomerId);

        mockMvc.perform(get("/conversations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].customerId", hasItems(customerId, outroCustomerId)));
    }

    @Test
    void deveBuscarConversaPorId() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        mockMvc.perform(get("/conversations/" + conversation.getId().getValue()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(conversation.getId().getValue().toString()))
            .andExpect(jsonPath("$.customerId").value(customerId))
            .andExpect(jsonPath("$.currentStep").value("IDLE"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.criadoEm").exists())
            .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    void deveRetornar404AoBuscarConversaInexistente() throws Exception {
        mockMvc.perform(get("/conversations/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400AoBuscarComIdInvalido() throws Exception {
        mockMvc.perform(get("/conversations/id-invalido"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveCriarConversa() throws Exception {
        String requestBody = "{\"customerId\":\"" + customerId + "\"}";

        mockMvc.perform(post("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.customerId").value(customerId))
            .andExpect(jsonPath("$.currentStep").value("IDLE"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deveRetornar400AoCriarComRequestNula() throws Exception {
        mockMvc.perform(post("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400AoCriarComCustomerIdInvalido() throws Exception {
        mockMvc.perform(post("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"invalido\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveAtualizarCamposParcialmente() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);
        String requestBody = "{"
            + "\"selectedServiceId\":\"" + serviceId + "\","
            + "\"selectedProfessionalId\":\"" + professionalId + "\","
            + "\"selectedDate\":\"2026-07-20\","
            + "\"selectedStartTime\":\"10:00\","
            + "\"selectedEndTime\":\"10:30\","
            + "\"reservationId\":\"" + reservationId + "\","
            + "\"appointmentId\":\"" + appointmentId + "\""
            + "}";

        mockMvc.perform(put("/conversations/" + conversation.getId().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.selectedServiceId").value(serviceId))
            .andExpect(jsonPath("$.selectedProfessionalId").value(professionalId))
            .andExpect(jsonPath("$.selectedDate").value("2026-07-20"))
            .andExpect(jsonPath("$.selectedStartTime").value("10:00"))
            .andExpect(jsonPath("$.selectedEndTime").value("10:30"))
            .andExpect(jsonPath("$.reservationId").value(reservationId))
            .andExpect(jsonPath("$.appointmentId").value(appointmentId));
    }

    @Test
    void deveRetornar404AoAtualizarConversaInexistente() throws Exception {
        mockMvc.perform(put("/conversations/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedDate\":\"2026-07-20\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400AoAtualizarComDadosInvalidos() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        mockMvc.perform(put("/conversations/" + conversation.getId().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedDate\":\"20/07/2026\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveAvancarEtapa() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        mockMvc.perform(put("/conversations/" + conversation.getId().getValue() + "/next"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value("SELECT_SERVICE"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deveAvancarAteFinalizar() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);
        String id = conversation.getId().getValue().toString();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put("/conversations/" + id + "/next"))
                .andExpect(status().isOk());
        }

        mockMvc.perform(put("/conversations/" + id + "/next"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value("FINISHED"))
            .andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void deveRetornar400AoAvancarConversaFinalizada() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);
        String id = conversation.getId().getValue().toString();
        for (int i = 0; i < 6; i++) {
            conversationApplicationService.avancarEtapa(conversation.getId());
        }

        mockMvc.perform(put("/conversations/" + id + "/next"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveVoltarEtapa() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);
        conversationApplicationService.avancarEtapa(conversation.getId());
        conversationApplicationService.avancarEtapa(conversation.getId());

        mockMvc.perform(put("/conversations/" + conversation.getId().getValue() + "/back"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value("SELECT_SERVICE"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deveRetornar400AoVoltarNoIdle() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        mockMvc.perform(put("/conversations/" + conversation.getId().getValue() + "/back"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveResetarConversa() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);
        conversationApplicationService.avancarEtapa(conversation.getId());
        conversationApplicationService.atualizarCampos(
            conversation.getId(),
            serviceId,
            professionalId,
            "2026-07-20",
            "10:00",
            "10:30",
            reservationId,
            appointmentId
        );

        mockMvc.perform(put("/conversations/" + conversation.getId().getValue() + "/reset"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStep").value("IDLE"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.selectedServiceId").doesNotExist())
            .andExpect(jsonPath("$.reservationId").doesNotExist());
    }

    @Test
    void deveCancelarConversaNoDelete() throws Exception {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        mockMvc.perform(delete("/conversations/" + conversation.getId().getValue()))
            .andExpect(status().isNoContent());

        Conversation cancelada = conversationApplicationService.buscarPorId(conversation.getId()).orElseThrow();
        assertEquals("CANCELLED", cancelada.getStatus().name());
        assertEquals("FINISHED", cancelada.getCurrentStep().name());
    }

    @Test
    void deveRetornar404AoDeletarConversaInexistente() throws Exception {
        mockMvc.perform(delete("/conversations/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400AoDeletarComIdInvalido() throws Exception {
        mockMvc.perform(delete("/conversations/id-invalido"))
            .andExpect(status().isBadRequest());
    }

    private static class IgnoringWhatsAppAdapter implements WhatsAppAdapter {
        @Override
        public Optional<IncomingMessage> receberMensagem(String payload) {
            return Optional.empty();
        }

        @Override
        public void enviarMensagem(String numero, String texto) {
        }
    }
}
