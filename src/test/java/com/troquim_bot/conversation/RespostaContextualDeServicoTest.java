package com.troquim_bot.conversation;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.common.valueobject.Money;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.conversation.state.ConversationStep;
import com.troquim_bot.repository.InMemoryConversationStateRepository;
import com.troquim_bot.support.OptionalBeans;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RespostaContextualDeServicoTest {
    private final ConversationBookingGateway gateway = mock(ConversationBookingGateway.class);

    @Test
    void valorVemDoCatalogoAtualNaoDeTabelaFixa() {
        when(gateway.listarServicosDetalhados()).thenReturn(List.of(
                new ConversationBookingGateway.ServicoDetalhado(
                        "Volume brasileiro", Optional.of(Money.of(180, "BRL")), 90),
                new ConversationBookingGateway.ServicoDetalhado(
                        "Manicure em gel", Optional.of(Money.of(110, "BRL")), 60)
        ));
        String resposta = RespostaContextualDeServico.responder(
                "Quanto custa volume brasileiro?", gateway).orElseThrow();
        assertTrue(resposta.contains("180"), resposta);
        assertTrue(resposta.contains("Volume brasileiro"), resposta);
        assertFalse(resposta.contains("manicure"), resposta);
    }

    @Test
    void naoInventaPrecoNaoCadastrado() {
        when(gateway.listarServicosDetalhados()).thenReturn(List.of(
                new ConversationBookingGateway.ServicoDetalhado(
                        "Manutenção de cílios", Optional.empty(), 60)
        ));
        String resposta = RespostaContextualDeServico.responder(
                "Qual valor de manutenção de cílios?", gateway).orElseThrow();
        assertTrue(resposta.contains("não foi informado"), resposta);
        assertFalse(resposta.contains("R$"), resposta);
    }

    @Test
    void alertaDeAlergiaNaoConfirmaProcedimentoNemReserva() {
        String resposta = RespostaContextualDeServico.responder(
                "Tenho alergia à cola, posso fazer extensão?", gateway).orElseThrow();
        assertTrue(resposta.contains("avaliar"), resposta);
        assertFalse(resposta.contains("confirmado"), resposta);
        verifyNoInteractions(gateway);
    }

    @Test
    void mensagensDeReservaEConfirmacoesNumericasContinuamNoFluxoOriginal() {
        assertTrue(RespostaContextualDeServico.responder("quero agendar cílios", gateway).isEmpty());
        assertTrue(RespostaContextualDeServico.responder("1", gateway).isEmpty());
        assertTrue(RespostaContextualDeServico.responder("nav_voltar", gateway).isEmpty());
    }

    @Test
    void perguntaDeValorNoMenuNaoAlteraEtapaDoAgendamento() {
        String numero = "5511987654321";
        var states = new ConversationStateService(new InMemoryConversationStateRepository());
        var menu = new StrictMvpMenuService(
                states,
                mock(AvailabilityApplicationService.class),
                mock(BookingApplicationService.class),
                OptionalBeans.ausente(),
                "STRICT_MVP",
                gateway);
        when(gateway.listarServicosDetalhados()).thenReturn(List.of(
                new ConversationBookingGateway.ServicoDetalhado(
                        "Manicure", Optional.of(Money.of(75, "BRL")), 40)
        ));
        String resposta = menu.processarMenu(numero, "Quanto custa manicure?",
                states.buscarPorNumero(numero));
        assertTrue(resposta.contains("75"), resposta);
        assertEquals(ConversationStep.INICIO, states.buscarPorNumero(numero).getStep());
    }

    @Test
    void menuSemServicoNaoCriaCatalogoPadrao() {
        when(gateway.listarServicosDetalhados()).thenReturn(List.of());
        String resposta = RespostaContextualDeServico.responder(
                "Quais serviços vocês oferecem?", gateway).orElseThrow();
        assertTrue(resposta.contains("ainda não configurou"), resposta);
        assertFalse(resposta.contains("manicure"), resposta);
    }
}
