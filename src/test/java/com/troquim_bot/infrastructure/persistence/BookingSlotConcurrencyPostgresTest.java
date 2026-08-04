package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.application.booking.BookingSlotCriticalSection;
import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessCalendar;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.BusinessCalendarRepository;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.support.TestTenants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concorrência REAL do caminho tipado, contra PostgreSQL real (Testcontainers), com threads
 * de verdade — a seção crítica de slot só se prova sob concorrência genuína, nunca "não
 * lançou exceção": toda asserção aqui conta linhas de verdade no banco.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("azure")
@DisplayName("Seção crítica de slot - concorrência real (PostgreSQL)")
class BookingSlotConcurrencyPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("TROQUIM_PILOT_BUSINESS_ID", () -> TestTenants.PILOT.getValue().toString());
        registry.add("TROQUIM_ADMIN_API_KEY", () -> "test-admin-key-for-azure");
    }

    @Autowired
    private BookingApplicationService booking;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServiceRepository servicos;
    @Autowired
    private ProfessionalRepository profissionais;
    @Autowired
    private BusinessCalendarRepository calendarios;
    @Autowired
    private AvailabilityRepository disponibilidades;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private BookingSlotCriticalSection criticalSection;
    @Autowired
    private com.troquim_bot.application.booking.BookingIdempotencyStore idempotencyStore;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private record Negocio(BusinessId businessId, ServiceId servicoId, ProfessionalId profissionalId) {
    }

    private Negocio provisionar() {
        BusinessId businessId = BusinessId.from(UUID.randomUUID());
        ServiceId servicoId = ServiceId.generate();
        ProfessionalId profissionalId = ProfessionalId.generate();

        Business negocio = new Business(businessId, "Negócio Concorrência", null, null);
        negocio.ativar();
        businessRepository.save(negocio);

        servicos.salvar(Service.novoSemPreco(servicoId, businessId, "Corte", null,
                ServiceDuration.ofMinutes(60)));
        profissionais.salvar(new Professional(profissionalId, businessId, "Profissional",
                Set.of(servicoId), Set.of(), "+5511900000000"));

        IntervaloDeHorario janela = IntervaloDeHorario.de(LocalTime.of(7, 0), LocalTime.of(21, 0));
        Map<DiaSemana, List<IntervaloDeHorario>> semana = new EnumMap<>(DiaSemana.class);
        for (DiaSemana dia : DiaSemana.values()) {
            semana.put(dia, List.of(janela));
        }
        calendarios.salvar(new BusinessCalendar(businessId, BusinessHours.deSemana(semana)));
        for (DiaSemana dia : DiaSemana.values()) {
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), businessId,
                    profissionalId, dia, janela));
        }

        return new Negocio(businessId, servicoId, profissionalId);
    }

    /** Reaproveita o profissional/serviço/calendário de um negócio já provisionado, com um novo profissional. */
    private ProfessionalId outroProfissionalNoMesmoNegocio(Negocio negocio) {
        ProfessionalId outro = ProfessionalId.generate();
        profissionais.salvar(new Professional(outro, negocio.businessId(), "Outro Profissional",
                Set.of(negocio.servicoId()), Set.of(), "+5511900000001"));
        IntervaloDeHorario janela = IntervaloDeHorario.de(LocalTime.of(7, 0), LocalTime.of(21, 0));
        for (DiaSemana dia : DiaSemana.values()) {
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), negocio.businessId(),
                    outro, dia, janela));
        }
        return outro;
    }

    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    private BookingCommandKey chave(Negocio n, String base, String telefone, LocalDate data, LocalTime hora) {
        return BookingCommandKey.de(base, n.businessId().getValue(), telefone,
                n.servicoId(), n.profissionalId(), data, hora);
    }

    private BookingResult confirmar(Negocio n, ProfessionalId profissional, String base, String telefone,
                                    LocalDate data, LocalTime hora) {
        BookingCommandKey chave = BookingCommandKey.de(base, n.businessId().getValue(), telefone,
                n.servicoId(), profissional, data, hora);
        return booking.confirmarEm(telefone, "Cliente " + base, n.servicoId(), "Corte",
                profissional, data, hora, java.time.Duration.ofMinutes(60), chave);
    }

    /** Duas tarefas distintas, largada sincronizada. */
    private List<BookingResult> emParaleloDistintos(Callable<BookingResult> a, Callable<BookingResult> b)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<BookingResult>> futuros = List.of(
                    pool.submit(() -> executarTolerante(largada, a)),
                    pool.submit(() -> executarTolerante(largada, b)));
            largada.countDown();
            List<BookingResult> resultados = new java.util.ArrayList<>();
            for (Future<BookingResult> f : futuros) {
                resultados.add(f.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    private static BookingResult executarTolerante(CountDownLatch largada, Callable<BookingResult> tarefa) {
        try {
            largada.await(5, TimeUnit.SECONDS);
            return tarefa.call();
        } catch (Exception e) {
            return BookingResult.falhaTecnica();
        }
    }

    // ==================== 3 e 4. comandos diferentes, mesmo slot, concorrentes ====================

    @Test
    @DisplayName("3/4. dois comandos DIFERENTES, mesmo Business+Professional+Date+horário, concorrentes: "
            + "exatamente 1 CONFIRMADO, 1 INDISPONIVEL, 1 Appointment ativo")
    void doisComandosDiferentesMesmoSlotConcorrentes() throws Exception {
        Negocio n = provisionar();
        LocalDate dia = proximaQuarta();
        LocalTime hora = LocalTime.of(10, 0);
        int antes = appointments.findAll().size();

        List<BookingResult> resultados = emParaleloDistintos(
                () -> confirmar(n, n.profissionalId(), "cmd-a", "5511977000001", dia, hora),
                () -> confirmar(n, n.profissionalId(), "cmd-b", "5511977000002", dia, hora));

        long confirmados = resultados.stream().filter(BookingResult::isConfirmado).count();
        long indisponiveis = resultados.stream()
                .filter(r -> r.status() == BookingResult.Status.INDISPONIVEL).count();

        assertEquals(1, confirmados, "Exatamente um comando pode vencer o slot: " + resultados);
        assertEquals(1, indisponiveis, "O perdedor precisa receber INDISPONIVEL: " + resultados);
        assertEquals(antes + 1, appointments.findAll().size(), "Exatamente um Appointment novo deve existir");

        long ativosNoSlot = appointments.findAll().stream()
                .filter(a -> a.getBusinessId().equals(n.businessId())
                        && a.getProfessionalId().equals(n.profissionalId())
                        && a.getDate().equals(dia) && a.isAtivo())
                .count();
        assertEquals(1, ativosNoSlot, "Exatamente um Appointment ATIVO no slot disputado");
    }

    // ==================== 5. mesmo comando concorrente ====================

    @Test
    @DisplayName("5. o MESMO comando executado simultaneamente não duplica: mesmo desfecho, um Appointment")
    void mesmoComandoConcorrenteNaoDuplica() throws Exception {
        Negocio n = provisionar();
        LocalDate dia = proximaQuarta();
        LocalTime hora = LocalTime.of(11, 0);
        String telefone = "5511977000003";
        int antes = appointments.findAll().size();

        List<BookingResult> resultados = emParaleloDistintos(
                () -> confirmar(n, n.profissionalId(), "cmd-mesmo", telefone, dia, hora),
                () -> confirmar(n, n.profissionalId(), "cmd-mesmo", telefone, dia, hora));

        assertTrue(resultados.stream().allMatch(BookingResult::isConfirmado),
                "As duas execuções do MESMO comando devem confirmar: " + resultados);
        assertEquals(antes + 1, appointments.findAll().size(), "Nenhuma duplicação de Appointment");
    }

    // ==================== 6. mesmo horário, negócios diferentes ====================

    @Test
    @DisplayName("6. mesmo horário em negócios DIFERENTES: ambos confirmam")
    void mesmoHorarioNegociosDiferentesAmbosConfirmam() throws Exception {
        Negocio a = provisionar();
        Negocio b = provisionar();
        LocalDate dia = proximaQuarta();
        LocalTime hora = LocalTime.of(12, 0);

        List<BookingResult> resultados = emParaleloDistintos(
                () -> confirmar(a, a.profissionalId(), "cmd-neg-a", "5511977000004", dia, hora),
                () -> confirmar(b, b.profissionalId(), "cmd-neg-b", "5511977000005", dia, hora));

        assertTrue(resultados.stream().allMatch(BookingResult::isConfirmado),
                "Negócios diferentes não podem bloquear um ao outro: " + resultados);

        assertEquals(1, appointments.findAll().stream()
                .filter(x -> x.getBusinessId().equals(a.businessId())).count());
        assertEquals(1, appointments.findAll().stream()
                .filter(x -> x.getBusinessId().equals(b.businessId())).count());
    }

    // ==================== 7. mesmo horário, profissionais diferentes ====================

    @Test
    @DisplayName("7. mesmo horário no MESMO negócio, profissionais DIFERENTES: ambos confirmam")
    void mesmoHorarioProfissionaisDiferentesAmbosConfirmam() throws Exception {
        Negocio n = provisionar();
        ProfessionalId outro = outroProfissionalNoMesmoNegocio(n);
        LocalDate dia = proximaQuarta();
        LocalTime hora = LocalTime.of(15, 0);

        List<BookingResult> resultados = emParaleloDistintos(
                () -> confirmar(n, n.profissionalId(), "cmd-prof-1", "5511977000006", dia, hora),
                () -> confirmar(n, outro, "cmd-prof-2", "5511977000007", dia, hora));

        assertTrue(resultados.stream().allMatch(BookingResult::isConfirmado),
                "Profissionais diferentes do mesmo negócio não se bloqueiam: " + resultados);

        assertEquals(1, appointments.findAll().stream()
                .filter(x -> x.getProfessionalId().equals(n.profissionalId())).count());
        assertEquals(1, appointments.findAll().stream()
                .filter(x -> x.getProfessionalId().equals(outro)).count());
    }

    // ==================== 15. rollback após reivindicar ====================

    @Test
    @DisplayName("15. falha após reivindicar o comando faz rollback completo: sem recibo, sem Appointment, "
            + "e o retry consegue reivindicar de novo")
    void falhaAposReivindicarFazRollbackCompleto() {
        Negocio n = provisionar();
        LocalDate dia = proximaQuarta();
        LocalTime hora = LocalTime.of(17, 0);
        String telefone = "5511977000009";
        BookingCommandKey chave = chave(n, "cmd-rollback", telefone, dia, hora);
        int antes = appointments.findAll().size();

        // Transação que reivindica e CONFIRMA de verdade, e só então é desfeita de
        // propósito — simula uma falha técnica ocorrendo depois da escrita de negócio.
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            confirmar(n, n.profissionalId(), "cmd-rollback", telefone, dia, hora);
            throw new IllegalStateException("falha tecnica proposital apos confirmar");
        })).isInstanceOf(RuntimeException.class);

        assertEquals(antes, appointments.findAll().size(), "Nenhum Appointment pode ter sobrevivido ao rollback");
        assertTrue(idempotencyStore.buscar(chave.valor()).isEmpty(),
                "A reivindicação precisa ter sido desfeita junto com o resto da transação");

        // A MESMA chave volta a funcionar: o comando, do ponto de vista do banco, nunca aconteceu.
        BookingResult retry = confirmar(n, n.profissionalId(), "cmd-rollback", telefone, dia, hora);
        assertTrue(retry.isConfirmado(), "O retry após rollback precisa conseguir reivindicar de novo");
        assertEquals(antes + 1, appointments.findAll().size());
    }

    // ==================== 16. adapter Postgres exige transação existente ====================

    @Test
    @DisplayName("16. o adapter PostgreSQL da seção crítica exige transação existente (MANDATORY)")
    void adapterPostgresExigeTransacaoExistente() {
        assertThat(criticalSection).isInstanceOf(PostgresBookingSlotCriticalSection.class);

        assertThatThrownBy(() -> criticalSection.executar(
                TestTenants.PILOT, com.troquim_bot.professional.ProfessionalId.from(UUID.randomUUID()),
                proximaQuarta(), () -> "nunca deveria rodar sem transação"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
