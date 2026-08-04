package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingIdempotencyOutcome;
import com.troquim_bot.application.booking.BookingIdempotencyRecord;
import com.troquim_bot.application.booking.BookingIdempotencyStore;
import com.troquim_bot.application.booking.RegistrarDesfechoDeBookingSemAgendamento;
import com.troquim_bot.application.booking.BookingResult;
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
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.BusinessCalendarRepository;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compatibilidade da representação PERSISTIDA de {@code booking_idempotency.outcome_status}
 * contra PostgreSQL real (Testcontainers).
 *
 * Recibos gravados antes da introdução de {@link BookingIdempotencyOutcome} (Etapa 5D-B)
 * têm a coluna preenchida com {@code BookingResult.Status.name()}: {@code CONFIRMADO},
 * {@code INDISPONIVEL}, {@code INVALIDO}, {@code SESSAO_JA_CONFIRMADA},
 * {@code FALHA_TECNICA}. O enum novo renomeou dois desses valores
 * ({@code HORARIO_INDISPONIVEL}, {@code PEDIDO_INVALIDO}) — sem uma tradução explícita na
 * Infrastructure, ler uma linha histórica lançaria {@code IllegalArgumentException} de um
 * {@code valueOf} direto. Este teste insere linhas históricas MANUALMENTE (fora do caminho
 * de escrita atual) para provar que a leitura continua funcionando, e prova que a ESCRITA de
 * hoje preserva os nomes históricos na coluna.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("azure")
