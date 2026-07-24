package com.troquim_bot.conversation;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingApplicationService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictMvpMenuServiceTest {

    private static final String NUMERO = "5511999990000";

    @Test
    void devePersistirAguardandoServicoAoIniciarNovoAgendamentoEProcessarProximaMensagemComoServico() {
        ConversationStateService conversationStateService =
                new ConversationStateService(new InMemoryConversationStateRepository());
        StrictMvpMenuService strictMvpMenuService = new StrictMvpMenuService(
                conversationStateService,
                new AvailabilityApplicationService(),
                new BookingApplicationService(
                        new ReservationApplicationService(new InMemoryReservationRepository()),
                        new AppointmentApplicationService(),
                        new CustomerProfileService(new InMemoryCustomerRepository(), TestTenants.pilot()),
                new InMemoryBookingIdempotencyStore()),
                OptionalBeans.ausente(),
                "STRICT_MVP"
        );

        // Cliente envia "oi": recebe o menu principal, estado permanece em INICIO
        ConversationState estadoInicial = conversationStateService.buscarPorNumero(NUMERO);
        String respostaSaudacao = strictMvpMenuService.processarMenu(NUMERO, "oi", estadoInicial);
        assertTrue(respostaSaudacao.contains("Escolha uma opcao"));

        // Cliente envia "1" (Agendar): deve iniciar o fluxo e persistir AGUARDANDO_SERVICO
        ConversationState estadoAntesDoMenu = conversationStateService.buscarPorNumero(NUMERO);
        String respostaMenuServicos = strictMvpMenuService.processarMenu(NUMERO, "1", estadoAntesDoMenu);
        assertTrue(respostaMenuServicos.contains("Qual servico"));

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

    private void assertFalseContemMenuPrincipal(String resposta) {
        assertTrue(!resposta.contains("1) Agendar\n2) Meus agendamentos"),
                "Resposta não deveria repetir o menu principal: " + resposta);
    }
}
