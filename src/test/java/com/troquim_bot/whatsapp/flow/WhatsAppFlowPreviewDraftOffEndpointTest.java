package com.troquim_bot.whatsapp.flow;

import com.troquim_bot.support.TestTenants;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.whatsapp.flow.infrastructure.persistence.SpringDataWhatsAppFlowSessionRepository;
import com.troquim_bot.whatsapp.flow.support.FlowTestCrypto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Preview com o token configurado MAS modo rascunho DESLIGADO (o estado de produção com o
 * Flow publicado). O portão exige draft=true, então o próprio token de preview tem de cair
 * em 427 — provado ponta a ponta, pela mesma cadeia real de criptografia do endpoint.
 *
 * É o contraponto de {@link WhatsAppFlowEndpointTest}, onde draft=true liga o preview.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WhatsApp Flow - preview inerte com draft=false")
class WhatsAppFlowPreviewDraftOffEndpointTest {

    private static final String ROTA = "/api/v1/whatsapp/flows";
    private static final String PREVIEW_TOKEN = "preview-teste-token-draft-off";

    private static final FlowTestCrypto CRYPTO = new FlowTestCrypto();

    @DynamicPropertySource
    static void chaves(DynamicPropertyRegistry registry) {
        registry.add("troquim.integrations.whatsapp.flow.enabled", () -> "true");
        registry.add("troquim.integrations.whatsapp.flow.private-key", CRYPTO::privateKeyPem);
        // Token configurado, porém DRAFT desligado (default de produção): preview inerte.
        registry.add("troquim.integrations.whatsapp.flow.modo-rascunho", () -> "false");
        registry.add("troquim.integrations.whatsapp.flow.preview-token", () -> PREVIEW_TOKEN);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppointmentApplicationService appointmentApplicationService;
    @Autowired
    private SpringDataWhatsAppFlowSessionRepository sessionRepository;

    @Test
    @DisplayName("token de preview cai em 427 quando draft=false, sem agendar nem gravar sessão")
    void previewInerteSemDraft() throws Exception {
        long sessoesAntes = sessionRepository.count();

        FlowTestCrypto.Sessao cripto = CRYPTO.novaSessao();
        String corpo = """
                {"version":"3.0","action":"data_exchange","screen":"SERVICO","flow_token":"%s",
                 "data":{"flow_action":"SERVICO_SELECIONADO","servico_id":"cabelo"}}"""
                .formatted(PREVIEW_TOKEN);

        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(CRYPTO.envelope(corpo, cripto)))
                .andExpect(result -> assertEquals(427, result.getResponse().getStatus()));

        assertTrue(appointmentApplicationService.listarAtivos(TestTenants.PILOT).isEmpty());
        // Um 427 não pode ser porta de entrada para criar sessão: nada é gravado.
        assertEquals(sessoesAntes, sessionRepository.count(),
                "Preview desligado não pode persistir linha em whatsapp_flow_sessions");
        assertTrue(sessionRepository.findById(PREVIEW_TOKEN).isEmpty(),
                "O token de preview recusado não pode existir na tabela de sessões");
    }
}
