package com.troquim_bot.conversation;

import com.troquim_bot.support.AvailabilityDeTeste;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.messaging.ConversationInteractivePresentation;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.conversation.state.ConversationStep;
import com.troquim_bot.repository.InMemoryConversationStateRepository;
import com.troquim_bot.support.OptionalBeans;
import com.troquim_bot.support.InMemoryBookingIdempotencyStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrictMvpMenuServiceTest {

    private static final String NUMERO = "5511999990000";

    @Test
    void devePersistirAguardandoServicoAoIniciarNovoAgendamentoEProcessarProximaMensagemComoServico() {
        ConversationStateService conversationStateService =
                new ConversationStateService(new InMemoryConversationStateRepository());
        StrictMvpMenuService strictMvpMenuService = new StrictMvpMenuService(
                conversationStateService,
                AvailabilityDeTeste.legado(),
                new BookingApplicationService(TestTenants.pilot(),
                        new ReservationApplicationService(new InMemoryReservationRepository()),
                        new AppointmentApplicationService(),
                        new CustomerProfileService(new InMemoryCustomerRepository(), TestTenants.pilot()),
                new InMemoryBookingIdempotencyStore(), new com.troquim_bot.infrastructure.persistence.InMemoryBookingSlotCriticalSection(), com.troquim_bot.support.TestBookingSupport.consultarDisponibilidadeInerte()),
                OptionalBeans.ausente(),
                "STRICT_MVP"
        );

        // Cliente envia "oi": recebe o menu principal, estado permanece em INICIO
        ConversationState estadoInicial = conversationStateService.buscarPorNumero(NUMERO);
        String respostaSaudacao = strictMvpMenuService.processarMenu(NUMERO, "oi", estadoInicial);
        assertTrue(respostaSaudacao.contains("Escolha uma opção"));

        // Cliente envia "1" (Agendar): deve iniciar o fluxo e persistir AGUARDANDO_SERVICO
        ConversationState estadoAntesDoMenu = conversationStateService.buscarPorNumero(NUMERO);
        String respostaMenuServicos = strictMvpMenuService.processarMenu(NUMERO, "1", estadoAntesDoMenu);
        assertTrue(respostaMenuServicos.contains("Qual serviço"));

        // Reproduz exatamente o que o ConversationOrchestrator faz na próxima mensagem:
        // busca o estado do zero no repositório (não reaproveita o objeto em memória).
        ConversationState estadoPersistido = conversationStateService.buscarPorNumero(NUMERO);
        assertEquals(ConversationStep.AGUARDANDO_SERVICO, estadoPersistido.getStep(),
                "O step AGUARDANDO_SERVICO precisa estar persistido, não só em memória local");

        // Cliente envia o serviço por nome: deve ser tratado como escolha de serviço
        // (avançando para o menu de dias), e não cair de volta no menu principal.
        String respostaServico = strictMvpMenuService.processarMenu(NUMERO, "cabelo", estadoPersistido);
        assertTrue(respostaServico.contains("Para qual dia"),
                "Esperava avançar para o menu de dias, mas recebeu: " + respostaServico);
        assertFalseContemMenuPrincipal(respostaServico);
    }

    @Test
    void semHorarioVoltaParaEscolhaDeDiaEProximaOpcaoTrocaODia() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.listarServicos()).thenReturn(
                List.of(new ConversationBookingGateway.Servico("Manicure")));
        when(gateway.nomeCanonicoDoServico("manicure")).thenReturn(Optional.of("Manicure"));
        when(gateway.consultarHorarios("Manicure", "segunda"))
                .thenReturn(new ConversationBookingGateway.ConsultaHorarios(
                        ConversationBookingGateway.Status.OK, "Manicure", "segunda", List.of()));
        when(gateway.consultarHorarios("Manicure", "terca"))
                .thenReturn(new ConversationBookingGateway.ConsultaHorarios(
                        ConversationBookingGateway.Status.OK, "Manicure", "terca",
                        List.of(LocalTime.of(9, 15), LocalTime.of(10, 30))));

        StrictMvpMenuService menu = menuComGateway(states, gateway);

        menu.processarMenu(NUMERO, "1", states.buscarPorNumero(NUMERO));
        menu.processarMenu(NUMERO, "manicure", states.buscarPorNumero(NUMERO));

        String semVaga = menu.processarMenu(NUMERO, "1", states.buscarPorNumero(NUMERO));
        assertTrue(semVaga.contains("Não tenho horários disponíveis para segunda"), semVaga);
        assertEquals(ConversationStep.AGUARDANDO_DIA, states.buscarPorNumero(NUMERO).getStep());
        assertTrue(states.buscarPorNumero(NUMERO).getDraftAtual().getDia() == null,
                "Dia sem vaga deve ser limpo para a proxima escolha substituir de verdade");

        String terca = menu.processarMenu(NUMERO, "2", states.buscarPorNumero(NUMERO));
        assertTrue(terca.contains("Horários disponíveis para terça"), terca);
        assertEquals("terca", states.buscarPorNumero(NUMERO).getDraftAtual().getDia());
        assertEquals(ConversationStep.AGUARDANDO_HORARIO, states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void estadoAntigoPresoEmHorarioSemSlotsSeAutoCorrigeAoEscolherOutroDia() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.consultarHorarios("Manicure", "segunda"))
                .thenReturn(new ConversationBookingGateway.ConsultaHorarios(
                        ConversationBookingGateway.Status.OK, "Manicure", "segunda", List.of()));
        when(gateway.consultarHorarios("Manicure", "terca"))
                .thenReturn(new ConversationBookingGateway.ConsultaHorarios(
                        ConversationBookingGateway.Status.OK, "Manicure", "terca",
                        List.of(LocalTime.of(9, 15))));

        StrictMvpMenuService menu = menuComGateway(states, gateway);

        ConversationState state = states.buscarPorNumero(NUMERO);
        state.criarNovoDraft();
        state.getDraftAtual().setServico("Manicure");
        state.getDraftAtual().setDia("segunda");
        state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        states.persistir(state);

        String resposta = menu.processarMenu(NUMERO, "2", states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Horários disponíveis para terça"), resposta);
        assertEquals("terca", states.buscarPorNumero(NUMERO).getDraftAtual().getDia());
    }

    @Test
    void servicoEDiaSaoClicaveisEIncluemVoltar() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.listarServicos()).thenReturn(List.of(
                new ConversationBookingGateway.Servico("Manicure"),
                new ConversationBookingGateway.Servico("Escova"),
                new ConversationBookingGateway.Servico("Sobrancelha")));

        StrictMvpMenuService menu = menuComGateway(states, gateway);

        String servicos = menu.processarMenu(NUMERO, "1", states.buscarPorNumero(NUMERO));
        var servicePresentation = ConversationInteractivePresentation.from(servicos).orElseThrow();
        assertEquals(ConversationInteractivePresentation.Type.LIST, servicePresentation.type());
        assertEquals(4, servicePresentation.options().size());
        assertEquals("nav_voltar", servicePresentation.options().get(3).id());

        String dias = menu.processarMenu(NUMERO, "1", states.buscarPorNumero(NUMERO));
        var dayPresentation = ConversationInteractivePresentation.from(dias).orElseThrow();
        assertEquals(7, dayPresentation.options().size());
        assertEquals("nav_voltar", dayPresentation.options().get(6).id());
    }

    @Test
    void dezesseteHorariosFicamPaginadosECliqueGlobalMantemSlotCorreto() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        List<LocalTime> horarios = List.of(
                LocalTime.of(9, 0), LocalTime.of(9, 15), LocalTime.of(9, 30),
                LocalTime.of(9, 45), LocalTime.of(10, 0), LocalTime.of(10, 15),
                LocalTime.of(10, 30), LocalTime.of(10, 45), LocalTime.of(11, 0),
                LocalTime.of(11, 15), LocalTime.of(11, 30), LocalTime.of(11, 45),
                LocalTime.of(12, 0), LocalTime.of(12, 15), LocalTime.of(12, 30),
                LocalTime.of(12, 45), LocalTime.of(13, 0));

        when(gateway.consultarHorarios("Manicure", "sabado"))
                .thenReturn(new ConversationBookingGateway.ConsultaHorarios(
                        ConversationBookingGateway.Status.OK, "Manicure", "sabado", horarios));

        StrictMvpMenuService menu = menuComGateway(states, gateway);
        ConversationState state = states.buscarPorNumero(NUMERO);
        state.criarNovoDraft();
        state.getDraftAtual().setServico("Manicure");
        state.getDraftAtual().setDia("sabado");
        state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        states.persistir(state);

        String primeira = menu.processarMenu(
                NUMERO, "horarios_pagina_0", states.buscarPorNumero(NUMERO));
        var p1 = ConversationInteractivePresentation.from(primeira).orElseThrow();
        assertEquals(9, p1.options().size());
        assertEquals("1", p1.options().get(0).id());
        assertEquals("7", p1.options().get(6).id());
        assertEquals("horarios_pagina_1", p1.options().get(7).id());
        assertEquals("nav_voltar", p1.options().get(8).id());

        String segunda = menu.processarMenu(
                NUMERO, "horarios_pagina_1", states.buscarPorNumero(NUMERO));
        var p2 = ConversationInteractivePresentation.from(segunda).orElseThrow();
        assertEquals(10, p2.options().size());
        assertEquals("8", p2.options().get(0).id());
        assertEquals("14", p2.options().get(6).id());
        assertEquals("horarios_pagina_0", p2.options().get(7).id());
        assertEquals("horarios_pagina_2", p2.options().get(8).id());
        assertEquals("nav_voltar", p2.options().get(9).id());

        String terceira = menu.processarMenu(
                NUMERO, "horarios_pagina_2", states.buscarPorNumero(NUMERO));
        var p3 = ConversationInteractivePresentation.from(terceira).orElseThrow();
        assertEquals(5, p3.options().size());
        assertEquals("15", p3.options().get(0).id());
        assertEquals("17", p3.options().get(2).id());
        assertEquals("horarios_pagina_1", p3.options().get(3).id());
        assertEquals("nav_voltar", p3.options().get(4).id());

        menu.processarMenu(NUMERO, "12", states.buscarPorNumero(NUMERO));
        assertEquals("11:45", states.buscarPorNumero(NUMERO).getDraftAtual().getHorario());
        assertEquals(ConversationStep.AGUARDANDO_NOME,
                states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void nomeLivreMantemBotaoVoltarEVoltarRetornaAosHorarios() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.consultarHorarios("Manicure", "sabado"))
                .thenReturn(new ConversationBookingGateway.ConsultaHorarios(
                        ConversationBookingGateway.Status.OK, "Manicure", "sabado",
                        List.of(LocalTime.of(9, 0), LocalTime.of(9, 15))));

        StrictMvpMenuService menu = menuComGateway(states, gateway);
        ConversationState state = states.buscarPorNumero(NUMERO);
        state.criarNovoDraft();
        state.getDraftAtual().setServico("Manicure");
        state.getDraftAtual().setDia("sabado");
        state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        states.persistir(state);

        String nome = menu.processarMenu(NUMERO, "1", states.buscarPorNumero(NUMERO));
        var nomePresentation = ConversationInteractivePresentation.from(nome).orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, nomePresentation.type());
        assertEquals(1, nomePresentation.options().size());
        assertEquals("nav_voltar", nomePresentation.options().get(0).id());
        assertEquals(ConversationStep.AGUARDANDO_NOME, states.buscarPorNumero(NUMERO).getStep());

        String voltou = menu.processarMenu(NUMERO, "voltar", states.buscarPorNumero(NUMERO));
        assertEquals(ConversationStep.AGUARDANDO_HORARIO, states.buscarPorNumero(NUMERO).getStep());
        assertTrue(states.buscarPorNumero(NUMERO).getDraftAtual().getHorario() == null);
        assertTrue(voltou.contains("Horários disponíveis para sábado"), voltou);
    }

    @Test
    void confirmacaoTemConfirmarCancelarEVoltarClicaveis() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.consultarHorarios("Manicure", "sabado"))
                .thenReturn(new ConversationBookingGateway.ConsultaHorarios(
                        ConversationBookingGateway.Status.OK, "Manicure", "sabado",
                        List.of(LocalTime.of(9, 0))));

        StrictMvpMenuService menu = menuComGateway(states, gateway);
        ConversationState state = states.buscarPorNumero(NUMERO);
        state.setNome("Gui");
        state.criarNovoDraft();
        state.getDraftAtual().setServico("Manicure");
        state.getDraftAtual().setDia("sabado");
        state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        states.persistir(state);

        String confirmacao = menu.processarMenu(NUMERO, "1", states.buscarPorNumero(NUMERO));
        var presentation = ConversationInteractivePresentation.from(confirmacao).orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, presentation.type());
        assertEquals(3, presentation.options().size());
        assertEquals("confirmar_sim", presentation.options().get(0).id());
        assertEquals("confirmar_nao", presentation.options().get(1).id());
        assertEquals("nav_voltar", presentation.options().get(2).id());
        assertEquals(ConversationStep.AGUARDANDO_CONFIRMACAO,
                states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void turboVaiDaFraseNaturalDiretoParaSlotsERevalidaOClique() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.recomendar(eq(NUMERO), any(BookingIntent.class)))
                .thenReturn(new ConversationBookingGateway.Recomendacao(
                        ConversationBookingGateway.Status.OK,
                        "Manicure",
                        List.of(
                                new ConversationBookingGateway.SlotSugerido(
                                        "Manicure", LocalDate.of(2026, 9, 25), LocalTime.of(16, 0)),
                                new ConversationBookingGateway.SlotSugerido(
                                        "Manicure", LocalDate.of(2026, 9, 25), LocalTime.of(17, 0))),
                        false));
        when(gateway.horarioPertenceAOferta("Manicure", "2026-09-25", "17h"))
                .thenReturn(true);

        StrictMvpMenuService menu = menuComGateway(states, gateway);

        String sugestoes = menu.processarMenu(
                NUMERO,
                "quero manicure sexta depois das 16",
                states.buscarPorNumero(NUMERO));

        var presentation = ConversationInteractivePresentation.from(sugestoes).orElseThrow();
        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, presentation.type());
        assertEquals("turbo_slot_2026-09-25_1600", presentation.options().get(0).id());
        assertEquals("turbo_slot_2026-09-25_1700", presentation.options().get(1).id());
        assertEquals("nav_voltar", presentation.options().get(2).id());
        assertEquals(ConversationStep.AGUARDANDO_HORARIO,
                states.buscarPorNumero(NUMERO).getStep());
        assertEquals("Manicure", states.buscarPorNumero(NUMERO).getDraftAtual().getServico());

        String proximo = menu.processarMenu(
                NUMERO,
                "turbo_slot_2026-09-25_1700",
                states.buscarPorNumero(NUMERO));

        assertTrue(proximo.contains("Qual é o seu nome"), proximo);
        assertEquals("2026-09-25", states.buscarPorNumero(NUMERO).getDraftAtual().getDia());
        assertEquals("17h", states.buscarPorNumero(NUMERO).getDraftAtual().getHorario());
        assertEquals(ConversationStep.AGUARDANDO_NOME,
                states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void fraseNaturalReorientaFluxoMesmoQuandoEstavaAguardandoHorario() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.recomendar(eq(NUMERO), any(BookingIntent.class)))
                .thenReturn(new ConversationBookingGateway.Recomendacao(
                        ConversationBookingGateway.Status.OK,
                        "Escova",
                        List.of(new ConversationBookingGateway.SlotSugerido(
                                "Escova", LocalDate.of(2026, 9, 29), LocalTime.of(10, 30))),
                        false));

        StrictMvpMenuService menu = menuComGateway(states, gateway);

        ConversationState state = states.buscarPorNumero(NUMERO);
        state.criarNovoDraft();
        state.getDraftAtual().setServico("Escova");
        state.getDraftAtual().setDia("terca");
        state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        states.persistir(state);

        String resposta = menu.processarMenu(
                NUMERO,
                "Quero fazer escova terça de manhã",
                states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Achei estes horários para Escova"), resposta);
        var presentation = ConversationInteractivePresentation.from(resposta).orElseThrow();
        assertEquals("turbo_slot_2026-09-29_1030", presentation.options().get(0).id());
        assertEquals(ConversationStep.AGUARDANDO_HORARIO,
                states.buscarPorNumero(NUMERO).getStep());
        assertEquals("Escova", states.buscarPorNumero(NUMERO).getDraftAtual().getServico());
    }

    @Test
    void turboSemVagaOfereceWaitlistClicavelEConfirmaEntrada() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.recomendar(eq(NUMERO), any(BookingIntent.class)))
                .thenReturn(new ConversationBookingGateway.Recomendacao(
                        ConversationBookingGateway.Status.OK,
                        "Manicure",
                        List.of(),
                        false));
        when(gateway.entrarNaEspera(
                eq(NUMERO),
                eq("Manicure"),
                eq("sexta"),
                eq(LocalTime.of(16, 0)),
                eq(null)))
                .thenReturn(true);

        StrictMvpMenuService menu = menuComGateway(states, gateway);

        String semVaga = menu.processarMenu(
                NUMERO,
                "quero manicure sexta depois das 16",
                states.buscarPorNumero(NUMERO));

        var presentation = ConversationInteractivePresentation.from(semVaga).orElseThrow();
        assertEquals(ConversationInteractivePresentation.Type.BUTTONS, presentation.type());
        assertTrue(presentation.options().get(0).id().startsWith("waitlist_join_sexta_1600_"));
        assertEquals("waitlist_other", presentation.options().get(1).id());
        assertEquals("nav_voltar", presentation.options().get(2).id());

        String entrou = menu.processarMenu(
                NUMERO,
                presentation.options().get(0).id(),
                states.buscarPorNumero(NUMERO));

        assertTrue(entrou.contains("entrou na espera"), entrou);
    }

    @Test
    void waitlistQuickReplyGlobalRetomaDiretoNaConfirmacaoComNomeSalvo() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);
        java.util.UUID waitlistId = java.util.UUID.randomUUID();
        LocalDate data = LocalDate.of(2026, 9, 25);
        LocalTime horario = LocalTime.of(17, 0);

        when(gateway.prepararResgateWaitlist(NUMERO, waitlistId, data, horario))
                .thenReturn(new ConversationBookingGateway.WaitlistClaim(
                        ConversationBookingGateway.Status.OK,
                        "Manicure",
                        data,
                        horario,
                        "Gui"));

        StrictMvpMenuService menu = menuComGateway(states, gateway);
        String payload = "waitlist_claim_" + waitlistId + "_2026-09-25_1700";

        String resposta = menu.processarMenu(
                NUMERO, payload, states.buscarPorNumero(NUMERO));

        assertTrue(resposta.contains("Vou confirmar seu agendamento"), resposta);
        ConversationState salvo = states.buscarPorNumero(NUMERO);
        assertEquals(ConversationStep.AGUARDANDO_CONFIRMACAO, salvo.getStep());
        assertEquals("Manicure", salvo.getDraftAtual().getServico());
        assertEquals("2026-09-25", salvo.getDraftAtual().getDia());
        assertEquals("17h", salvo.getDraftAtual().getHorario());
        assertEquals("Gui", salvo.getDraftAtual().getNome());
    }

    @Test
    void cancelamentoComMultiplosAgendamentosEhClicavelETemVoltar() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.listarAgendamentosAtivos(NUMERO)).thenReturn(List.of(
                new ConversationBookingGateway.Agendamento(
                        "Manicure", LocalDate.of(2026, 9, 23), LocalTime.of(9, 15), true),
                new ConversationBookingGateway.Agendamento(
                        "Escova", LocalDate.of(2026, 9, 24), LocalTime.of(10, 30), true),
                new ConversationBookingGateway.Agendamento(
                        "Sobrancelha", LocalDate.of(2026, 9, 25), LocalTime.of(14, 0), true)));

        StrictMvpMenuService menu = menuComGateway(states, gateway);

        String resposta = menu.processarMenu(NUMERO, "3", states.buscarPorNumero(NUMERO));
        var presentation = ConversationInteractivePresentation.from(resposta).orElseThrow();

        assertEquals(ConversationInteractivePresentation.Type.LIST, presentation.type());
        assertEquals(4, presentation.options().size());
        assertEquals("1", presentation.options().get(0).id());
        assertEquals("3", presentation.options().get(2).id());
        assertEquals("nav_voltar", presentation.options().get(3).id());
        assertEquals(ConversationStep.AGUARDANDO_CANCELAMENTO,
                states.buscarPorNumero(NUMERO).getStep());
    }

    @Test
    void usaNomeDoPerfilComoSaudacaoESolicitaConfirmacaoAntesDeSalvar() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

        when(gateway.consultarHorarios("Manicure", "sabado"))
                .thenReturn(new ConversationBookingGateway.ConsultaHorarios(
                        ConversationBookingGateway.Status.OK, "Manicure", "sabado",
                        List.of(LocalTime.of(9, 0))));

        StrictMvpMenuService menu = menuComGateway(states, gateway);
        ConversationState state = states.buscarPorNumero(NUMERO);
        state.setNomePerfil("Gui");
        states.persistir(state);

        String saudacao = menu.processarMenu(NUMERO, "oi", states.buscarPorNumero(NUMERO));
        assertTrue(saudacao.contains("Olá Gui"), saudacao);

        state = states.buscarPorNumero(NUMERO);
        state.criarNovoDraft();
        state.getDraftAtual().setServico("Manicure");
        state.getDraftAtual().setDia("sabado");
        state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        states.persistir(state);

        String escolhaNome = menu.processarMenu(NUMERO, "1", states.buscarPorNumero(NUMERO));
        var presentation = ConversationInteractivePresentation.from(escolhaNome).orElseThrow();

        assertEquals(ConversationStep.AGUARDANDO_ESCOLHA_NOME,
                states.buscarPorNumero(NUMERO).getStep());
        assertEquals(3, presentation.options().size());
        assertEquals("nome_perfil_usar", presentation.options().get(0).id());
        assertEquals("nome_outro", presentation.options().get(1).id());
        assertEquals("nav_voltar", presentation.options().get(2).id());

        String confirmacao = menu.processarMenu(
                NUMERO, "nome_perfil_usar", states.buscarPorNumero(NUMERO));

        assertEquals("Gui", states.buscarPorNumero(NUMERO).getNome());
        assertEquals("Gui", states.buscarPorNumero(NUMERO).getDraftAtual().getNome());
        assertEquals(ConversationStep.AGUARDANDO_CONFIRMACAO,
                states.buscarPorNumero(NUMERO).getStep());
        assertTrue(confirmacao.contains("Manicure"), confirmacao);
    }

    @Test
    void outroNomeMantemPerfilComoSugestaoMasSalvaNomeInformado() {
        ConversationStateService states =
                new ConversationStateService(new InMemoryConversationStateRepository());
        ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);
        StrictMvpMenuService menu = menuComGateway(states, gateway);

        ConversationState state = states.buscarPorNumero(NUMERO);
        state.setNomePerfil("Gui");
        state.criarNovoDraft();
        state.getDraftAtual().setServico("Manicure");
        state.getDraftAtual().setDia("sabado");
        state.getDraftAtual().setHorario("9h");
        state.setStep(ConversationStep.AGUARDANDO_ESCOLHA_NOME);
        states.persistir(state);

        String pergunta = menu.processarMenu(
                NUMERO, "nome_outro", states.buscarPorNumero(NUMERO));
        assertTrue(pergunta.contains("Qual nome"), pergunta);
        assertEquals(ConversationStep.AGUARDANDO_NOME,
                states.buscarPorNumero(NUMERO).getStep());

        menu.processarMenu(NUMERO, "Julia", states.buscarPorNumero(NUMERO));

        assertEquals("Gui", states.buscarPorNumero(NUMERO).getNomePerfil());
        assertEquals("Julia", states.buscarPorNumero(NUMERO).getNome());
        assertEquals("Julia", states.buscarPorNumero(NUMERO).getDraftAtual().getNome());
    }

    private StrictMvpMenuService menuComGateway(ConversationStateService states,
                                                 ConversationBookingGateway gateway) {
        return new StrictMvpMenuService(
                states,
                mock(AvailabilityApplicationService.class),
                mock(BookingApplicationService.class),
                OptionalBeans.ausente(),
                "STRICT_MVP",
                gateway);
    }

    private void assertFalseContemMenuPrincipal(String resposta) {
        assertTrue(!resposta.contains("1) Agendar\n2) Meus agendamentos"),
                "Resposta não deveria repetir o menu principal: " + resposta);
    }
}
