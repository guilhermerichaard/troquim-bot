package com.troquim_bot.whatsapp.flow;

import com.troquim_bot.support.CatalogoDeTeste;
import com.troquim_bot.support.TestTenants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.whatsapp.flow.infrastructure.persistence.SpringDataWhatsAppFlowSessionRepository;
import com.troquim_bot.whatsapp.flow.support.FlowTestCrypto;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Preview LIGADO (draft=true + token configurado) não deixa rastro em
 * {@code whatsapp_flow_sessions}.
 *
 * A sessão de preview é efêmera por construção — {@code FlowSession.preview(...)} nasce sem
 * telefone e sem businessId, e a tabela exige ambos NOT NULL. Este teste prende essa
 * garantia pelo lado observável: navegar e até CONFIRMAR pelo token de preview não pode
 * criar nenhuma linha de sessão nem nenhum agendamento.
 *
 * O contraponto com draft desligado está em {@link WhatsAppFlowPreviewDraftOffEndpointTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WhatsApp Flow - preview ligado não persiste sessão")
class WhatsAppFlowPreviewNaoPersisteSessaoTest {

    private static final String ROTA = "/api/v1/whatsapp/flows";
    private static final String PREVIEW_TOKEN = "preview-teste-sem-persistencia";

    private static final FlowTestCrypto CRYPTO = new FlowTestCrypto();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void chaves(DynamicPropertyRegistry registry) {
        registry.add("troquim.integrations.whatsapp.flow.enabled", () -> "true");
        registry.add("troquim.integrations.whatsapp.flow.private-key", CRYPTO::privateKeyPem);
        // Preview LIGADO: as duas condições valendo juntas.
        registry.add("troquim.integrations.whatsapp.flow.modo-rascunho", () -> "true");
        registry.add("troquim.integrations.whatsapp.flow.preview-token", () -> PREVIEW_TOKEN);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SpringDataWhatsAppFlowSessionRepository sessionRepository;
    @Autowired
    private AppointmentApplicationService appointmentApplicationService;
    @Autowired
    private ProvisionarNegocio provisionarNegocio;
    @Autowired
    private ConsultarCatalogo consultarCatalogo;

    /**
     * O preview não tem tenant próprio: navega sobre o catálogo do negócio corrente. Sem
     * catálogo provisionado não haveria serviço algum para o editor da Meta exercitar.
     */
    @BeforeEach
    void provisionarCatalogo() {
        CatalogoDeTeste.provisionar(provisionarNegocio, TestTenants.PILOT);
    }

    @Test
    @DisplayName("navegar e confirmar pelo token de preview não grava sessão nem agendamento")
    void previewNaoDeixaRastro() throws Exception {
        long sessoesAntes = sessionRepository.count();
        String cabelo = CatalogoDeTeste.servicoId(
                consultarCatalogo, TestTenants.PILOT, CatalogoDeTeste.CABELO);
        String unhas = CatalogoDeTeste.servicoId(
                consultarCatalogo, TestTenants.PILOT, CatalogoDeTeste.UNHAS);
        String profissional = CatalogoDeTeste.profissionalId(
                consultarCatalogo, TestTenants.PILOT, CatalogoDeTeste.UNHAS);

        // 1. Navegação: escolher serviço avança de tela (o preview existe para isso).
        JsonNode navegacao = trocar("""
                {"version":"3.0","action":"data_exchange","screen":"SERVICO","flow_token":"%s",
                 "data":{"flow_action":"SERVICO_SELECIONADO","servico_id":"%s"}}"""
                .formatted(PREVIEW_TOKEN, cabelo));
        assertEquals("SERVICO", navegacao.path("screen").asText());

        // 2. Confirmação: encerra em SUCCESS simulado.
        JsonNode confirmacao = trocar("""
                {"version":"3.0","action":"data_exchange","screen":"CONFIRMACAO","flow_token":"%s",
                 "data":{"flow_action":"CONFIRMAR_AGENDAMENTO","servico_id":"%s","profissional_id":"%s",
                 "data":"%s","horario":"10:00","nome":"Ana Souza"}}"""
                .formatted(PREVIEW_TOKEN, unhas, profissional, proximaQuarta()));
        assertEquals("SUCCESS", confirmacao.path("screen").asText());

        // 3. O que importa: nada foi gravado.
        assertEquals(sessoesAntes, sessionRepository.count(),
                "Sessão de preview é efêmera: não pode virar linha em whatsapp_flow_sessions");
        assertTrue(sessionRepository.findById(PREVIEW_TOKEN).isEmpty(),
                "O token de preview não pode existir na tabela de sessões");
        assertTrue(appointmentApplicationService.listarAtivos(TestTenants.PILOT).isEmpty(),
                "Um token de preview NUNCA pode criar agendamento real");
    }

    private JsonNode trocar(String corpoClaro) throws Exception {
        FlowTestCrypto.Sessao cripto = CRYPTO.novaSessao();
        MvcResult resultado = mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(CRYPTO.envelope(corpoClaro, cripto)))
                .andReturn();
        assertEquals(200, resultado.getResponse().getStatus(),
                "O preview ligado deve responder 200, não 427");
        return MAPPER.readTree(CRYPTO.decifrarResposta(
                resultado.getResponse().getContentAsString(), cripto));
    }

    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != java.time.DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }
}
