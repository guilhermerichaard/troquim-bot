package com.troquim_bot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AppointmentFromReservationIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private ReservationController reservationController;

    @Autowired
    private AppointmentController appointmentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(reservationController, appointmentController)
            .build();
    }

    @Test
    void deveCriarAppointmentAPartirDeReservationCriadaViaEndpoint() throws Exception {
        String customerId = UUID.randomUUID().toString();
        String professionalId = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();
        String availabilityId = UUID.randomUUID().toString();
        LocalDate date = LocalDate.now().plusDays(15);
        LocalTime startTime = LocalTime.of(14, 0);
        LocalTime endTime = LocalTime.of(15, 0);
        LocalDateTime expiresAt = LocalDateTime.of(date, LocalTime.of(23, 59));

        String reservationBody = "{"
            + "\"customerId\":\"" + customerId + "\","
            + "\"professionalId\":\"" + professionalId + "\","
            + "\"serviceId\":\"" + serviceId + "\","
            + "\"availabilityId\":\"" + availabilityId + "\","
            + "\"date\":\"" + date + "\","
            + "\"startTime\":\"" + startTime + "\","
            + "\"endTime\":\"" + endTime + "\","
            + "\"expiresAt\":\"" + expiresAt + "\""
            + "}";

        MvcResult reservationResult = mockMvc.perform(post("/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ATIVO"))
            .andReturn();

        JsonNode reservationJson = objectMapper.readTree(reservationResult.getResponse().getContentAsString());
        String reservationId = reservationJson.get("id").asText();

        mockMvc.perform(post("/appointments/from-reservation/" + reservationId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customerId").value(customerId))
            .andExpect(jsonPath("$.professionalId").value(professionalId))
            .andExpect(jsonPath("$.serviceId").value(serviceId))
            .andExpect(jsonPath("$.availabilityId").value(availabilityId))
            .andExpect(jsonPath("$.reservationId").value(reservationId))
            .andExpect(jsonPath("$.status").value("PENDENTE"));

        mockMvc.perform(get("/reservations/" + reservationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELADO"));
    }
}