@DisplayName("Compatibilidade de outcome_status persistido (PostgreSQL real)")
class BookingIdempotencyOutcomeCompatibilityPostgresTest {

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
        registry.add("TROQUIM_PILOT_BUSINESS_ID", () -> "11111111-1111-1111-1111-111111111111");
        registry.add("TROQUIM_ADMIN_API_KEY", () -> "test-admin-key-for-azure");
    }

    @Autowired
    private BookingIdempotencyStore idempotencyStore;
    @Autowired
    private BookingApplicationService booking;
    @Autowired
    private RegistrarDesfechoDeBookingSemAgendamento registrar;
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
    private TransactionTemplate tx;
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Negócio + catálogo isolados por chamada. {@link ServiceId} e {@link ProfessionalId}
     * são gerados frescos (nunca derivados de texto fixo como {@code BookingIds.serviceId})
     * porque {@code services}/{@code professionals} têm PK só no {@code id}, GLOBAL — dois
     * testes reusando o mesmo id determinístico sob negócios diferentes colidiriam.
     */
    private record NegocioCompat(BusinessId businessId, ServiceId servicoId, ProfessionalId profissionalId) {
    }

    private NegocioCompat novoNegocioComCatalogo() {
        BusinessId id = BusinessId.from(UUID.randomUUID());
        ServiceId servicoId = ServiceId.from(UUID.randomUUID());
        ProfessionalId profissionalId = ProfessionalId.from(UUID.randomUUID());
        businessRepository.save(new Business(id, "Negocio Compat " + id, null, null));

        servicos.salvar(Service.novoSemPreco(servicoId, id, "Unha", null, ServiceDuration.ofMinutes(60)));
        profissionais.salvar(new Professional(profissionalId, id, "Profissional Padrão",
                Set.of(servicoId), Set.of(), "+5511900000000"));

        IntervaloDeHorario janelaAmpla = IntervaloDeHorario.de(LocalTime.of(0, 0), LocalTime.of(23, 59));
        Map<DiaSemana, List<IntervaloDeHorario>> semanaAberta = new EnumMap<>(DiaSemana.class);
        for (DiaSemana dia : DiaSemana.values()) {
            semanaAberta.put(dia, List.of(janelaAmpla));
        }
        calendarios.salvar(new BusinessCalendar(id, BusinessHours.deSemana(semanaAberta)));
        for (DiaSemana dia : DiaSemana.values()) {
            disponibilidades.salvar(new Availability(AvailabilityId.generate(), id,
                    profissionalId, dia, janelaAmpla));
        }

        return new NegocioCompat(id, servicoId, profissionalId);
    }

    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    private BookingCommandKey chaveFlow(NegocioCompat negocio, String base, String telefone,
                                        LocalDate data, LocalTime horario) {
        return BookingCommandKey.de(base, negocio.businessId().getValue(), telefone,
                negocio.servicoId(), negocio.profissionalId(), data, horario);
    }

    /** Insere uma linha JÁ CONCLUÍDA diretamente, como se tivesse sido gravada antes da Etapa 5D-B. */
    private void inserirReciboHistorico(BookingCommandKey chave, String outcomeStatusHistorico) {
        tx.executeWithoutResult(s -> entityManager.createNativeQuery("""
                        INSERT INTO booking_idempotency
                            (command_key, business_id, command_base, request_fingerprint,
                             outcome_status, outcome_servico, outcome_data, outcome_horario,
                             outcome_nome, created_at, completed_at)
                        VALUES (:chave, :business, :base, :fingerprint,
                                :status, 'unha', :data, :horario, 'Cliente Historico', :agora, :agora)
                        """)
                .setParameter("chave", chave.valor())
                .setParameter("business", chave.businessId())
                .setParameter("base", chave.base())
                .setParameter("fingerprint", chave.fingerprint())
                .setParameter("status", outcomeStatusHistorico)
                .setParameter("data", proximaQuarta().toString())
                .setParameter("horario", "10:00")
                .setParameter("agora", LocalDateTime.now())
                .executeUpdate());
    }

    private String colunaOutcomeStatus(String commandKey) {
        return tx.execute(s -> (String) entityManager.createNativeQuery(
                        "SELECT outcome_status FROM booking_idempotency WHERE command_key = :chave")
                .setParameter("chave", commandKey)
                .getSingleResult());
    }

    @Test
    @DisplayName("1. recibo histórico INDISPONIVEL é lido sem exceção como HORARIO_INDISPONIVEL")
    void reciboHistoricoIndisponivelELidoComoHorarioIndisponivel() {
        NegocioCompat negocio = novoNegocioComCatalogo();
        BookingCommandKey chave = chaveFlow(negocio, "flow-hist-indisponivel", "5511977880001",
                proximaQuarta(), LocalTime.of(10, 0));
        inserirReciboHistorico(chave, "INDISPONIVEL");

        BookingIdempotencyStore.Claim claim = tx.execute(s -> idempotencyStore.reivindicar(chave));

        assertFalse(claim.reivindicada());
        assertTrue(claim.existente().isPresent(), "Recibo histórico precisa ser reconhecido como já concluído");
        BookingIdempotencyRecord registro = claim.existente().get();
        assertEquals(BookingIdempotencyOutcome.HORARIO_INDISPONIVEL, registro.status());
        assertEquals(BookingResult.Status.INDISPONIVEL, registro.comoResultado().status());

        var buscado = idempotencyStore.buscar(chave.valor()).orElseThrow();
        assertEquals(BookingIdempotencyOutcome.HORARIO_INDISPONIVEL, buscado.status());
    }

    @Test
    @DisplayName("2. recibo histórico INVALIDO é lido sem exceção como PEDIDO_INVALIDO")
    void reciboHistoricoInvalidoELidoComoPedidoInvalido() {
        NegocioCompat negocio = novoNegocioComCatalogo();
        BookingCommandKey chave = chaveFlow(negocio, "flow-hist-invalido", "5511977880002",
                proximaQuarta(), LocalTime.of(11, 0));
        inserirReciboHistorico(chave, "INVALIDO");

        var registro = idempotencyStore.buscar(chave.valor()).orElseThrow();
        assertEquals(BookingIdempotencyOutcome.PEDIDO_INVALIDO, registro.status());
        assertEquals(BookingResult.Status.INVALIDO, registro.comoResultado().status());
    }

    @Test
    @DisplayName("3. conflito de horário concluído HOJE grava outcome_status = INDISPONIVEL (nunca HORARIO_INDISPONIVEL)")
    void conflitoConcluidoHojeGravaValorHistorico() {
        NegocioCompat negocio = novoNegocioComCatalogo();
        LocalDate dia = proximaQuarta();
        LocalTime hora = LocalTime.of(14, 0);

        BookingCommandKey chaveOcupante = chaveFlow(negocio, "flow-conflito-ocupante", "5511977880003", dia, hora);
        BookingResult ocupante = booking.confirmarEm("5511977880003", "Ocupante",
                negocio.servicoId(), "unha", negocio.profissionalId(), dia, hora,
                Duration.ofMinutes(60), chaveOcupante);
        assertTrue(ocupante.isConfirmado());

        BookingCommandKey chaveConflito = chaveFlow(negocio, "flow-conflito-perdedor", "5511977880004", dia, hora);
        BookingResult conflito = booking.confirmarEm("5511977880004", "Perdedor",
                negocio.servicoId(), "unha", negocio.profissionalId(), dia, hora,
                Duration.ofMinutes(60), chaveConflito);
        assertTrue(conflito.isConflito());

        assertEquals("INDISPONIVEL", colunaOutcomeStatus(chaveConflito.valor()));
    }

    @Test
    @DisplayName("4. SELECAO_INDISPONIVEL grava outcome_status = SELECAO_INDISPONIVEL")
    void selecaoIndisponivelGravaProprioNome() {
        NegocioCompat negocio = novoNegocioComCatalogo();
        BookingCommandKey chave = BookingCommandKey.deChaveExclusiva(negocio.businessId(),
                "http-key-" + UUID.randomUUID(), "+5511977880005", ServiceId.from(UUID.randomUUID()),
                ProfessionalId.from(UUID.randomUUID()), proximaQuarta(), LocalTime.of(9, 0));

        BookingIdempotencyOutcome outcome = registrar.registrarSelecaoIndisponivel(chave);

        assertEquals(BookingIdempotencyOutcome.SELECAO_INDISPONIVEL, outcome);
        assertEquals("SELECAO_INDISPONIVEL", colunaOutcomeStatus(chave.valor()));
    }

    @Test
    @DisplayName("5. retry do Flow sobre recibo histórico INDISPONIVEL reproduz o conflito, "
            + "sem virar falha técnica e sem criar novo Appointment")
    void retryDoFlowSobreReciboHistoricoIndisponivelReproduzConflito() {
        NegocioCompat negocio = novoNegocioComCatalogo();
        LocalDate dia = proximaQuarta();
        LocalTime hora = LocalTime.of(15, 0);
        BookingCommandKey chave = chaveFlow(negocio, "flow-retry-historico", "5511977880006", dia, hora);
        inserirReciboHistorico(chave, "INDISPONIVEL");

        BookingResult retry = booking.confirmarEm("5511977880006", "Cliente Retry",
                negocio.servicoId(), "unha", negocio.profissionalId(), dia, hora,
                Duration.ofMinutes(60), chave);

        assertTrue(retry.isConflito(), "Retry sobre recibo histórico INDISPONIVEL precisa reproduzir o conflito: " + retry);
        assertFalse(retry.isFalhaTecnica());
        assertThat(retry.status()).isEqualTo(BookingResult.Status.INDISPONIVEL);
    }
}
