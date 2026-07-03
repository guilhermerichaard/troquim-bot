package com.troquim_bot.application.conversation;

import com.troquim_bot.application.intent.IntentEngine;
import com.troquim_bot.application.intent.IntentResult;
import com.troquim_bot.application.intent.IntentType;
import com.troquim_bot.conversation.Conversation;
import com.troquim_bot.conversation.ConversationId;
import com.troquim_bot.conversation.ConversationStatus;
import com.troquim_bot.conversation.ConversationStep;
import com.troquim_bot.repository.InMemoryConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationApplicationServiceTest {

    private ConversationApplicationService conversationApplicationService;
    private InMemoryConversationRepository conversationRepository;

    private String customerId;
    private String serviceId;
    private String professionalId;
    private String reservationId;
    private String appointmentId;

    @BeforeEach
    void setUp() {
        conversationRepository = new InMemoryConversationRepository();
        IntentEngine intentEngine = new IntentEngine() {
            @Override
            public IntentResult classify(String message) {
                return new IntentResult(IntentType.UNKNOWN);
            }
        };
        conversationApplicationService = new ConversationApplicationService(
            new ConversationRegistry(conversationRepository),
            new ConversationOrchestrator(
                (numero, mensagem) -> "resposta",
                new IgnoringWhatsAppAdapter(),
                intentEngine
            ),
            new ConversationInputMapper()
        );

        customerId = UUID.randomUUID().toString();
        serviceId = UUID.randomUUID().toString();
        professionalId = UUID.randomUUID().toString();
        reservationId = UUID.randomUUID().toString();
        appointmentId = UUID.randomUUID().toString();
    }

    @Test
    void deveCriarConversaComEstadoInicial() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        assertNotNull(conversation.getId());
        assertEquals(customerId, conversation.getCustomerId());
        assertEquals(ConversationStep.IDLE, conversation.getCurrentStep());
        assertEquals(ConversationStatus.ACTIVE, conversation.getStatus());
        assertNull(conversation.getSelectedServiceId());
        assertNull(conversation.getReservationId());
        assertNotNull(conversation.getCriadoEm());
        assertNotNull(conversation.getAtualizadoEm());
    }

    @Test
    void deveValidarCustomerIdObrigatorio() {
        assertThrows(IllegalArgumentException.class, () ->
            conversationApplicationService.criarConversa(null));
    }

    @Test
    void deveValidarCustomerIdComoUuid() {
        assertThrows(IllegalArgumentException.class, () ->
            conversationApplicationService.criarConversa("customer-invalido"));
    }

    @Test
    void deveBuscarConversaPorId() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        Optional<Conversation> encontrado = conversationApplicationService.buscarPorId(conversation.getId());

        assertTrue(encontrado.isPresent());
        assertEquals(conversation.getId(), encontrado.get().getId());
    }

    @Test
    void deveRetornarVazioQuandoBuscarIdNulo() {
        assertTrue(conversationApplicationService.buscarPorId(null).isEmpty());
    }

    @Test
    void deveListarTodasAsConversas() {
        conversationApplicationService.criarConversa(customerId);
        conversationApplicationService.criarConversa(UUID.randomUUID().toString());

        List<Conversation> conversations = conversationApplicationService.listarTodos();

        assertEquals(2, conversations.size());
    }

    @Test
    void deveAtualizarCamposSelecionadosParcialmente() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        Conversation atualizado = conversationApplicationService.atualizarCampos(
            conversation.getId(),
            serviceId,
            null,
            "2026-07-20",
            "10:00",
            "10:30",
            null,
            null
        );

        assertEquals(serviceId, atualizado.getSelectedServiceId());
        assertNull(atualizado.getSelectedProfessionalId());
        assertEquals(LocalDate.of(2026, 7, 20), atualizado.getSelectedDate());
        assertEquals(LocalTime.of(10, 0), atualizado.getSelectedStartTime());
        assertEquals(LocalTime.of(10, 30), atualizado.getSelectedEndTime());

        Conversation atualizadoNovamente = conversationApplicationService.atualizarCampos(
            conversation.getId(),
            null,
            professionalId,
            null,
            null,
            null,
            null,
            null
        );

        assertEquals(serviceId, atualizadoNovamente.getSelectedServiceId());
        assertEquals(professionalId, atualizadoNovamente.getSelectedProfessionalId());
        assertEquals(LocalDate.of(2026, 7, 20), atualizadoNovamente.getSelectedDate());
    }

    @Test
    void deveArmazenarReservationIdEAppointmentIdQuandoExistirem() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        Conversation atualizado = conversationApplicationService.atualizarCampos(
            conversation.getId(),
            null,
            null,
            null,
            null,
            null,
            reservationId,
            appointmentId
        );

        assertEquals(reservationId, atualizado.getReservationId());
        assertEquals(appointmentId, atualizado.getAppointmentId());
    }

    @Test
    void deveValidarIdsSelecionadosComoUuid() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        assertThrows(IllegalArgumentException.class, () ->
            conversationApplicationService.atualizarCampos(
                conversation.getId(),
                "service-invalido",
                null,
                null,
                null,
                null,
                null,
                null
            ));
    }

    @Test
    void deveValidarDataEHorario() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        assertThrows(IllegalArgumentException.class, () ->
            conversationApplicationService.atualizarCampos(
                conversation.getId(),
                null,
                null,
                "20/07/2026",
                null,
                null,
                null,
                null
            ));

        assertThrows(IllegalArgumentException.class, () ->
            conversationApplicationService.atualizarCampos(
                conversation.getId(),
                null,
                null,
                null,
                "10h00",
                null,
                null,
                null
            ));
    }

    @Test
    void deveAvancarEtapasAteFinalizar() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        assertEquals(ConversationStep.SELECT_SERVICE,
            conversationApplicationService.avancarEtapa(conversation.getId()).getCurrentStep());
        assertEquals(ConversationStep.SELECT_PROFESSIONAL,
            conversationApplicationService.avancarEtapa(conversation.getId()).getCurrentStep());
        assertEquals(ConversationStep.SELECT_DATE,
            conversationApplicationService.avancarEtapa(conversation.getId()).getCurrentStep());
        assertEquals(ConversationStep.SELECT_TIME,
            conversationApplicationService.avancarEtapa(conversation.getId()).getCurrentStep());
        assertEquals(ConversationStep.CONFIRMATION,
            conversationApplicationService.avancarEtapa(conversation.getId()).getCurrentStep());

        Conversation finalizada = conversationApplicationService.avancarEtapa(conversation.getId());

        assertEquals(ConversationStep.FINISHED, finalizada.getCurrentStep());
        assertEquals(ConversationStatus.FINISHED, finalizada.getStatus());
    }

    @Test
    void deveLancarExcecaoAoAvancarConversaFinalizada() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);
        for (int i = 0; i < 6; i++) {
            conversationApplicationService.avancarEtapa(conversation.getId());
        }

        assertThrows(IllegalStateException.class, () ->
            conversationApplicationService.avancarEtapa(conversation.getId()));
    }

    @Test
    void deveVoltarEtapa() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);
        conversationApplicationService.avancarEtapa(conversation.getId());
        conversationApplicationService.avancarEtapa(conversation.getId());

        Conversation atualizado = conversationApplicationService.voltarEtapa(conversation.getId());

        assertEquals(ConversationStep.SELECT_SERVICE, atualizado.getCurrentStep());
        assertEquals(ConversationStatus.ACTIVE, atualizado.getStatus());
    }

    @Test
    void deveLancarExcecaoAoVoltarNoIdle() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        assertThrows(IllegalStateException.class, () ->
            conversationApplicationService.voltarEtapa(conversation.getId()));
    }

    @Test
    void deveResetarConversa() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);
        conversationApplicationService.avancarEtapa(conversation.getId());
        conversationApplicationService.atualizarCampos(
            conversation.getId(),
            serviceId,
            professionalId,
            "2026-07-20",
            "10:00",
            "10:30",
            reservationId,
            appointmentId
        );

        Conversation resetada = conversationApplicationService.resetarConversa(conversation.getId());

        assertEquals(ConversationStep.IDLE, resetada.getCurrentStep());
        assertEquals(ConversationStatus.ACTIVE, resetada.getStatus());
        assertNull(resetada.getSelectedServiceId());
        assertNull(resetada.getSelectedProfessionalId());
        assertNull(resetada.getSelectedDate());
        assertNull(resetada.getSelectedStartTime());
        assertNull(resetada.getSelectedEndTime());
        assertNull(resetada.getReservationId());
        assertNull(resetada.getAppointmentId());
    }

    @Test
    void deveCancelarConversaSemRemoverDoRepositorio() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        Conversation cancelada = conversationApplicationService.cancelarConversa(conversation.getId());

        assertEquals(ConversationStep.FINISHED, cancelada.getCurrentStep());
        assertEquals(ConversationStatus.CANCELLED, cancelada.getStatus());
        assertTrue(conversationApplicationService.buscarPorId(conversation.getId()).isPresent());
    }

    @Test
    void deveBloquearAlteracoesEmConversaCancelada() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);
        conversationApplicationService.cancelarConversa(conversation.getId());

        assertThrows(IllegalStateException.class, () ->
            conversationApplicationService.avancarEtapa(conversation.getId()));
        assertThrows(IllegalStateException.class, () ->
            conversationApplicationService.voltarEtapa(conversation.getId()));
        assertThrows(IllegalStateException.class, () ->
            conversationApplicationService.resetarConversa(conversation.getId()));
        assertThrows(IllegalStateException.class, () ->
            conversationApplicationService.atualizarCampos(
                conversation.getId(),
                serviceId,
                null,
                null,
                null,
                null,
                null,
                null
            ));
    }

    @Test
    void deveVerificarExistencia() {
        Conversation conversation = conversationApplicationService.criarConversa(customerId);

        assertTrue(conversationApplicationService.existe(conversation.getId()));
        assertFalse(conversationApplicationService.existe(ConversationId.generate()));
        assertFalse(conversationApplicationService.existe(null));
    }

    @Test
    void deveLancarQuandoConversaNaoExiste() {
        assertThrows(NoSuchElementException.class, () ->
            conversationApplicationService.avancarEtapa(ConversationId.generate()));
    }

    private static class IgnoringWhatsAppAdapter implements WhatsAppAdapter {
        @Override
        public Optional<IncomingMessage> receberMensagem(String payload) {
            return Optional.empty();
        }

        @Override
        public void enviarMensagem(String numero, String texto) {
        }
    }
}
