package com.troquim_bot.application.availability;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.AvailabilityStatus;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.schedule.ScheduleService;
import com.troquim_bot.support.TestTenants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRUD tenant-scoped da disponibilidade profissional.
 *
 * Toda operação recebe {@link BusinessId} explícito. O cálculo de horários NÃO é testado
 * aqui — ele mora em {@code ConsultarDisponibilidade} e tem sua própria suíte.
 */
@DisplayName("AvailabilityApplicationService - CRUD por negócio")
class AvailabilityApplicationServiceTest {

    private static final BusinessId SALAO = TestTenants.PILOT;
    private static final BusinessId OUTRO_SALAO = TestTenants.OUTRO;

    private AvailabilityApplicationService servico;
    private InMemoryAvailabilityRepository repositorio;

    private final ProfessionalId profId1 = ProfessionalId.from(UUID.randomUUID());
    private final ProfessionalId profId2 = ProfessionalId.from(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        repositorio = new InMemoryAvailabilityRepository();
        // ConsultarDisponibilidade nulo de propósito: nenhum teste deste arquivo consulta
        // horários. Se algum passar a consultar, o NPE denuncia na hora.
        servico = new AvailabilityApplicationService(repositorio, null, new ScheduleService());
    }

    private Availability criar(BusinessId negocio, ProfessionalId profissional, DiaSemana dia,
                               LocalTime inicio, LocalTime fim) {
        return servico.criarDisponibilidade(negocio, profissional, dia, inicio, fim);
    }

    // ==================== criarDisponibilidade ====================

    @Test
    void deveCriarDisponibilidadeComSucesso() {
        Availability availability = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertNotNull(availability);
        assertNotNull(availability.getId());
        assertEquals(SALAO, availability.getBusinessId());
        assertEquals(profId1, availability.getProfessionalId());
        assertEquals(DiaSemana.SEGUNDA, availability.getDayOfWeek());
        assertEquals(LocalTime.of(8, 0), availability.getStartTime());
        assertEquals(LocalTime.of(12, 0), availability.getEndTime());
        assertEquals(AvailabilityStatus.ATIVO, availability.getStatus());
        assertTrue(availability.isAtivo());
        assertNotNull(availability.getCriadoEm());
        assertNotNull(availability.getAtualizadoEm());
    }

