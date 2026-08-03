package com.troquim_bot.conversation.query;

import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.schedule.ScheduleService;
import com.troquim_bot.support.TestTenants;
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

    /**
     * FLAKE DE RELÓGIO CORRIGIDO (pré-existente, sem relação com o catálogo persistido).
     *
     * A versão anterior perguntava sempre por "segunda". Quando a suíte roda numa segunda
     * à tarde, o dia consultado é HOJE, e {@code AvailabilityApplicationService} descarta
     * horário que já passou — a grade de segunda termina às 17:00, então depois disso a
     * lista vinha vazia e o teste falhava por hora do dia, não por regressão.
     *
     * A correção escolhe um dia da semana que nunca é hoje, mantendo a intenção original:
     * havendo dia e serviço, o responder lista horários.
     */
    @Test
    void responderConsultaDisponibilidade_quandoTemDiaEServicoListaHorarios() {
        BookingQueryResponder responder = criarResponder(new ScheduleService());
        ConversationState state = new ConversationState("5511999999999");
        String dia = diaUtilQueNaoEhHoje();

        Optional<String> resposta = responder.responderConsultaDisponibilidade(
                "Tem horário para unha " + dia + "?",
                state
        );

        assertTrue(resposta.isPresent());
        assertTrue(resposta.get().startsWith("Tenho horários para unha na " + dia + ":"),
                "resposta inesperada: " + resposta.get());
    }

    /**
     * Dia útil determinístico e diferente de hoje, para a consulta cair sempre numa data
     * futura — onde o filtro de horário já passado não se aplica.
     */
    private String diaUtilQueNaoEhHoje() {
        return java.time.LocalDate.now().getDayOfWeek() == java.time.DayOfWeek.TUESDAY
                ? "quarta"
                : "terça";
    }

    private BookingQueryResponder criarResponder(ScheduleService scheduleService) {
        return new BookingQueryResponder(
                new AppointmentApplicationService(
                        new InMemoryAppointmentRepository(),
                        new InMemoryReservationRepository()
                ),
                new AvailabilityApplicationService(TestTenants.pilot(),
                        new InMemoryAvailabilityRepository(),
                        scheduleService
                ),
                new CustomerProfileService(new InMemoryCustomerRepository(), TestTenants.pilot())
        );
    }
}
