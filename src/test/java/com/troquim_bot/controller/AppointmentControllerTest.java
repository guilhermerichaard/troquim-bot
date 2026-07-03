package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.CreateAppointmentRequest;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.appointment.AppointmentStatus;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;
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

class AppointmentControllerTest {

    private MockMvc mockMvc;
    private AppointmentApplicationService appointmentApplicationService;
    private InMemoryAppointmentRepository appointmentRepository;
    private InMemoryReservationRepository reservationRepository;

    private final CustomerId customerId1 = CustomerId.from(UUID.randomUUID());
    private final ProfessionalId profId1 = ProfessionalId.from(UUID.randomUUID());
    private final ServiceId serviceId1 = ServiceId.from(UUID.randomUUID());
    private final AvailabilityId availabilityId1 = AvailabilityId.from(UUID.randomUUID());

    private final LocalDate futureDate = LocalDate.now().plusDays(10);

    @BeforeEach
    void setUp() {
        appointmentRepository = new InMemoryAppointmentRepository();
        reservationRepository = new InMemoryReservationRepository();
        appointmentApplicationService = new AppointmentApplicationService(appointmentRepository, reservationRepository);
        AppointmentController appointmentController = new AppointmentController(appointmentApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(appointmentController).build();
    }

    // ==================== GET /appointments ====================

    @Test
    void deveRetornar200QuandoListarTodos() throws Exception {
        appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(10, 0), LocalTime.of(11, 0));

        mockMvc.perform(get("/appointments"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistem() throws Exception {
        mockMvc.perform(get("/appointments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ==================== GET /appointments/{id} ====================

    @Test
    void deveRetornar200QuandoBuscarPorIdExistente() throws Exception {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        String appointmentId = appointment.getId().getValue().toString();

        mockMvc.perform(get("/appointments/" + appointmentId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(appointmentId))
            .andExpect(jsonPath("$.customerId").value(customerId1.getValue().toString()))
            .andExpect(jsonPath("$.professionalId").value(profId1.getValue().toString()))
            .andExpect(jsonPath("$.serviceId").value(serviceId1.getValue().toString()))
            .andExpect(jsonPath("$.availabilityId").value(availabilityId1.getValue().toString()))
            .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void deveRetornar404QuandoBuscarPorIdInexistente() throws Exception {
        mockMvc.perform(get("/appointments/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400QuandoIdInvalido() throws Exception {
        mockMvc.perform(get("/appointments/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }

    // ==================== POST /appointments ====================

    @Test
    void deveCriarAgendamentoERetornar201() throws Exception {
        String requestBody = "{" +
            "\"customerId\":\"" + customerId1.getValue().toString() + "\"," +
            "\"professionalId\":\"" + profId1.getValue().toString() + "\"," +
            "\"serviceId\":\"" + serviceId1.getValue().toString() + "\"," +
            "\"availabilityId\":\"" + availabilityId1.getValue().toString() + "\"," +
            "\"date\":\"" + futureDate.toString() + "\"," +
            "\"startTime\":\"08:00\"," +
            "\"endTime\":\"09:00\"" +
            "}";

        mockMvc.perform(post("/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.customerId").value(customerId1.getValue().toString()))
            .andExpect(jsonPath("$.professionalId").value(profId1.getValue().toString()))
            .andExpect(jsonPath("$.serviceId").value(serviceId1.getValue().toString()))
            .andExpect(jsonPath("$.availabilityId").value(availabilityId1.getValue().toString()))
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.criadoEm").exists())
            .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    void deveRetornar400QuandoRequestNull() throws Exception {
        mockMvc.perform(post("/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    // ==================== POST /appointments/from-reservation/{reservationId} ====================

    @Test
    void deveCriarAgendamentoDeReservaERetornar201() throws Exception {
        Reservation reservation = new Reservation(
            ReservationId.generate(), customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0),
            LocalDateTime.of(futureDate, LocalTime.of(23, 59)));
        reservationRepository.save(reservation);

        mockMvc.perform(post("/appointments/from-reservation/" + reservation.getId().getValue().toString()))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.customerId").value(customerId1.getValue().toString()))
            .andExpect(jsonPath("$.professionalId").value(profId1.getValue().toString()))
            .andExpect(jsonPath("$.serviceId").value(serviceId1.getValue().toString()))
            .andExpect(jsonPath("$.availabilityId").value(availabilityId1.getValue().toString()))
            .andExpect(jsonPath("$.reservationId").value(reservation.getId().getValue().toString()))
            .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void deveRetornar400QuandoReservationIdInvalido() throws Exception {
        mockMvc.perform(post("/appointments/from-reservation/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }

    // ==================== PUT /appointments/{id}/confirm ====================

    @Test
    void deveConfirmarAgendamento() throws Exception {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));

        mockMvc.perform(put("/appointments/" + appointment.getId().getValue().toString() + "/confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMADO"));
    }

    @Test
    void deveRetornar400QuandoConfirmarInexistente() throws Exception {
        mockMvc.perform(put("/appointments/" + UUID.randomUUID() + "/confirm"))
            .andExpect(status().isBadRequest());
    }

    // ==================== PUT /appointments/{id}/cancel ====================

    @Test
    void deveCancelarAgendamento() throws Exception {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));

        mockMvc.perform(put("/appointments/" + appointment.getId().getValue().toString() + "/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    // ==================== PUT /appointments/{id}/complete ====================

    @Test
    void deveConcluirAgendamento() throws Exception {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));
        appointmentApplicationService.confirmarAgendamento(appointment.getId());

        mockMvc.perform(put("/appointments/" + appointment.getId().getValue().toString() + "/complete"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONCLUIDO"));
    }

    // ==================== DELETE /appointments/{id} ====================

    @Test
    void deveCancelarAgendamentoERetornar204() throws Exception {
        Appointment appointment = appointmentApplicationService.criarAgendamento(
            customerId1, profId1, serviceId1, availabilityId1,
            futureDate, LocalTime.of(8, 0), LocalTime.of(9, 0));

        assertTrue(appointmentApplicationService.buscarPorId(appointment.getId()).orElseThrow().isAtivo());

        mockMvc.perform(delete("/appointments/" + appointment.getId().getValue().toString()))
            .andExpect(status().isNoContent());

        Appointment cancelado = appointmentApplicationService.buscarPorId(appointment.getId()).orElseThrow();
        assertFalse(cancelado.isAtivo());
        assertEquals(AppointmentStatus.CANCELADO, cancelado.getStatus());
    }

    @Test
    void deveRetornar400QuandoDeletarInexistente() throws Exception {
        mockMvc.perform(delete("/appointments/" + UUID.randomUUID()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoIdInvalidoNoDelete() throws Exception {
        mockMvc.perform(delete("/appointments/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }
}