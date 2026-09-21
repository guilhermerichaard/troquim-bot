package com.troquim_bot.application.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationInteractivePresentationTest {

    @Test
    void menuPrincipalViraBotoesSemDuplicarNumerosNoCorpo() {
        var presentation = ConversationInteractivePresentation.from(
                "Olá! Escolha uma opção:\n\n"
                + "1) Agendar\n"
                + "2) Meus agendamentos\n"
                + "3) Cancelar").orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, presentation.type());
        assertEquals(3, presentation.options().size());
        assertFalse(presentation.text().contains("1) Agendar"));
        assertTrue(presentation.text().contains("Escolha uma opção"));
    }

    @Test
    void menuDeAcoesNaoApagaAgendamentoNumeradoDoCorpo() {
        var presentation = ConversationInteractivePresentation.from(
                "Seus agendamentos ativos:\n\n"
                + "1) Manicure em 23/09/2026 às 9:15 - CONFIRMADO\n\n"
                + "Deseja fazer algo mais?\n\n"
                + "1) Agendar\n"
                + "2) Meus agendamentos\n"
                + "3) Cancelar").orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, presentation.type());
        assertTrue(presentation.text().contains(
                "1) Manicure em 23/09/2026 às 9:15 - CONFIRMADO"));
        assertFalse(presentation.text().contains("1) Agendar"));
    }

    @Test
    void listaDeDiasIncluiVoltarECorpoFicaLimpo() {
        var presentation = ConversationInteractivePresentation.from(
                "Perfeito! Para qual dia você gostaria?\n\n"
                + "1) Segunda\n"
                + "2) Terça\n"
                + "3) Quarta\n"
                + "4) Quinta\n"
                + "5) Sexta\n"
                + "6) Sábado\n"
                + "[[choice:nav_voltar|← Voltar]]").orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.LIST, presentation.type());
        assertEquals(7, presentation.options().size());
        assertEquals("nav_voltar", presentation.options().get(6).id());
        assertEquals("Perfeito! Para qual dia você gostaria?", presentation.text());
    }

    @Test
    void paginaDeHorariosIncluiNavegacaoClicavelSemVazarMarcadores() {
        var presentation = ConversationInteractivePresentation.from(
                "Horários disponíveis para sábado:\n\n"
                + "1) 9h\n"
                + "2) 9:15\n"
                + "3) 9:30\n"
                + "4) 9:45\n"
                + "5) 10h\n"
                + "6) 10:15\n"
                + "7) 10:30\n"
                + "[[choice:horarios_pagina_1|Mais horários →]]\n"
                + "[[choice:nav_voltar|← Voltar]]").orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.LIST, presentation.type());
        assertEquals(9, presentation.options().size());
        assertEquals("horarios_pagina_1", presentation.options().get(7).id());
        assertEquals("nav_voltar", presentation.options().get(8).id());
        assertFalse(presentation.text().contains("[[choice:"));
    }

    @Test
    void sugestaoDeCorrecaoUsaSimNaoEVoltar() {
        var presentation = ConversationInteractivePresentation.from(
                "Você quis dizer Manicure?\n\n"
                + "1) Sim\n"
                + "2) Não").orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, presentation.type());
        assertEquals("Sim", presentation.options().get(0).title());
        assertEquals("Não", presentation.options().get(1).title());
        assertEquals("Voltar", presentation.options().get(2).title());
        assertEquals("voltar",
                ConversationInteractivePresentation.canonicalInput("nav_voltar"));
    }

    @Test
    void somenteVoltarAindaRenderizaBotao() {
        var presentation = ConversationInteractivePresentation.from(
                "Não consegui consultar os horários agora.\n\n"
                + "[[choice:nav_voltar|← Voltar]]").orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, presentation.type());
        assertEquals(1, presentation.options().size());
        assertEquals("nav_voltar", presentation.options().get(0).id());
    }
}
