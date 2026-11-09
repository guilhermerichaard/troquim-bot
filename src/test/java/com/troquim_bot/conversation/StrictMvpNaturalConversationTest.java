package com.troquim_bot.conversation;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.AberturaDeAgenda;
import com.troquim_bot.application.booking.AberturaDeAgendaResultado;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.conversation.BusinessConversationFacts;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.conversation.state.ConversationStep;
import com.troquim_bot.repository.InMemoryConversationStateRepository;
import com.troquim_bot.support.OptionalBeans;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrictMvpNaturalConversationTest {

    private static final String NUMERO = "5511999990000";

    @Test
    void digitacaoLivreNaoDisparaInterpretacaoDeServicoNemExibeHorarios() {
        var fixture = fixture();
        for (String entrada : List.of("MANI", "manicure", "quinta 10:30", "AKHA")) {
            String resposta = fixture.menu.processarMenu(
                    NUMERO, entrada, fixture.states.buscarPorNumero(NUMERO));
            assertTrue(resposta.contains("[[choice:agenda_visual|Abrir agenda]]"), resposta);
            assertFalse(resposta.contains("reformular"), resposta);
            assertFalse(resposta.contains("15:30"), resposta);
        }
        assertEquals(ConversationStep.INICIO, fixture.states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void agendarEnviaApenasOFlowSemSegundaMensagemRedundante() {
        AberturaDeAgenda agenda = mock(AberturaDeAgenda.class);
        when(agenda.disponivel()).thenReturn(true);
        when(agenda.abrirPara(NUMERO)).thenReturn(AberturaDeAgendaResultado.enviado());
        var fixture = fixture(agenda);

        String resposta = fixture.menu.processarMenu(
                NUMERO, "agendar", fixture.states.buscarPorNumero(NUMERO));

        assertEquals("", resposta);
        org.mockito.Mockito.verify(agenda).abrirPara(NUMERO);
        assertEquals(ConversationStep.INICIO, fixture.states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void falhaNoEnvioNaoMandaClienteDigitarServicoOuHorario() {
        AberturaDeAgenda agenda = mock(AberturaDeAgenda.class);
        when(agenda.disponivel()).thenReturn(true);
        when(agenda.abrirPara(NUMERO))
                .thenReturn(AberturaDeAgendaResultado.falhaNoEnvio("temporaria"));
        var fixture = fixture(agenda);

        String resposta = fixture.menu.processarMenu(
                NUMERO, "agendar", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Tente novamente"), resposta);
        assertFalse(resposta.contains("Escolher serviço"), resposta);
    }

    @Test
    void estadoLegadoDeServicoOuHorarioNaoReativaFormularioTextual() {
        var fixture = fixture();
        for (ConversationStep step : List.of(
                ConversationStep.AGUARDANDO_SERVICO,
                ConversationStep.AGUARDANDO_DIA,
                ConversationStep.AGUARDANDO_HORARIO)) {
            ConversationState state = fixture.states.buscarPorNumero(NUMERO);
            state.setStep(step);
            fixture.states.persistir(state);
            String resposta = fixture.menu.processarMenu(
                    NUMERO, "manicure", fixture.states.buscarPorNumero(NUMERO));
            assertTrue(resposta.contains("Abrir agenda"), resposta);
            assertFalse(resposta.contains("O que você quer fazer?"), resposta);
        }
    }

    @Test
    void naoDepoisDeFinalizadoEncerraSemFallbackGenerico() {
        var fixture = fixture();
        ConversationState state = fixture.states.buscarPorNumero(NUMERO);
        state.setStep(ConversationStep.FINALIZADO);
        fixture.states.persistir(state);

        String resposta = fixture.menu.processarMenu(
                NUMERO, "Não", fixture.states.buscarPorNumero(NUMERO));

        assertEquals("Tudo certo. Até mais!", resposta);
    }

    @Test
    void perguntaSobreServicosDirecionaParaAgendaSemListagemTextual() {
        var fixture = fixture();
        when(fixture.gateway.listarServicos()).thenReturn(List.of(
                new ConversationBookingGateway.Servico("Escova", "", 45, 80.0, "BRL"),
                new ConversationBookingGateway.Servico("Manicure", "", 40, 55.0, "BRL")));

        String resposta = fixture.menu.processarMenu(
                NUMERO, "quais serviços vocês fazem?", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Abrir agenda"), resposta);
        assertFalse(resposta.contains("Escova"), resposta);
        assertFalse(resposta.contains("Manicure"), resposta);
        assertEquals(ConversationStep.INICIO, fixture.states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void precoVemDoCatalogoCanonicoENaoDeTextoHardcoded() {
        var fixture = fixture();
        when(fixture.gateway.nomeCanonicoDoServico("quanto custa manicure?"))
                .thenReturn(Optional.of("Manicure"));
        when(fixture.facts.services()).thenReturn(List.of(
                new BusinessConversationFacts.ServiceFact(
                        "Manicure", "Mãos completas", new BigDecimal("55.00"), "BRL", 45)));

        String resposta = fixture.menu.processarMenu(
                NUMERO, "quanto custa manicure?", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Manicure R$ 55,00"), resposta);
        assertTrue(resposta.contains("45 min"), resposta);
        assertFalse(resposta.contains("Rua Augusta"), resposta);
    }

    @Test
    void enderecoEExpedienteVemDasFontesDoNegocio() {
        var fixture = fixture();
        when(fixture.facts.address()).thenReturn(Optional.of("Av. Teste, 123"));
        when(fixture.facts.openingHoursSummary()).thenReturn(
                Optional.of("seg 9h-18h; sáb 9h-14h"));

        String endereco = fixture.menu.processarMenu(
                NUMERO, "onde fica?", fixture.states.buscarPorNumero(NUMERO));
        String horario = fixture.menu.processarMenu(
                NUMERO, "que horas abre?", fixture.states.buscarPorNumero(NUMERO));

        assertEquals("Ficamos em Av. Teste, 123.", endereco);
        assertTrue(horario.contains("seg 9h-18h"), horario);
        assertTrue(horario.contains("sáb 9h-14h"), horario);
    }

    private Fixture fixture() {
        return fixture(null);
    }

    private Fixture fixture(AberturaDeAgenda agenda) {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);
        BusinessConversationFacts facts = mock(BusinessConversationFacts.class);
        SaudacaoDoNegocio saudacao = mock(SaudacaoDoNegocio.class);
        when(saudacao.atual()).thenReturn("Bom dia");

        StrictMvpMenuService menu = new StrictMvpMenuService(
                states,
                mock(AvailabilityApplicationService.class),
                mock(BookingApplicationService.class),
                agenda == null ? OptionalBeans.ausente() : OptionalBeans.de(agenda),
                "STRICT_MVP",
                gateway,
                saudacao,
                new HeuristicBookingIntentInterpreter(),
                facts);

        return new Fixture(states, gateway, facts, menu);
    }

    private record Fixture(ConversationStateService states,
                           ConversationBookingGateway gateway,
                           BusinessConversationFacts facts,
                           StrictMvpMenuService menu) {
    }
}
