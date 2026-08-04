package com.troquim_bot.application.booking;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.ConsultarDisponibilidade;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.availability.RelogioDoNegocio;
import com.troquim_bot.business.BusinessCalendar;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.repository.InMemoryBusinessCalendarRepository;
import com.troquim_bot.repository.InMemoryCustomerRepository;
import com.troquim_bot.repository.InMemoryProfessionalRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.repository.InMemoryServiceRepository;
import com.troquim_bot.repository.ReservationRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.support.InMemoryBookingIdempotencyStore;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Semântica do caso de uso: conflito, falha técnica e idempotência por COMANDO.
 *
 * LIMITE: usa store em memória, então cobre apenas o comportamento SEQUENCIAL. Concorrência
 * e rollback só são provados em {@code BookingIdempotencyPostgresTest}, contra Postgres
 * real — um fake em memória não prova nada sobre commit incerto.
 */
@DisplayName("Booking - conflito, falha técnica e idempotência de comando")
class BookingFalhaTecnicaTest {

    private static final ProfessionalId PROFISSIONAL = BookingIds.PROFISSIONAL_PADRAO;
    private static final UUID TENANT = TestTenants.PILOT.getValue();
    private static final String TELEFONE = "5511999990000";

    // ==================== conflito x falha técnica ====================

    @Test
    @DisplayName("horário ocupado por OUTRO comando devolve INDISPONIVEL")
    void conflitoRealDevolveIndisponivel() {
        Cenario c = new Cenario(new InMemoryAppointmentRepository());
        LocalDate dia = proximaQuarta();

        assertTrue(c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(10, 0)).isConfirmado());

        BookingResult segundo = c.confirmar("cmd-2", "5511988887777", dia, LocalTime.of(10, 0));

