package com.troquim_bot.application.conversation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ConversationInputMapperTest {

    private final ConversationInputMapper mapper = new ConversationInputMapper();

    // ========== customerId tests ==========

    @Test
    void deveNormalizarCustomerIdComUuidValido() {
        String result = mapper.customerId("123e4567-e89b-12d3-a456-426614174000");

        assertEquals("123e4567-e89b-12d3-a456-426614174000", result);
    }

    @Test
    void deveNormalizarCustomerIdComUuidComEspacos() {
        String result = mapper.customerId("  123e4567-e89b-12d3-a456-426614174000  ");

        assertEquals("123e4567-e89b-12d3-a456-426614174000", result);
    }

    @Test
    void deveRejeitarCustomerIdNulo() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.customerId(null)
        );

        assertEquals("CustomerId e obrigatorio", exception.getMessage());
    }

    @Test
    void deveRejeitarCustomerIdVazio() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.customerId("")
        );

        assertEquals("CustomerId e obrigatorio", exception.getMessage());
    }

    @Test
    void deveRejeitarCustomerIdComEspacosApenas() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.customerId("   ")
        );

        assertEquals("CustomerId e obrigatorio", exception.getMessage());
    }

    @Test
    void deveRejeitarCustomerIdsComFormatoInvalido() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.customerId("not-a-uuid")
        );

        assertTrue(exception.getMessage().contains("CustomerId deve ser um UUID valido"));
    }

    // ========== update - normalizeOptionalUuid tests ==========

    @Test
    void deveNormalizarUpdateComTodosOsCamposPreenchidos() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            "123e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174001",
            "2025-01-15",
            "10:30",
            "11:30",
            "123e4567-e89b-12d3-a456-426614174002",
            "123e4567-e89b-12d3-a456-426614174003"
        );

        assertEquals("123e4567-e89b-12d3-a456-426614174000", update.selectedServiceId());
        assertEquals("123e4567-e89b-12d3-a456-426614174001", update.selectedProfessionalId());
        assertEquals("123e4567-e89b-12d3-a456-426614174002", update.reservationId());
        assertEquals("123e4567-e89b-12d3-a456-426614174003", update.appointmentId());
    }

    @Test
    void deveNormalizarUpdateComUuidsComEspacos() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            " 123e4567-e89b-12d3-a456-426614174000 ",
            " 123e4567-e89b-12d3-a456-426614174001 ",
            null,
            null,
            null,
            " 123e4567-e89b-12d3-a456-426614174002 ",
            " 123e4567-e89b-12d3-a456-426614174003 "
        );

        assertEquals("123e4567-e89b-12d3-a456-426614174000", update.selectedServiceId());
        assertEquals("123e4567-e89b-12d3-a456-426614174001", update.selectedProfessionalId());
        assertEquals("123e4567-e89b-12d3-a456-426614174002", update.reservationId());
        assertEquals("123e4567-e89b-12d3-a456-426614174003", update.appointmentId());
    }

    @Test
    void deveRetornarNullParaUuidsOpcionaisNulos() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertNull(update.selectedServiceId());
        assertNull(update.selectedProfessionalId());
        assertNull(update.reservationId());
        assertNull(update.appointmentId());
    }

    @Test
    void deveRetornarStringVaziaParaUuidsOpcionaisVazios() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            "",
            "",
            null,
            null,
            null,
            "",
            ""
        );

        assertEquals("", update.selectedServiceId());
        assertEquals("", update.selectedProfessionalId());
        assertEquals("", update.reservationId());
        assertEquals("", update.appointmentId());
    }

    @Test
    void deveRejeitarSelectedServiceIdComFormatoInvalido() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.update(
                "not-a-uuid",
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        assertTrue(exception.getMessage().contains("SelectedServiceId deve ser um UUID valido"));
    }

    @Test
    void deveRejeitarSelectedProfessionalIdComFormatoInvalido() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.update(
                null,
                "not-a-uuid",
                null,
                null,
                null,
                null,
                null
            )
        );

        assertTrue(exception.getMessage().contains("SelectedProfessionalId deve ser um UUID valido"));
    }

    @Test
    void deveRejeitarReservationIdComFormatoInvalido() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.update(
                null,
                null,
                null,
                null,
                null,
                "not-a-uuid",
                null
            )
        );

        assertTrue(exception.getMessage().contains("ReservationId deve ser um UUID valido"));
    }

    @Test
    void deveRejeitarAppointmentIdComFormatoInvalido() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.update(
                null,
                null,
                null,
                null,
                null,
                null,
                "not-a-uuid"
            )
        );

        assertTrue(exception.getMessage().contains("AppointmentId deve ser um UUID valido"));
    }

    // ========== update - parseOptionalDate tests ==========

    @Test
    void deveParsearDataValida() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            null,
            null,
            "2025-01-15",
            null,
            null,
            null,
            null
        );

        assertEquals(LocalDate.of(2025, 1, 15), update.selectedDate());
    }

    @Test
    void deveParsearDataComEspacos() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            null,
            null,
            " 2025-01-15 ",
            null,
            null,
            null,
            null
        );

        assertEquals(LocalDate.of(2025, 1, 15), update.selectedDate());
    }

    @Test
    void deveRetornarNullParaDataNula() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertNull(update.selectedDate());
    }

    @Test
    void deveRejeitarDataComFormatoInvalido() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.update(
                null,
                null,
                "15-01-2025",
                null,
                null,
                null,
                null
            )
        );

        assertTrue(exception.getMessage().contains("SelectedDate deve estar no formato yyyy-MM-dd"));
    }

    @Test
    void deveRejeitarDataComFormatoInvalido2() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.update(
                null,
                null,
                "2025/01/15",
                null,
                null,
                null,
                null
            )
        );

        assertTrue(exception.getMessage().contains("SelectedDate deve estar no formato yyyy-MM-dd"));
    }

    // ========== update - parseOptionalTime tests ==========

    @Test
    void deveParsearHorarioValido() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            null,
            null,
            null,
            "10:30",
            "11:30",
            null,
            null
        );

        assertEquals(LocalTime.of(10, 30), update.selectedStartTime());
        assertEquals(LocalTime.of(11, 30), update.selectedEndTime());
    }

    @Test
    void deveParsearHorarioComEspacos() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            null,
            null,
            null,
            " 10:30 ",
            " 11:30 ",
            null,
            null
        );

        assertEquals(LocalTime.of(10, 30), update.selectedStartTime());
        assertEquals(LocalTime.of(11, 30), update.selectedEndTime());
    }

    @Test
    void deveRetornarNullParaHorarioNulo() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertNull(update.selectedStartTime());
        assertNull(update.selectedEndTime());
    }

    @Test
    void deveRejeitarHorarioComFormatoInvalido() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.update(
                null,
                null,
                null,
                "10h30",
                null,
                null,
                null
            )
        );

        assertTrue(exception.getMessage().contains("SelectedStartTime deve estar no formato HH:mm"));
    }

    @Test
    void deveRejeitarHorarioComFormatoInvalido2() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> mapper.update(
                null,
                null,
                null,
                null,
                "10-30",
                null,
                null
            )
        );

        assertTrue(exception.getMessage().contains("SelectedEndTime deve estar no formato HH:mm"));
    }

    // ========== update - cenários combinados ==========

    @Test
    void deveValidarTodosOsCamposJuntos() {
        ConversationInputMapper.ConversationUpdate update = mapper.update(
            "123e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174001",
            "2025-12-31",
            "23:59",
            "00:00",
            "123e4567-e89b-12d3-a456-426614174002",
            "123e4567-e89b-12d3-a456-426614174003"
        );

        assertEquals("123e4567-e89b-12d3-a456-426614174000", update.selectedServiceId());
        assertEquals("123e4567-e89b-12d3-a456-426614174001", update.selectedProfessionalId());
        assertEquals(LocalDate.of(2025, 12, 31), update.selectedDate());
        assertEquals(LocalTime.of(23, 59), update.selectedStartTime());
        assertEquals(LocalTime.of(0, 0), update.selectedEndTime());
        assertEquals("123e4567-e89b-12d3-a456-426614174002", update.reservationId());
        assertEquals("123e4567-e89b-12d3-a456-426614174003", update.appointmentId());
    }
}