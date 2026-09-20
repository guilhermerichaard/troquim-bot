package com.troquim_bot.controller;

import com.troquim_bot.business.Business;
import com.troquim_bot.repository.BusinessCalendarRepository;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.BusinessPublicProfileRepository;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.business.DiaSemana;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Onboarding administrativo usa ProvisionarNegocio")
class AdminOnboardingControllerTest {

    private static final String ROUTE = "/api/v1/admin/onboarding/provision";
    private static final String AUTH = "Bearer test-admin-key";

    @Autowired MockMvc mockMvc;
    @Autowired BusinessRepository businessRepository;
    @Autowired ServiceRepository serviceRepository;
    @Autowired ProfessionalRepository professionalRepository;
    @Autowired AvailabilityRepository availabilityRepository;
    @Autowired BusinessCalendarRepository businessCalendarRepository;
    @Autowired BusinessPublicProfileRepository businessPublicProfileRepository;

    @BeforeEach
    void garantirNegocio() {
        if (!businessRepository.exists(TestTenants.PILOT)) {
            businessRepository.save(new Business(
                    TestTenants.PILOT, "Negócio de Teste", null, null));
        }
    }

    @Test
    @DisplayName("sem Bearer admin a rota é recusada")
    void exigeAdmin() throws Exception {
        mockMvc.perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("provisiona catálogo, vínculo, expediente e disponibilidade de forma idempotente")
    void provisionaTudoViaCasoDeUso() throws Exception {
        mockMvc.perform(post(ROUTE)
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isOk());

        // Repetição do MESMO onboarding não duplica identidade nem períodos.
        mockMvc.perform(post(ROUTE)
                        .header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()))
                .andExpect(status().isOk());

        long unhas = serviceRepository.listarTodos(TestTenants.PILOT).stream()
                .filter(s -> "Unhas Onboarding".equals(s.getNome()))
                .count();
        long cabelo = serviceRepository.listarTodos(TestTenants.PILOT).stream()
                .filter(s -> "Cabelo Onboarding".equals(s.getNome()))
                .count();
        assertEquals(1, unhas);
        assertEquals(1, cabelo);

        var professional = professionalRepository.listarTodos(TestTenants.PILOT).stream()
                .filter(p -> "Malu Onboarding".equals(p.getNome()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, professional.getServicosHabilitados().size());

        assertEquals(2, businessCalendarRepository.buscar(TestTenants.PILOT)
                .getExpediente().periodosDe(DiaSemana.SEGUNDA).size());

        assertEquals(2, availabilityRepository
                .listarAtivasPorProfissionalEDia(
                        TestTenants.PILOT, professional.getId(), DiaSemana.SEGUNDA)
                .size());

        assertTrue(professional.isAtivo());

        var profile = businessPublicProfileRepository.buscarPorBusinessId(TestTenants.PILOT)
                .orElseThrow();
        assertEquals("studio-bella-demo-test", profile.getSlug().getValue());
        assertTrue(profile.publicado());
    }

    private static String payload() {
        return """
                {
                  "business": {
                    "name":"Studio Bella Demo Test"
                  },
                  "services": [
                    {"name":"Unhas Onboarding","durationMinutes":60},
                    {"name":"Cabelo Onboarding","durationMinutes":90}
                  ],
                  "professional": {
                    "name":"Malu Onboarding",
                    "phone":"+5511999999999",
                    "services":["Unhas Onboarding","Cabelo Onboarding"],
                    "availability":[
                      {"day":"SEGUNDA","periods":[
                        {"start":"09:00","end":"12:00"},
                        {"start":"13:00","end":"18:00"}
                      ]}
                    ]
                  },
                  "businessHours":[
                    {"day":"SEGUNDA","periods":[
                      {"start":"09:00","end":"12:00"},
                      {"start":"13:00","end":"18:00"}
                    ]}
                  ],
                  "publicProfile": {
                    "slug":"studio-bella-demo-test",
                    "publicName":"Studio Bella Demo Test",
                    "shortDescription":"Perfil de teste do onboarding",
                    "publish":true
                  }
                }
                """;
    }
}
