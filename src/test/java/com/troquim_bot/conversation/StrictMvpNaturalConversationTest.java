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
    void mensagemLivreNoInicioNaoEInterpretadaComoServicoEOfereceCaminhosClicaveis() {
        var fixture = fixture();

        String resposta = fixture.menu.processarMenu(
                NUMERO, "MANI", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Abrir agenda"), resposta);
        assertTrue(resposta.contains("Escolher serviço"), resposta);
        assertFalse(resposta.contains("reformular"), resposta);
        assertEquals(ConversationStep.INICIO, fixture.states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void agendarAbreAgendaComoCaminhoPrincipalEDeixaServicoComoAlternativa() {
        AberturaDeAgenda agenda = mock(AberturaDeAgenda.class);
        when(agenda.disponivel()).thenReturn(true);
        when(agenda.abrirPara(NUMERO)).thenReturn(AberturaDeAgendaResultado.enviado());

        var fixture = fixture(agenda);

        String resposta = fixture.menu.processarMenu(
                NUMERO, "agendar", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Te enviei a agenda"), resposta);
        assertTrue(resposta.contains("Prefiro escolher o serviço"), resposta);
        assertFalse(resposta.contains("Para qual dia"), resposta);
        assertEquals(ConversationStep.AGUARDANDO_SERVICO,
                fixture.states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void servicoDigitadoDuranteEscolhaNaoEAdvinhadoEReapresentaBotoes() {
        var fixture = fixture();
        when(fixture.gateway.listarServicos()).thenReturn(List.of(
                new ConversationBookingGateway.Servico("Escova", "", 45, 80.0, "BRL"),
                new ConversationBookingGateway.Servico("Manicure", "", 40, 55.0, "BRL")));

        ConversationState state = fixture.states.buscarPorNumero(NUMERO);
        state.criarNovoDraft();
        state.setStep(ConversationStep.AGUARDANDO_SERVICO);
        fixture.states.persistir(state);

        String resposta = fixture.menu.processarMenu(
                NUMERO, "manicure", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("escolha o serviço pelos botões"), resposta);
        assertTrue(resposta.contains("Abrir agenda"), resposta);
        assertTrue(resposta.contains("Manicure"), resposta);
        assertEquals(ConversationStep.AGUARDANDO_SERVICO,
                fixture.states.buscarPorNumero(NUMERO).getStep());
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
        assertFalse(resposta.contains("reformular"), resposta);
    }

    @Test
    void consultaDeServicosContinuaUsandoCatalogoRealEClicavel() {
        var fixture = fixture();
        when(fixture.gateway.listarServicos()).thenReturn(List.of(
                new ConversationBookingGateway.Servico("Escova", "", 45, 80.0, "BRL"),
                new ConversationBookingGateway.Servico("Manicure", "", 40, 55.0, "BRL")));

        String resposta = fixture.menu.processarMenu(
                NUMERO, "quais serviços vocês fazem?", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Escova"), resposta);
        assertTrue(resposta.contains("Manicure"), resposta);
        assertTrue(resposta.contains("[[choice:"), resposta);
        assertEquals(ConversationStep.AGUARDANDO_SERVICO,
                fixture.states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void primeiroBlocoDeHorarioMostraTresRecomendacoesEAgendaComoEscape() {
        var fixture = fixture();
        ConversationState state = fixture.states.buscarPorNumero(NUMERO);
        state.criarNovoDraft();
        state.getDraftAtual().setServico("Escova");
        state.getDraftAtual().setDia("quinta");
        state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        fixture.states.persistir(state);

        var slots = List.of(
                new ConversationBookingGateway.SlotSugerido(
                        "Escova", java.time.LocalDate.of(2026, 11, 12), LocalTime.of(15, 30)),
                new ConversationBookingGateway.SlotSugerido(
                        "Escova", java.time.LocalDate.of(2026, 11, 12), LocalTime.of(17, 0)),
                new ConversationBookingGateway.SlotSugerido(
                        "Escova", java.time.LocalDate.of(2026, 11, 12), LocalTime.of(18, 30)));

        when(fixture.gateway.recomendarHorariosDoDia("Escova", "quinta", 3))
                .thenReturn(new ConversationBookingGateway.Recomendacao(
                        ConversationBookingGateway.Status.OK, "Escova", slots, false));

        state.setStep(ConversationStep.AGUARDANDO_DIA);
        fixture.states.persistir(state);

        String resposta = fixture.menu.processarMenu(
                NUMERO, "quinta", fixture.states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("15:30"), resposta);
        assertTrue(resposta.contains("17h"), resposta);
        assertTrue(resposta.contains("18:30"), resposta);
        assertTrue(resposta.contains("Ver todos os horários"), resposta);
        assertTrue(resposta.contains("Ver agenda completa"), resposta);
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
