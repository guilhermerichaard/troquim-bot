package com.troquim_bot.conversation;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryConversationStateRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.repository.ReservationRepository;
import com.troquim_bot.support.OptionalBeans;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.support.InMemoryBookingIdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A conversa textual precisa preservar a MESMA distinção do WhatsApp Flow.
 *
 * Os dois canais atendem o mesmo cliente e não podem divergir sobre o que aconteceu —
 * por isso ambos usam {@link BookingResult#MENSAGEM_FALHA_TECNICA}. Este teste percorre
 * o menu de verdade, até a confirmação, com a persistência quebrada.
 */
@DisplayName("Conversa textual - mensagens de falha técnica e de conflito")
class StrictMvpFalhaTecnicaMensagemTest {

    private static final String NUMERO = "5511999990000";

    private FalhaControlada appointments;
    private ConversationStateService conversationStateService;
    private StrictMvpMenuService menu;

    @BeforeEach
    void setUp() {
        ReservationRepository reservations = new InMemoryReservationRepository();
        appointments = new FalhaControlada();

        BookingApplicationService booking = new BookingApplicationService(TestTenants.pilot(),
                new ReservationApplicationService(reservations),
                new AppointmentApplicationService(appointments, reservations),
                new CustomerProfileService(new InMemoryCustomerRepository(), TestTenants.pilot()),
                new InMemoryBookingIdempotencyStore());

        conversationStateService = new ConversationStateService(new InMemoryConversationStateRepository());
        menu = new StrictMvpMenuService(
                conversationStateService,
                new AvailabilityApplicationService(TestTenants.pilot()),
                booking,
                OptionalBeans.ausente(),
                "STRICT_MVP");
    }

    @Test
    @DisplayName("falha técnica usa o texto canônico e não afirma nada sobre a agenda")
    void mensagemDeFalhaTecnicaENeutra() {
        appointments.falharSempre = true;

        String resposta = percorrerAteConfirmar();

        assertTrue(resposta.startsWith(BookingResult.MENSAGEM_FALHA_TECNICA),
                "A conversa precisa usar o texto canônico compartilhado: " + resposta);

        String texto = resposta.toLowerCase();
        for (String proibido : List.of("continua livre", "continua disponivel", "continua disponível",
                "nao foi criado", "não foi criado", "nada foi agendado", "nao foi agendado",
                "ocupado", "escolha outro horario", "escolha outro horário")) {
            assertFalse(texto.contains(proibido),
                    "Falha técnica não pode afirmar \"" + proibido + "\": " + resposta);
        }

        assertTrue(texto.contains("tente novamente"), "Deve permitir nova tentativa");
    }

    @Test
    @DisplayName("conflito real continua pedindo outro horário — ali há evidência")
    void mensagemDeConflitoPedeOutroHorario() {
        // Primeiro agendamento entra normalmente.
        percorrerAteConfirmar();
        assertEquals(1, appointments.findAll().size());

        // Outro cliente tenta exatamente o mesmo slot.
        conversationStateService.limparEstado("5511988887777");
        String resposta = percorrerAteConfirmar("5511988887777");

        String texto = resposta.toLowerCase();
        assertTrue(texto.contains("ocupado"), "Conflito deve dizer que o horário não está livre");
        assertTrue(texto.contains("outro horario") || texto.contains("outro horário"),
                "Conflito deve pedir outro horário: " + resposta);
        assertFalse(resposta.startsWith(BookingResult.MENSAGEM_FALHA_TECNICA));
        assertEquals(1, appointments.findAll().size(), "O conflito não pode criar um segundo");
    }

    @Test
    @DisplayName("sucesso só é anunciado após a persistência concluir")
    void sucessoSomenteAposPersistir() {
        String resposta = percorrerAteConfirmar();

        assertTrue(resposta.contains("registrado com sucesso"), resposta);
        assertEquals(1, appointments.findAll().size(),
                "A mensagem de sucesso exige Appointment persistido");
    }

    @Test
    @DisplayName("repetir após falha técnica não duplica o agendamento")
    void repeticaoAposFalhaTecnicaNaoDuplica() {
        appointments.falharNaProxima = true;

        String primeira = percorrerAteConfirmar();
        assertTrue(primeira.startsWith(BookingResult.MENSAGEM_FALHA_TECNICA));
        assertTrue(appointments.findAll().isEmpty());

        // O cliente faz o que a mensagem diz: tenta de novo, na mesma conversa.
        String segunda = enviar(NUMERO, "1");
        assertTrue(segunda.contains("registrado com sucesso"), segunda);

        assertEquals(1, appointments.findAll().size(),
                "O retry após falha técnica não pode gerar agendamento duplicado");
    }

    // ==================== helpers ====================

    private String percorrerAteConfirmar() {
        return percorrerAteConfirmar(NUMERO);
    }

    private String percorrerAteConfirmar(String numero) {
        enviar(numero, "oi");
        enviar(numero, "1");                     // agendar
        enviar(numero, "cabelo");                // serviço
        enviar(numero, diaFuturoComAgenda());    // dia — nunca hoje (ver o método)
        enviar(numero, "1");                     // primeiro horário livre
        enviar(numero, "Ana Souza");
        return enviar(numero, "1");              // confirma
    }

    /**
     * Nome de um dia da semana que é SEMPRE futuro e SEMPRE tem agenda.
     *
     * Um dia fixo ("quarta") tornava o teste dependente do relógio: quando aquele dia
     * calhava de ser hoje, {@code proximaDataPara} resolvia para a data de HOJE e
     * {@code AvailabilityApplicationService} descartava os horários já passados. Depois
     * do último slot do dia a agenda ficava vazia, a conversa travava em AGUARDANDO_DIA
     * e os testes falhavam por hora do relógio — não por regra de negócio.
     *
     * Partir de amanhã garante uma data futura (nenhum horário no passado a filtrar), e
     * pular domingo garante um dia com horários no gabarito. Determinístico para
     * qualquer data de execução, sem tocar em nenhuma regra de produção.
     */
    private static String diaFuturoComAgenda() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            data = data.plusDays(1);
        }
        return switch (data.getDayOfWeek()) {
            case MONDAY -> "segunda";
            case TUESDAY -> "terca";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY -> "sabado";
            case SUNDAY -> throw new IllegalStateException("domingo nao tem agenda");
        };
    }

    private String enviar(String numero, String mensagem) {
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        return menu.processarMenu(numero, mensagem, state);
    }

    /** Repositório com falha controlável: sempre, uma vez só, ou nunca. */
    private static final class FalhaControlada extends InMemoryAppointmentRepository {
        private boolean falharSempre = false;
        private boolean falharNaProxima = false;

        @Override
        public Appointment save(Appointment appointment) {
            if (falharSempre) {
                throw new IllegalStateException("Falha simulada de persistência");
            }
            if (falharNaProxima) {
                falharNaProxima = false;
                throw new IllegalStateException("Falha transitória de persistência");
            }
            return super.save(appointment);
        }
    }
}
