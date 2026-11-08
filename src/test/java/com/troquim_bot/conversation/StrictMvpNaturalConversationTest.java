package com.troquim_bot.conversation;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
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
    void saudacaoNaoPareceSacNemObrigaClienteADigitarOpcao() {
        var fixture = fixture();

        String resposta = fixture.menu.processarMenu(
                NUMERO, "oi", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("pode falar do seu jeito"), resposta);
        assertFalse(resposta.contains("No momento eu consigo te ajudar"), resposta);
        assertFalse(resposta.contains("Escolha uma opção"), resposta);
        assertEquals(ConversationStep.INICIO, fixture.states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void nomeDoServicoSozinhoJaIniciaOAgendamentoSemMenuIntermediario() {
        var fixture = fixture();
        when(fixture.gateway.nomeCanonicoDoServico("manicure"))
                .thenReturn(Optional.of("Manicure"));

        String resposta = fixture.menu.processarMenu(
                NUMERO, "manicure", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Manicure"), resposta);
        assertTrue(resposta.contains("Para qual dia"), resposta);
        ConversationState salvo = fixture.states.buscarPorNumero(NUMERO);
        assertEquals(ConversationStep.AGUARDANDO_DIA, salvo.getStep());
        assertEquals("Manicure", salvo.getDraftAtual().getServico());
    }

    @Test
    void diaEHorarioNaMesmaMensagemSaoAproveitadosNoMesmoTurno() {
        var fixture = fixture();
        ConversationState state = fixture.states.buscarPorNumero(NUMERO);
        state.criarNovoDraft();
        state.getDraftAtual().setServico("Manicure");
        state.setStep(ConversationStep.AGUARDANDO_DIA);
        fixture.states.persistir(state);

        when(fixture.gateway.horarioPertenceAOferta("Manicure", "quinta", "10:30"))
                .thenReturn(true);

        String resposta = fixture.menu.processarMenu(
                NUMERO, "quinta 10:30", fixture.states.buscarPorNumero(NUMERO));

        ConversationState salvo = fixture.states.buscarPorNumero(NUMERO);
        assertEquals("quinta", salvo.getDraftAtual().getDia());
        assertEquals("10:30", salvo.getDraftAtual().getHorario());
        assertEquals(ConversationStep.AGUARDANDO_NOME, salvo.getStep());
        assertTrue(resposta.contains("Qual é o seu nome"), resposta);
    }

    @Test
    void consultaDeServicosUsaCatalogoRealEJaDeixaOpcoesClicaveis() {
        var fixture = fixture();
        when(fixture.gateway.listarServicos()).thenReturn(List.of(
                new ConversationBookingGateway.Servico("Manicure"),
                new ConversationBookingGateway.Servico("Escova")));

        String resposta = fixture.menu.processarMenu(
                NUMERO, "quais serviços vocês fazem?", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Manicure"), resposta);
        assertTrue(resposta.contains("Escova"), resposta);
        assertEquals(ConversationStep.AGUARDANDO_SERVICO,
                fixture.states.buscarPorNumero(NUMERO).getStep());
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
                OptionalBeans.ausente(),
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