    @Test
    void deveLancarExcecaoQuandoBusinessIdNulo() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () ->
                criar(null, profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0)));
        assertTrue(erro.getMessage().contains("BusinessId"));
    }

    @Test
    void deveLancarExcecaoQuandoProfessionalIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
                criar(SALAO, null, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoDayOfWeekNulo() {
        assertThrows(IllegalArgumentException.class, () ->
                criar(SALAO, profId1, null, LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeNulo() {
        assertThrows(IllegalArgumentException.class, () ->
                criar(SALAO, profId1, DiaSemana.SEGUNDA, null, LocalTime.of(12, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoEndTimeNulo() {
        assertThrows(IllegalArgumentException.class, () ->
                criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), null));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeMaiorQueEndTime() {
        assertThrows(IllegalArgumentException.class, () ->
                criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(12, 0), LocalTime.of(8, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeIgualEndTime() {
        assertThrows(IllegalArgumentException.class, () ->
                criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(8, 0)));
    }

    @Test
    @DisplayName("períodos profissionais sobrepostos são rejeitados")
    void deveLancarExcecaoQuandoHorarioSobreposto() {
        criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertThrows(IllegalArgumentException.class, () ->
                criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(10, 0), LocalTime.of(14, 0)));
    }

    @Test
    @DisplayName("dois períodos encostados no mesmo dia são o intervalo de almoço, e são aceitos")
    void devePermitirHorarioNaoSobreposto() {
        criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(9, 0), LocalTime.of(12, 0));
        Availability tarde = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(13, 0), LocalTime.of(18, 0));

        assertNotNull(tarde);
        assertEquals(2, repositorio
                .listarAtivasPorProfissionalEDia(SALAO, profId1, DiaSemana.SEGUNDA).size());
    }

    @Test
    void devePermitirHorarioSobrepostoParaProfissionaisDiferentes() {
        criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertNotNull(criar(SALAO, profId2, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    @Test
    void devePermitirHorarioSobrepostoEmDiasDiferentes() {
        criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertNotNull(criar(SALAO, profId1, DiaSemana.TERCA,
                LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    @Test
    @DisplayName("o MESMO profissional em negócios diferentes não conflita consigo mesmo")
    void mesmoProfissionalEmNegociosDiferentesNaoConflita() {
        criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Cenário adversário: o mesmo UUID de profissional cadastrado sob outro negócio.
        assertNotNull(criar(OUTRO_SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    // ==================== buscarPorId ====================

    @Test
    void deveBuscarDisponibilidadePorId() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        Optional<Availability> encontrada = servico.buscarPorId(SALAO, criada.getId());

        assertTrue(encontrada.isPresent());
        assertEquals(criada.getId(), encontrada.get().getId());
    }

    @Test
    @DisplayName("buscar com o tenant do outro negócio devolve vazio, não o dado alheio")
    void buscaRespeitaTenant() {
        Availability doSalao = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertTrue(servico.buscarPorId(SALAO, doSalao.getId()).isPresent());
        assertTrue(servico.buscarPorId(OUTRO_SALAO, doSalao.getId()).isEmpty());
    }

    @Test
    void deveRetornarVazioQuandoIdNaoExiste() {
        assertTrue(servico.buscarPorId(SALAO, AvailabilityId.generate()).isEmpty());
    }

    @Test
    void deveRetornarVazioQuandoIdNulo() {
        assertTrue(servico.buscarPorId(SALAO, null).isEmpty());
    }

    // ==================== listagens ====================

    @Test
    @DisplayName("a listagem é do NEGÓCIO: uma empresa não lê a disponibilidade da outra")
    void listagemNaoAtravessaNegocios() {
        criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        criar(SALAO, profId2, DiaSemana.TERCA, LocalTime.of(9, 0), LocalTime.of(13, 0));
        criar(OUTRO_SALAO, profId1, DiaSemana.QUARTA, LocalTime.of(10, 0), LocalTime.of(14, 0));

        assertEquals(2, servico.listarPorNegocio(SALAO).size());
        assertEquals(1, servico.listarPorNegocio(OUTRO_SALAO).size());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistem() {
        assertTrue(servico.listarPorNegocio(SALAO).isEmpty());
    }

    @Test
    void deveListarPorProfissional() {
        criar(SALAO, profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        criar(SALAO, profId1, DiaSemana.TERCA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        criar(SALAO, profId2, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        List<Availability> doProfissional = servico.listarPorProfissional(SALAO, profId1);

        assertEquals(2, doProfissional.size());
        assertTrue(doProfissional.stream().allMatch(a -> a.getProfessionalId().equals(profId1)));
    }

    @Test
    void deveRetornarListaVaziaQuandoProfessionalIdNulo() {
        assertTrue(servico.listarPorProfissional(SALAO, null).isEmpty());
    }

    @Test
    void deveListarApenasAtivos() {
        Availability primeira = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));
        criar(SALAO, profId2, DiaSemana.TERCA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        servico.inativarDisponibilidade(SALAO, primeira.getId());

        assertEquals(1, servico.listarAtivos(SALAO).size());
        assertEquals(2, servico.listarPorNegocio(SALAO).size());
    }

    // ==================== atualizações ====================

    @Test
    void deveAtualizarDayOfWeek() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability atualizada = servico.atualizarDayOfWeek(SALAO, criada.getId(), DiaSemana.QUARTA);

        assertEquals(DiaSemana.QUARTA, atualizada.getDayOfWeek());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarDayOfWeekDeInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
                servico.atualizarDayOfWeek(SALAO, AvailabilityId.generate(), DiaSemana.QUARTA));
    }

    @Test
    @DisplayName("atualizar com o tenant errado não altera o dado do outro negócio")
    void atualizacaoRespeitaTenant() {
        Availability doSalao = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertThrows(IllegalArgumentException.class, () ->
                servico.atualizarDayOfWeek(OUTRO_SALAO, doSalao.getId(), DiaSemana.QUARTA));
        assertEquals(DiaSemana.SEGUNDA,
                servico.buscarPorId(SALAO, doSalao.getId()).orElseThrow().getDayOfWeek());
    }

    @Test
    void deveAtualizarStartTime() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability atualizada = servico.atualizarStartTime(SALAO, criada.getId(), LocalTime.of(9, 0));

        assertEquals(LocalTime.of(9, 0), atualizada.getStartTime());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarStartTimeMaiorQueEndTime() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertThrows(IllegalArgumentException.class, () ->
                servico.atualizarStartTime(SALAO, criada.getId(), LocalTime.of(13, 0)));
    }

    @Test
    void deveAtualizarEndTime() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability atualizada = servico.atualizarEndTime(SALAO, criada.getId(), LocalTime.of(14, 0));

        assertEquals(LocalTime.of(14, 0), atualizada.getEndTime());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarEndTimeMenorQueStartTime() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertThrows(IllegalArgumentException.class, () ->
                servico.atualizarEndTime(SALAO, criada.getId(), LocalTime.of(7, 0)));
    }

    @Test
    void deveAtualizarHorarioCompleto() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability atualizada = servico.atualizarHorario(SALAO, criada.getId(),
                DiaSemana.SEXTA, LocalTime.of(14, 0), LocalTime.of(18, 0));

        assertEquals(DiaSemana.SEXTA, atualizada.getDayOfWeek());
        assertEquals(LocalTime.of(14, 0), atualizada.getStartTime());
        assertEquals(LocalTime.of(18, 0), atualizada.getEndTime());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarHorarioDeInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
                servico.atualizarHorario(SALAO, AvailabilityId.generate(), DiaSemana.SEXTA,
                        LocalTime.of(14, 0), LocalTime.of(18, 0)));
    }

    // ==================== ciclo de vida ====================

    @Test
    void deveInativarDisponibilidade() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability inativada = servico.inativarDisponibilidade(SALAO, criada.getId());

        assertFalse(inativada.isAtivo());
        assertEquals(AvailabilityStatus.INATIVO, inativada.getStatus());
    }

    @Test
    void deveLancarExcecaoQuandoInativarInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
                servico.inativarDisponibilidade(SALAO, AvailabilityId.generate()));
    }

    @Test
    void deveAtivarDisponibilidade() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));
        servico.inativarDisponibilidade(SALAO, criada.getId());

        Availability ativada = servico.ativarDisponibilidade(SALAO, criada.getId());

        assertTrue(ativada.isAtivo());
    }

    @Test
    void deveLancarExcecaoQuandoAtivarInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
                servico.ativarDisponibilidade(SALAO, AvailabilityId.generate()));
    }

    // ==================== existe ====================

    @Test
    void deveRetornarTrueQuandoExiste() {
        Availability criada = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertTrue(servico.existe(SALAO, criada.getId()));
        assertFalse(servico.existe(OUTRO_SALAO, criada.getId()));
    }

    @Test
    void deveRetornarFalseQuandoNaoExiste() {
        assertFalse(servico.existe(SALAO, AvailabilityId.generate()));
    }

    @Test
    void deveRetornarFalseQuandoIdNulo() {
        assertFalse(servico.existe(SALAO, null));
    }

    // ==================== Disponibilidade inativada não conflita ====================

    @Test
    void disponibilidadeInativadaNaoImpedeCriacaoDeNova() {
        Availability primeira = criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(8, 0), LocalTime.of(12, 0));
        servico.inativarDisponibilidade(SALAO, primeira.getId());

        assertNotNull(criar(SALAO, profId1, DiaSemana.SEGUNDA,
                LocalTime.of(9, 0), LocalTime.of(13, 0)));
    }
}
