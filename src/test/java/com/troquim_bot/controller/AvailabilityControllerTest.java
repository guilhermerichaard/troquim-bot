package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.CreateAvailabilityRequest;
import com.troquim_bot.controller.dto.UpdateAvailabilityRequest;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.AvailabilityStatus;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class AvailabilityControllerTest {

    private MockMvc mockMvc;
    private AvailabilityApplicationService availabilityApplicationService;
    private InMemoryAvailabilityRepository availabilityRepository;

    private final ProfessionalId profId1 = ProfessionalId.from(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        availabilityRepository = new InMemoryAvailabilityRepository();
        availabilityApplicationService = new AvailabilityApplicationService(availabilityRepository);
        AvailabilityController availabilityController = new AvailabilityController(availabilityApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(availabilityController).build();
    }

    // ==================== GET /availability ====================

    @Test
    void deveRetornar200QuandoListarTodos() throws Exception {
        // Cria algumas disponibilidades
        availabilityApplicationService.criarDisponibilidade(profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        availabilityApplicationService.criarDisponibilidade(profId1, DiaSemana.TERCA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Testa GET /availability
        mockMvc.perform(get("/availability"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistem() throws Exception {
        // Testa GET /availability sem disponibilidades
        mockMvc.perform(get("/availability"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ==================== GET /availability/{id} ====================

    @Test
    void deveRetornar200QuandoBuscarPorIdExistente() throws Exception {
        // Cria uma disponibilidade
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        String availabilityId = availability.getId().getValue().toString();

        // Testa GET /availability/{id}
        mockMvc.perform(get("/availability/" + availabilityId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(availabilityId))
            .andExpect(jsonPath("$.professionalId").value(profId1.getValue().toString()))
            .andExpect(jsonPath("$.dayOfWeek").value("SEGUNDA"))
            .andExpect(jsonPath("$.startTime").value("08:00"))
            .andExpect(jsonPath("$.endTime").value("12:00"))
            .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    void deveRetornar404QuandoBuscarPorIdInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(get("/availability/" + nonExistentId))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400QuandoIdInvalido() throws Exception {
        mockMvc.perform(get("/availability/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }

    // ==================== POST /availability ====================

    @Test
    void deveCriarDisponibilidadeERetornar201() throws Exception {
        String requestBody = "{\"professionalId\":\"" + profId1.getValue().toString() +
            "\",\"dayOfWeek\":\"SEGUNDA\",\"startTime\":\"08:00\",\"endTime\":\"12:00\"}";

        mockMvc.perform(post("/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.professionalId").value(profId1.getValue().toString()))
            .andExpect(jsonPath("$.dayOfWeek").value("SEGUNDA"))
            .andExpect(jsonPath("$.startTime").value("08:00"))
            .andExpect(jsonPath("$.endTime").value("12:00"))
            .andExpect(jsonPath("$.status").value("ATIVO"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.criadoEm").exists())
            .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    void deveRetornar400QuandoRequestNull() throws Exception {
        mockMvc.perform(post("/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoProfessionalIdInvalido() throws Exception {
        String requestBody = "{\"professionalId\":\"invalid-uuid\",\"dayOfWeek\":\"SEGUNDA\",\"startTime\":\"08:00\",\"endTime\":\"12:00\"}";

        mockMvc.perform(post("/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoDayOfWeekInvalido() throws Exception {
        String requestBody = "{\"professionalId\":\"" + profId1.getValue().toString() +
            "\",\"dayOfWeek\":\"INVALIDO\",\"startTime\":\"08:00\",\"endTime\":\"12:00\"}";

        mockMvc.perform(post("/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoHorarioSobreposto() throws Exception {
        // Cria primeira disponibilidade
        availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Tenta criar horário sobreposto
        String requestBody = "{\"professionalId\":\"" + profId1.getValue().toString() +
            "\",\"dayOfWeek\":\"SEGUNDA\",\"startTime\":\"09:00\",\"endTime\":\"11:00\"}";

        mockMvc.perform(post("/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    // ==================== PUT /availability/{id} ====================

    @Test
    void deveAtualizarHorarioCompleto() throws Exception {
        // Cria uma disponibilidade
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        String availabilityId = availability.getId().getValue().toString();

        // Atualiza todos os campos
        String requestBody = "{\"dayOfWeek\":\"QUARTA\",\"startTime\":\"14:00\",\"endTime\":\"18:00\"}";

        mockMvc.perform(put("/availability/" + availabilityId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dayOfWeek").value("QUARTA"))
            .andExpect(jsonPath("$.startTime").value("14:00"))
            .andExpect(jsonPath("$.endTime").value("18:00"));
    }

    @Test
    void deveAtualizarApenasDayOfWeek() throws Exception {
        // Cria uma disponibilidade
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        String availabilityId = availability.getId().getValue().toString();

        // Atualiza apenas o dia da semana
        String requestBody = "{\"dayOfWeek\":\"TERCA\"}";

        mockMvc.perform(put("/availability/" + availabilityId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dayOfWeek").value("TERCA"))
            .andExpect(jsonPath("$.startTime").value("08:00")) // Não alterado
            .andExpect(jsonPath("$.endTime").value("12:00")); // Não alterado
    }

    @Test
    void deveRetornar400QuandoAtualizarInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();
        String requestBody = "{\"dayOfWeek\":\"TERCA\"}";

        mockMvc.perform(put("/availability/" + nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /availability/{id} ====================

    @Test
    void deveInativarDisponibilidadeERetornar204() throws Exception {
        // Cria uma disponibilidade
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        String availabilityId = availability.getId().getValue().toString();

        // Verifica que está ativo
        assertTrue(availabilityApplicationService.buscarPorId(availability.getId()).orElseThrow().isAtivo());

        // Testa DELETE /availability/{id}
        mockMvc.perform(delete("/availability/" + availabilityId))
            .andExpect(status().isNoContent());

        // Verifica que foi inativado (soft delete)
        Availability availabilityInativado = availabilityApplicationService.buscarPorId(availability.getId()).orElseThrow();
        assertFalse(availabilityInativado.isAtivo());
        assertEquals(AvailabilityStatus.INATIVO, availabilityInativado.getStatus());
    }

    @Test
    void deveRetornar400QuandoDeletarInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/availability/" + nonExistentId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoIdInvalidoNoDelete() throws Exception {
        mockMvc.perform(delete("/availability/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }
}