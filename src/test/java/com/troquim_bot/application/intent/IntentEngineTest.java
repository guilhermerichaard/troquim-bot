package com.troquim_bot.application.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentEngineTest {

    private IntentEngine intentEngine;

    @BeforeEach
    void setUp() {
        intentEngine = new RuleBasedIntentEngine();
    }

    @Test
    void deveClassificarGreeting() {
        assertIntent("oi", IntentType.GREETING);
        assertIntent("olá", IntentType.GREETING);
        assertIntent("bom dia", IntentType.GREETING);
        assertIntent("boa tarde", IntentType.GREETING);
        assertIntent("boa noite", IntentType.GREETING);
    }

    @Test
    void deveClassificarBookAppointment() {
        assertIntent("quero agendar", IntentType.BOOK_APPOINTMENT);
        assertIntent("quero marcar horário", IntentType.BOOK_APPOINTMENT);
        assertIntent("agendar horário", IntentType.BOOK_APPOINTMENT);
        assertIntent("marcar um horário", IntentType.BOOK_APPOINTMENT);
        assertIntent("preciso agendar", IntentType.BOOK_APPOINTMENT);
    }

    @Test
    void deveClassificarCancelAppointment() {
        assertIntent("quero cancelar", IntentType.CANCEL_APPOINTMENT);
        assertIntent("preciso desmarcar", IntentType.CANCEL_APPOINTMENT);
    }

    @Test
    void deveClassificarRescheduleAppointment() {
        assertIntent("quero remarcar", IntentType.RESCHEDULE_APPOINTMENT);
        assertIntent("preciso trocar horário", IntentType.RESCHEDULE_APPOINTMENT);
    }

    @Test
    void deveClassificarCheckAvailability() {
        assertIntent("tem horário hoje?", IntentType.CHECK_AVAILABILITY);
        assertIntent("está disponível amanhã?", IntentType.CHECK_AVAILABILITY);
        assertIntent("qual a disponibilidade?", IntentType.CHECK_AVAILABILITY);
    }

    @Test
    void deveClassificarAskServices() {
        assertIntent("quais serviços vocês têm?", IntentType.ASK_SERVICES);
        assertIntent("quais os preços?", IntentType.ASK_SERVICES);
        assertIntent("qual o valor?", IntentType.ASK_SERVICES);
    }

    @Test
    void deveClassificarHumanAttendant() {
        assertIntent("quero falar com atendente", IntentType.HUMAN_ATTENDANT);
        assertIntent("preciso de um humano", IntentType.HUMAN_ATTENDANT);
        assertIntent("quero falar com uma pessoa", IntentType.HUMAN_ATTENDANT);
    }

    @Test
    void deveClassificarAgradecimento() {
        assertIntent("obrigado", IntentType.AGRADECIMENTO);
        assertIntent("obrigada", IntentType.AGRADECIMENTO);
        assertIntent("agradeço", IntentType.AGRADECIMENTO);
        assertIntent("valeu", IntentType.AGRADECIMENTO);
    }

    @Test
    void deveClassificarDespedida() {
        assertIntent("tchau", IntentType.DESPEDIDA);
        assertIntent("até logo", IntentType.DESPEDIDA);
        assertIntent("até mais", IntentType.DESPEDIDA);
        assertIntent("adeus", IntentType.DESPEDIDA);
        assertIntent("falou", IntentType.DESPEDIDA);
    }

    @Test
    void deveClassificarLembrarCliente() {
        assertIntent("lembra de mim", IntentType.LEMBRAR_CLIENTE);
        assertIntent("você lembra de mim", IntentType.LEMBRAR_CLIENTE);
        assertIntent("me conhece", IntentType.LEMBRAR_CLIENTE);
        assertIntent("sabe quem eu sou", IntentType.LEMBRAR_CLIENTE);
    }

    @Test
    void deveClassificarConsultarAgendamento() {
        assertIntent("qual meu agendamento", IntentType.CONSULTAR_AGENDAMENTO);
        assertIntent("qual agendamento", IntentType.CONSULTAR_AGENDAMENTO);
        assertIntent("agendamentos pendentes", IntentType.CONSULTAR_AGENDAMENTO);
        assertIntent("agendou mesmo", IntentType.CONSULTAR_AGENDAMENTO);
        assertIntent("quero ver meu agendamento", IntentType.CONSULTAR_AGENDAMENTO);
    }

    @Test
    void deveClassificarConsultarDiaAgendado() {
        assertIntent("que dia agendei", IntentType.CONSULTAR_DIA_AGENDADO);
        assertIntent("qual dia agendei", IntentType.CONSULTAR_DIA_AGENDADO);
        assertIntent("marquei para quando", IntentType.CONSULTAR_DIA_AGENDADO);
        assertIntent("agendei para quando", IntentType.CONSULTAR_DIA_AGENDADO);
    }

    @Test
    void deveClassificarConsultarHorarioAgendado() {
        assertIntent("qual horário", IntentType.CONSULTAR_HORARIO_AGENDADO);
        assertIntent("que horário", IntentType.CONSULTAR_HORARIO_AGENDADO);
    }

    @Test
    void deveClassificarConsultarServicoAgendado() {
        assertIntent("qual serviço", IntentType.CONSULTAR_SERVICO_AGENDADO);
        assertIntent("que serviço", IntentType.CONSULTAR_SERVICO_AGENDADO);
    }

    @Test
    void deveClassificarConsultarNome() {
        assertIntent("qual meu nome", IntentType.CONSULTAR_NOME);
        assertIntent("meu nome", IntentType.CONSULTAR_NOME);
        assertIntent("meu nome é", IntentType.CONSULTAR_NOME);
        assertIntent("sabe meu nome", IntentType.CONSULTAR_NOME);
    }

    @Test
    void deveClassificarUnknown() {
        assertIntent("mensagem sem intencao conhecida", IntentType.UNKNOWN);
    }

    private void assertIntent(String message, IntentType expectedType) {
        assertEquals(expectedType, intentEngine.classify(message).type());
    }
}