        assertEquals(BookingResult.Status.INDISPONIVEL, segundo.status());
        assertTrue(segundo.isConflito());
        assertFalse(segundo.isFalhaTecnica());
        assertEquals(1, c.appointments.findAll().size());
    }

    @Test
    @DisplayName("falha de escrita LANÇA, para que a reivindicação role back junto")
    void falhaDeEscritaLanca() {
        Cenario c = new Cenario(new RepositorioQueFalhaAoSalvar());

        assertThrows(BookingPersistenceException.class,
                () -> c.confirmar("cmd-1", TELEFONE, proximaQuarta(), LocalTime.of(10, 0)));

        // Nenhum desfecho gravado: se tivesse, o retry veria um comando "concluído" que
        // nunca produziu agendamento.
        assertEquals(0, c.idempotency.totalConcluidos());
        assertTrue(c.reservations.findAll().stream().noneMatch(r -> r.isAtivo()),
                "A reserva órfã precisa ter sido compensada");
    }

    @Test
    @DisplayName("a mensagem de falha técnica não afirma nada sobre a agenda")
    void mensagemDeFalhaTecnicaENeutra() {
        String texto = BookingResult.MENSAGEM_FALHA_TECNICA.toLowerCase();

        for (String proibido : List.of("continua livre", "continua disponível", "ainda está livre",
                "não foi criado", "nada foi criado", "nada foi agendado", "não foi agendado",
                "não foi salvo", "ocupado", "indisponível", "escolha outro")) {
            assertFalse(texto.contains(proibido),
                    "Falha técnica não pode afirmar \"" + proibido + "\"");
        }
        assertTrue(texto.contains("não conseguimos concluir"));
        assertTrue(texto.contains("tente novamente"));
    }

    @Test
    @DisplayName("conflito e falha técnica nunca compartilham a mesma mensagem")
    void mensagensDistintasPorSemantica() {
        Cenario c = new Cenario(new InMemoryAppointmentRepository());
        LocalDate dia = proximaQuarta();
        c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(10, 0));

        BookingResult conflito = c.confirmar("cmd-2", "5511988887777", dia, LocalTime.of(10, 0));

        assertNotEquals(BookingResult.MENSAGEM_FALHA_TECNICA, conflito.mensagem());
        // Conflito PODE falar de ocupação — ali existe evidência observada.
        assertTrue(conflito.mensagem().toLowerCase().contains("ocupado"));
    }

    // ==================== idempotência por comando ====================

    @Test
    @DisplayName("mesmo comando sequencial devolve o MESMO appointment, sem duplicar")
    void mesmoComandoSequencial() {
        Cenario c = new Cenario(new InMemoryAppointmentRepository());
        LocalDate dia = proximaQuarta();

        BookingResult primeira = c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(14, 0));
        BookingResult segunda = c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(14, 0));

        assertTrue(primeira.isConfirmado());
        assertTrue(segunda.isConfirmado());
        assertEquals(1, c.appointments.findAll().size());

        AppointmentId gravado = c.appointments.findAll().get(0).getId();
        AppointmentId doRecibo = c.idempotency
                .buscar(c.chave("cmd-1", TELEFONE, dia, LocalTime.of(14, 0)).valor())
                .orElseThrow().appointmentId().orElseThrow();
        assertEquals(gravado, doRecibo, "O retry precisa apontar para o MESMO appointment");
    }

    @Test
    @DisplayName("REGRA DO MVP: mesma base com dados diferentes é REJEITADA após confirmar")
    void mesmaBaseDadosDiferentesERejeitada() {
        Cenario c = new Cenario(new InMemoryAppointmentRepository());
        LocalDate dia = proximaQuarta();

        BookingResult primeira = c.confirmar("token-A", TELEFONE, dia, LocalTime.of(9, 0));
        BookingResult segunda = c.confirmar("token-A", TELEFONE, dia, LocalTime.of(11, 0));

        assertTrue(primeira.isConfirmado());
        assertTrue(segunda.isSessaoJaConfirmada(),
                "Um flow_token vale por UM agendamento: " + segunda);
        // Nem devolve o anterior em silêncio, nem cria outro.
        assertNotEquals(BookingResult.Status.CONFIRMADO, segunda.status());
        assertEquals(1, c.appointments.findAll().size(),
                "A segunda escolha não pode criar um segundo agendamento");
    }

    @Test
    @DisplayName("a rejeição orienta a abrir um novo Flow, sem falar em horário nem em retry")
    void mensagemDeSessaoEsgotada() {
        String texto = BookingResult.MENSAGEM_SESSAO_JA_CONFIRMADA.toLowerCase();

        assertTrue(texto.contains("já foi concluído"));
        assertTrue(texto.contains("peça a agenda novamente"), "Deve orientar a abrir novo Flow");
        for (String proibido : List.of("ocupado", "escolha outro", "tente novamente",
                "continua livre", "problema técnico")) {
            assertFalse(texto.contains(proibido),
                    "Sessão esgotada não é conflito nem falha técnica: \"" + proibido + "\"");
        }
        assertNotEquals(BookingResult.MENSAGEM_FALHA_TECNICA,
                BookingResult.MENSAGEM_SESSAO_JA_CONFIRMADA);
    }

    @Test
    @DisplayName("conflito NÃO consome a base: o cliente ainda não agendou nada")
    void conflitoNaoConsomeABase() {
        Cenario c = new Cenario(new InMemoryAppointmentRepository());
        LocalDate dia = proximaQuarta();
        // Outro cliente ocupa 10:00.
        c.confirmar("token-outro", "5511988887777", dia, LocalTime.of(10, 0));

        // O dono do token-B bate no conflito...
        BookingResult conflito = c.confirmar("token-B", TELEFONE, dia, LocalTime.of(10, 0));
        assertTrue(conflito.isConflito());

        // ...e ainda consegue agendar outro horário com o MESMO Flow.
        BookingResult segunda = c.confirmar("token-B", TELEFONE, dia, LocalTime.of(11, 0));
        assertTrue(segunda.isConfirmado(),
                "Perder um horário para outra pessoa não pode gastar o Flow do cliente");
    }

    @Test
    @DisplayName("comando NOVO para slot já ocupado devolve INDISPONIVEL de forma estável")
    void comandoNovoParaSlotOcupado() {
        Cenario c = new Cenario(new InMemoryAppointmentRepository());
        LocalDate dia = proximaQuarta();
        c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(10, 0));

        BookingResult conflito = c.confirmar("cmd-2", "5511988887777", dia, LocalTime.of(10, 0));
        // Repetir o comando de conflito devolve a MESMA resposta, sem varrer a agenda.
        BookingResult repetido = c.confirmar("cmd-2", "5511988887777", dia, LocalTime.of(10, 0));

        assertEquals(BookingResult.Status.INDISPONIVEL, conflito.status());
        assertEquals(BookingResult.Status.INDISPONIVEL, repetido.status());
        assertEquals(1, c.appointments.findAll().size());
    }

    @Test
    @DisplayName("retry após falha técnica reivindica de novo e confirma, sem duplicar")
    void retryAposFalhaTecnica() {
        FalhaApenasNaPrimeiraEscrita appointments = new FalhaApenasNaPrimeiraEscrita();
        Cenario c = new Cenario(appointments);
        LocalDate dia = proximaQuarta();

        assertThrows(BookingPersistenceException.class,
                () -> c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(16, 0)));
        // Simula o que o banco faria no rollback: a reivindicação some.
        c.idempotency.desfazerReivindicacaoNaoConcluida(
                c.chave("cmd-1", TELEFONE, dia, LocalTime.of(16, 0)));

        BookingResult segunda = c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(16, 0));
        BookingResult terceira = c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(16, 0));

        assertTrue(segunda.isConfirmado());
        assertTrue(terceira.isConfirmado());
        assertEquals(1, appointments.findAll().size());
    }

    @Test
    @DisplayName("o retry NÃO depende de listar agendamentos do cliente")
    void retryNaoDependeDeListarAgendamentos() {
        // Repositório cego para consultas por cliente: se o reconhecimento do retry
        // dependesse dessa listagem, este teste duplicaria o agendamento.
        RepositorioSemBuscaPorCliente appointments = new RepositorioSemBuscaPorCliente();
        Cenario c = new Cenario(appointments);
        LocalDate dia = proximaQuarta();

        c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(13, 0));
        BookingResult repetido = c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(13, 0));

        assertTrue(repetido.isConfirmado());
        assertEquals(1, appointments.findAll().size(),
                "A chave de comando deve reconhecer o retry sozinha");
    }

    @Test
    @DisplayName("fingerprint divergente na mesma chave é recusado")
    void fingerprintDivergenteFalhaAlto() {
        Cenario c = new Cenario(new InMemoryAppointmentRepository());
        LocalDate dia = proximaQuarta();
        BookingCommandKey original = c.chave("cmd-1", TELEFONE, dia, LocalTime.of(10, 0));
        c.confirmar("cmd-1", TELEFONE, dia, LocalTime.of(10, 0));

        BookingCommandKey forjada = new BookingCommandKey(
                original.businessId(), original.base(), original.valor(), "0".repeat(64));

        // Tipada desde a introdução de BookingCommandKeyReutilizadaException: o mesmo
        // detector serve de() (aqui, só ocorreria por colisão de SHA-256) e
        // deChaveExclusiva() (onde é o caso ESPERADO de reuso de Idempotency-Key).
        assertThrows(BookingCommandKeyReutilizadaException.class, () -> c.booking.confirmarEm(
                TELEFONE, "Ana Souza", "unha", PROFISSIONAL, dia, LocalTime.of(10, 0),
                Duration.ofHours(1), forjada));
    }

    @Test
    @DisplayName("confirmarEm exige a chave de comando")
    void chaveObrigatoria() {
        Cenario c = new Cenario(new InMemoryAppointmentRepository());
        assertThrows(IllegalArgumentException.class, () -> c.booking.confirmarEm(
                TELEFONE, "Ana Souza", "unha", PROFISSIONAL, proximaQuarta(),
                LocalTime.of(10, 0), Duration.ofHours(1), null));
    }

    // ==================== cenário ====================

    /** Monta o caso de uso com repositórios em memória e expõe o que o teste inspeciona. */
    private static final class Cenario {
        private final AppointmentRepository appointments;
        private final ReservationRepository reservations = new InMemoryReservationRepository();
        private final InMemoryBookingIdempotencyStore idempotency = new InMemoryBookingIdempotencyStore();
        private final BookingApplicationService booking;

        Cenario(AppointmentRepository appointments) {
            this.appointments = appointments;

            // Catálogo, calendário e disponibilidade REAIS para o par sintético
            // (BookingIds.serviceId("unha"), PROFISSIONAL_PADRAO): o caminho tipado agora
            // revalida o slot via ConsultarDisponibilidade dentro da seção crítica, então
            // este cenário precisa que o par exista de fato — janela larga em todos os
            // dias da semana para não depender de em qual dia o teste roda.
            InMemoryServiceRepository servicos = new InMemoryServiceRepository();
            InMemoryProfessionalRepository profissionais = new InMemoryProfessionalRepository();
            com.troquim_bot.service.ServiceId servicoId = BookingIds.serviceId("unha");
            servicos.salvar(Service.novoSemPreco(servicoId, TestTenants.PILOT, "Unha", null,
                    ServiceDuration.ofMinutes(60)));
            profissionais.salvar(new Professional(PROFISSIONAL, TestTenants.PILOT, "Profissional Padrão",
                    Set.of(servicoId), Set.of(), "+5511900000000"));

            InMemoryBusinessCalendarRepository calendarios = new InMemoryBusinessCalendarRepository();
            InMemoryAvailabilityRepository disponibilidades = new InMemoryAvailabilityRepository();
            IntervaloDeHorario janelaAmpla = IntervaloDeHorario.de(LocalTime.of(7, 0), LocalTime.of(21, 0));
            Map<DiaSemana, List<IntervaloDeHorario>> semanaAberta = new EnumMap<>(DiaSemana.class);
            for (DiaSemana dia : DiaSemana.values()) {
                semanaAberta.put(dia, List.of(janelaAmpla));
            }
            calendarios.salvar(new BusinessCalendar(TestTenants.PILOT, BusinessHours.deSemana(semanaAberta)));
            for (DiaSemana dia : DiaSemana.values()) {
                disponibilidades.salvar(new com.troquim_bot.availability.Availability(
                        AvailabilityId.generate(), TestTenants.PILOT, PROFISSIONAL, dia, janelaAmpla));
            }

            ConsultarDisponibilidade consultarDisponibilidade = new ConsultarDisponibilidade(
                    new ConsultarCatalogo(servicos, profissionais), calendarios, disponibilidades,
                    appointments, new RelogioDoNegocio());

            this.booking = new BookingApplicationService(TestTenants.pilot(),
                    new ReservationApplicationService(reservations),
                    new AppointmentApplicationService(appointments, reservations),
                    new CustomerProfileService(new InMemoryCustomerRepository(), TestTenants.pilot()),
                    idempotency,
                    new com.troquim_bot.infrastructure.persistence.InMemoryBookingSlotCriticalSection(),
                    consultarDisponibilidade);
        }

        BookingCommandKey chave(String base, String telefone, LocalDate data, LocalTime hora) {
            return BookingCommandKey.de(base, TENANT, telefone,
                    BookingIds.serviceId("unha"), PROFISSIONAL, data, hora);
        }

        BookingResult confirmar(String base, String telefone, LocalDate data, LocalTime hora) {
            return booking.confirmarEm(telefone, "Ana Souza", "unha", PROFISSIONAL, data, hora,
                    Duration.ofHours(1), chave(base, telefone, data, hora));
        }
    }

    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != java.time.DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    // ==================== duplos ====================

    /** Falha transitória: a primeira escrita estoura, as seguintes funcionam. */
    private static final class FalhaApenasNaPrimeiraEscrita extends InMemoryAppointmentRepository {
        private boolean jaFalhou = false;

        @Override
        public Appointment save(Appointment appointment) {
            if (!jaFalhou) {
                jaFalhou = true;
                throw new IllegalStateException("Falha transitória de persistência");
            }
            return super.save(appointment);
        }
    }

    /** Cego para consultas por cliente — prova que o retry não depende delas. */
    private static final class RepositorioSemBuscaPorCliente extends InMemoryAppointmentRepository {
        @Override
        public List<Appointment> findByCustomerId(CustomerId customerId) {
            return List.of();
        }
    }

    /** Lê normalmente, recusa toda escrita: banco indisponível depois da checagem de agenda. */
    private static final class RepositorioQueFalhaAoSalvar implements AppointmentRepository {
        @Override
        public Appointment save(Appointment appointment) {
            throw new IllegalStateException("Falha simulada de persistência");
        }

        @Override
        public List<Appointment> findByBusinessIdAndProfessionalIdAndDate(
                com.troquim_bot.business.BusinessId businessId,
                ProfessionalId professionalId, java.time.LocalDate date) {
            return List.of();
        }

        @Override
        public List<Appointment> findByBusinessId(com.troquim_bot.business.BusinessId businessId) {
            return List.of();
        }

        @Override
        public Appointment findById(AppointmentId id) {
            return null;
        }

        @Override
        public boolean exists(AppointmentId id) {
            return false;
        }

        @Override
        public List<Appointment> findAll() {
            return List.of();
        }

        @Override
        public List<Appointment> findByProfessionalIdAndDate(ProfessionalId professionalId,
                                                             LocalDate date) {
            return List.of();
        }

        @Override
        public List<Appointment> findByCustomerId(CustomerId customerId) {
            return List.of();
        }

        @Override
        public void delete(AppointmentId id) {
        }
    }
}
