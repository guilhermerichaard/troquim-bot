package com.troquim_bot.application.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentEngineEdgeCasesTest {

    private IntentEngine intentEngine;

    @BeforeEach
    void setUp() {
        intentEngine = new RuleBasedIntentEngine();
    }

    @Test
    void deveClassificarEntradaNulaVaziaOuComEspacosComoUnknown() {
        assertIntent(null, IntentType.UNKNOWN);
        assertIntent("", IntentType.UNKNOWN);
        assertIntent("   ", IntentType.UNKNOWN);
    }

    @Test
    void deveIgnorarMaiusculasMinusculasEAcentos() {
        assertIntent("OLÁ", IntentType.GREETING);
        assertIntent("DISPONÍVEL", IntentType.CHECK_AVAILABILITY);
        assertIntent("PREÇOS", IntentType.ASK_SERVICES);
    }

    @Test
    void devePriorizarCancelamentoSobreHorarioGenerico() {
        assertIntent("quero cancelar meu horário", IntentType.CANCEL_APPOINTMENT);
        assertIntent("preciso desmarcar o horário", IntentType.CANCEL_APPOINTMENT);
    }

    @Test
    void devePriorizarRemarcacaoSobreHorarioGenerico() {
        assertIntent("quero remarcar meu horário", IntentType.RESCHEDULE_APPOINTMENT);
        assertIntent("quero trocar horário amanhã", IntentType.RESCHEDULE_APPOINTMENT);
    }

    @Test
    void devePriorizarDisponibilidadeSobreAgendamentoGenerico() {
        assertIntent("tem horário para marcar?", IntentType.CHECK_AVAILABILITY);
    }

    @Test
    void devePriorizarAtendenteSobreServicosEAgendamento() {
        assertIntent("quero falar com atendente sobre serviços", IntentType.HUMAN_ATTENDANT);
        assertIntent("quero uma pessoa para agendar", IntentType.HUMAN_ATTENDANT);
    }

    @Test
    void devePriorizarAcaoSobreSaudacao() {
        assertIntent("oi, quero agendar", IntentType.BOOK_APPOINTMENT);
    }

    @Test
    void deveNaoClassificarHorarioIsoladoComoBookAppointment() {
        assertIntent("que horário é?", IntentType.CONSULTAR_HORARIO_AGENDADO);
        assertIntent("preciso saber o horário", IntentType.CONSULTAR_HORARIO_AGENDADO);
        assertIntent("horário", IntentType.UNKNOWN);
    }

    @Test
    void deveManterCheckAvailabilityParaHorariosDisponiveis() {
        assertIntent("tem horário?", IntentType.CHECK_AVAILABILITY);
        assertIntent("horários disponíveis", IntentType.CHECK_AVAILABILITY);
    }

    @Test
    void deveNaoClassificarMarcaPresencaComoBookAppointment() {
        assertIntent("quero marcar presença", IntentType.UNKNOWN);
    }

    private void assertIntent(String message, IntentType expectedType) {
        assertEquals(expectedType, intentEngine.classify(message).type());
    }
}