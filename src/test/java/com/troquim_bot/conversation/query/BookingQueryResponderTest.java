package com.troquim_bot.conversation.query;

import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.schedule.ScheduleService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingQueryResponderTest {

    @Test
    void responderConsultaAgendamento_quandoNaoExisteAppointmentRetornaMensagemPadrao() {
        BookingQueryResponder responder = criarResponder(new ScheduleService());
        ConversationState state = new ConversationState("5511999999999");

        Optional<String> resposta = responder.responderConsultaAgendamento(
                "5511999999999",
                IntentType.CONSULTAR_AGENDAMENTO,
                "Meu agendamento",
                state
        );

        assertTrue(resposta.isPresent());
        assertEquals("Você ainda não tem uma solicitação de agendamento registrada.", resposta.get());
    }

    @Test
    void responderConsultaDisponibilidade_quandoTemDiaEServicoListaHorarios() {
        BookingQueryResponder responder = criarResponder(new ScheduleService());
        ConversationState state = new ConversationState("5511999999999");

        Optional<String> resposta = responder.responderConsultaDisponibilidade(
                "Tem horário para unha segunda?",
                state
        );

        assertTrue(resposta.isPresent());
        assertTrue(resposta.get().startsWith("Tenho horários para unha na segunda:"));
    }

    private BookingQueryResponder criarResponder(ScheduleService scheduleService) {
        return new BookingQueryResponder(
                new AppointmentApplicationService(
                        new InMemoryAppointmentRepository(),
                        new InMemoryReservationRepository()
                ),
                new AvailabilityApplicationService(
                        new InMemoryAvailabilityRepository(),
                        scheduleService
                )
        );
    }
}
