package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.CreateReservationRequest;
import com.troquim_bot.controller.dto.UpdateReservationRequest;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;
import com.troquim_bot.reservation.ReservationStatus;
import com.troquim_bot.service.ServiceId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ReservationControllerTest {

    private MockMvc mockMvc;
    private ReservationApplicationService reservationApplicationService;
    private InMemoryReservationRepository reservationRepository;

    private final CustomerId customerId1 = CustomerId.from(UUID.randomUUID());
    private final ProfessionalId profId1 = ProfessionalId.from(UUID.randomUUID());
    private final ServiceId serviceId1 = ServiceId.from(UUID.randomUUID());
    private final AvailabilityId availabilityId1 = AvailabilityId.from(UUID.randomUUID());

    private final LocalDate data = LocalDate.of(2026, 7, 10);
    private final LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 10, 23, 59);

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        reservationApplicationService = new ReservationApplicationService(reservationRepository);
        ReservationController reservationController = new ReservationController(reservationApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(reservationController).build();
    }

    // ==================== GET /reservations ====================

    @Test
    void deveRetornar200QuandoListarTodos() throws Exception {
        // Cria algumas reservas
        reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);
        reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(10, 0), LocalTime.of(11, 0), expiresAt);

        // Testa GET /reservations
        mockMvc.perform(get("/reservations"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistem() throws Exception {
        mockMvc.perform(get("/reservations"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ==================== GET /reservations/{id} ====================

    @Test
    void deveRetornar200QuandoBuscarPorIdExistente() throws Exception {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);
        String reservationId = reservation.getId().getValue().toString();

        mockMvc.perform(get("/reservations/" + reservationId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(reservationId))
            .andExpect(jsonPath("$.customerId").value(customerId1.getValue().toString()))
            .andExpect(jsonPath("$.professionalId").value(profId1.getValue().toString()))
            .andExpect(jsonPath("$.serviceId").value(serviceId1.getValue().toString()))
            .andExpect(jsonPath("$.availabilityId").value(availabilityId1.getValue().toString()))
            .andExpect(jsonPath("$.date").value("2026-07-10"))
            .andExpect(jsonPath("$.startTime").value("08:00"))
            .andExpect(jsonPath("$.endTime").value("09:00"))
            .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    void deveRetornar404QuandoBuscarPorIdInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(get("/reservations/" + nonExistentId))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400QuandoIdInvalido() throws Exception {
        mockMvc.perform(get("/reservations/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }

    // ==================== POST /reservations ====================

    @Test
    void deveCriarReservaERetornar201() throws Exception {
        String requestBody = "{" +
            "\"customerId\":\"" + customerId1.getValue().toString() + "\"," +
            "\"professionalId\":\"" + profId1.getValue().toString() + "\"," +
            "\"serviceId\":\"" + serviceId1.getValue().toString() + "\"," +
            "\"availabilityId\":\"" + availabilityId1.getValue().toString() + "\"," +
            "\"date\":\"2026-07-10\"," +
            "\"startTime\":\"08:00\"," +
            "\"endTime\":\"09:00\"," +
            "\"expiresAt\":\"2026-07-10T23:59:00\"" +
            "}";

        mockMvc.perform(post("/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.customerId").value(customerId1.getValue().toString()))
            .andExpect(jsonPath("$.professionalId").value(profId1.getValue().toString()))
            .andExpect(jsonPath("$.serviceId").value(serviceId1.getValue().toString()))
            .andExpect(jsonPath("$.availabilityId").value(availabilityId1.getValue().toString()))
            .andExpect(jsonPath("$.date").value("2026-07-10"))
            .andExpect(jsonPath("$.startTime").value("08:00"))
            .andExpect(jsonPath("$.endTime").value("09:00"))
            .andExpect(jsonPath("$.status").value("ATIVO"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.criadoEm").exists())
            .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    void deveRetornar400QuandoRequestNull() throws Exception {
        mockMvc.perform(post("/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoCustomerIdInvalido() throws Exception {
        String requestBody = "{" +
            "\"customerId\":\"invalid-uuid\"," +
            "\"professionalId\":\"" + profId1.getValue().toString() + "\"," +
            "\"serviceId\":\"" + serviceId1.getValue().toString() + "\"," +
            "\"availabilityId\":\"" + availabilityId1.getValue().toString() + "\"," +
            "\"date\":\"2026-07-10\"," +
            "\"startTime\":\"08:00\"," +
            "\"endTime\":\"09:00\"," +
            "\"expiresAt\":\"2026-07-10T23:59:00\"" +
            "}";

        mockMvc.perform(post("/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    // ==================== PUT /reservations/{id} ====================

    @Test
    void deveAtualizarData() throws Exception {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);
        String reservationId = reservation.getId().getValue().toString();

        String requestBody = "{\"date\":\"2026-07-11\"}";

        mockMvc.perform(put("/reservations/" + reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.date").value("2026-07-11"))
            .andExpect(jsonPath("$.startTime").value("08:00")) // Não alterado
            .andExpect(jsonPath("$.endTime").value("09:00")); // Não alterado
    }

    @Test
    void deveRetornar400QuandoAtualizarInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();
        String requestBody = "{\"date\":\"2026-07-11\"}";

        mockMvc.perform(put("/reservations/" + nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /reservations/{id} ====================

    @Test
    void deveCancelarReservaERetornar204() throws Exception {
        Reservation reservation = reservationApplicationService.criarReserva(
            customerId1, profId1, serviceId1, availabilityId1,
            data, LocalTime.of(8, 0), LocalTime.of(9, 0), expiresAt);
        String reservationId = reservation.getId().getValue().toString();

        assertTrue(reservationApplicationService.buscarPorId(reservation.getId()).orElseThrow().isAtivo());

        mockMvc.perform(delete("/reservations/" + reservationId))
            .andExpect(status().isNoContent());

        Reservation reservationCancelada = reservationApplicationService.buscarPorId(reservation.getId()).orElseThrow();
        assertFalse(reservationCancelada.isAtivo());
        assertEquals(ReservationStatus.CANCELADO, reservationCancelada.getStatus());
    }

    @Test
    void deveRetornar400QuandoDeletarInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/reservations/" + nonExistentId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoIdInvalidoNoDelete() throws Exception {
        mockMvc.perform(delete("/reservations/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }
}