package com.troquim_bot.application.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationInteractivePresentationTest {

    @Test
    void menuPrincipalViraBotoesSemDuplicarNumerosNoCorpo() {
        var presentation = ConversationInteractivePresentation.from(
                "Ola! Escolha uma opcao:\n\n"
                + "1) Agendar\n"
                + "2) Meus agendamentos\n"
                + "3) Cancelar").orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, presentation.type());
        assertEquals(3, presentation.options().size());
        assertFalse(presentation.text().contains("1) Agendar"));
        assertTrue(presentation.text().contains("Escolha uma opcao"));
    }

    @Test
    void menuDeAcoesNaoApagaAgendamentoNumeradoDoCorpo() {
        var presentation = ConversationInteractivePresentation.from(
                "Seus agendamentos ativos:\n\n"
                + "1) Manicure em 23/09/2026 as 9:15 - CONFIRMADO\n\n"
                + "Deseja fazer algo mais?\n\n"
                + "1) Agendar\n"
                + "2) Meus agendamentos\n"
                + "3) Cancelar").orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, presentation.type());
        assertTrue(presentation.text().contains(
                "1) Manicure em 23/09/2026 as 9:15 - CONFIRMADO"));
        assertFalse(presentation.text().contains("1) Agendar"));
    }

    @Test
    void listaDeDiasRemoveNumerosEInstrucaoDeDigitacaoDoCorpo() {
        var presentation = ConversationInteractivePresentation.from(
                "Perfeito! Para qual dia voce gostaria?\n\n"
                + "1) Segunda\n"
                + "2) Terca\n"
                + "3) Quarta\n"
                + "4) Quinta\n"
                + "5) Sexta\n"
                + "6) Sabado\n\n"
                + "Digite o numero ou o nome do dia:").orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.LIST, presentation.type());
        assertEquals(6, presentation.options().size());
        assertEquals("Perfeito! Para qual dia voce gostaria?", presentation.text());
    }
}
