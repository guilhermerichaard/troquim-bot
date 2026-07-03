package com.troquim_bot.application.availability;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.AvailabilityStatus;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AvailabilityApplicationServiceTest {

    private AvailabilityApplicationService availabilityApplicationService;
    private InMemoryAvailabilityRepository availabilityRepository;

    private final ProfessionalId profId1 = ProfessionalId.from(UUID.randomUUID());
    private final ProfessionalId profId2 = ProfessionalId.from(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        availabilityRepository = new InMemoryAvailabilityRepository();
        availabilityApplicationService = new AvailabilityApplicationService(availabilityRepository);
    }

    // ==================== criarDisponibilidade ====================

    @Test
    void deveCriarDisponibilidadeComSucesso() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertNotNull(availability);
        assertNotNull(availability.getId());
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
    void deveLancarExcecaoQuandoProfessionalIdNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.criarDisponibilidade(
                null, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoDayOfWeekNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.criarDisponibilidade(
                profId1, null, LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.criarDisponibilidade(
                profId1, DiaSemana.SEGUNDA, null, LocalTime.of(12, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoEndTimeNulo() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.criarDisponibilidade(
                profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), null));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeMaiorQueEndTime() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.criarDisponibilidade(
                profId1, DiaSemana.SEGUNDA, LocalTime.of(12, 0), LocalTime.of(8, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoStartTimeIgualEndTime() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.criarDisponibilidade(
                profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(8, 0)));
    }

    @Test
    void deveLancarExcecaoQuandoHorarioSobreposto() {
        // Cria primeira disponibilidade
        availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Tenta criar horário sobreposto (dentro do mesmo período)
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.criarDisponibilidade(
                profId1, DiaSemana.SEGUNDA, LocalTime.of(9, 0), LocalTime.of(10, 0)));
    }

    @Test
    void devePermitirHorarioNaoSobreposto() {
        // Cria primeira disponibilidade
        availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Cria horário não sobreposto (após o primeiro)
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(13, 0), LocalTime.of(18, 0));

        assertNotNull(availability);
        assertEquals(AvailabilityStatus.ATIVO, availability.getStatus());
    }

    @Test
    void devePermitirHorarioSobrepostoParaProfissionaisDiferentes() {
        // Cria disponibilidade para profissional 1
        availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Cria horário sobreposto para profissional 2
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId2, DiaSemana.SEGUNDA, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertNotNull(availability);
    }

    @Test
    void devePermitirHorarioSobrepostoEmDiasDiferentes() {
        // Cria disponibilidade na segunda
        availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        // Cria horário sobreposto na terça
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.TERCA, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertNotNull(availability);
    }

    // ==================== buscarPorId ====================

    @Test
    void deveBuscarDisponibilidadePorId() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        AvailabilityId id = availability.getId();

        Optional<Availability> encontrado = availabilityApplicationService.buscarPorId(id);

        assertTrue(encontrado.isPresent());
        assertEquals(availability.getId(), encontrado.get().getId());
    }

    @Test
    void deveRetornarVazioQuandoIdNaoExiste() {
        Optional<Availability> encontrado = availabilityApplicationService.buscarPorId(AvailabilityId.generate());

        assertFalse(encontrado.isPresent());
    }

    @Test
    void deveRetornarVazioQuandoIdNulo() {
        Optional<Availability> encontrado = availabilityApplicationService.buscarPorId(null);

        assertFalse(encontrado.isPresent());
    }

    // ==================== listarTodos ====================

    @Test
    void deveListarTodos() {
        availabilityApplicationService.criarDisponibilidade(profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        availabilityApplicationService.criarDisponibilidade(profId1, DiaSemana.TERCA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        availabilityApplicationService.criarDisponibilidade(profId2, DiaSemana.SEGUNDA, LocalTime.of(14, 0), LocalTime.of(18, 0));

        List<Availability> availabilities = availabilityApplicationService.listarTodos();

        assertEquals(3, availabilities.size());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistem() {
        List<Availability> availabilities = availabilityApplicationService.listarTodos();

        assertTrue(availabilities.isEmpty());
    }

    // ==================== listarPorProfissional ====================

    @Test
    void deveListarPorProfissional() {
        availabilityApplicationService.criarDisponibilidade(profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        availabilityApplicationService.criarDisponibilidade(profId1, DiaSemana.TERCA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        availabilityApplicationService.criarDisponibilidade(profId2, DiaSemana.SEGUNDA, LocalTime.of(14, 0), LocalTime.of(18, 0));

        List<Availability> doProf1 = availabilityApplicationService.listarPorProfissional(profId1);

        assertEquals(2, doProf1.size());
        assertTrue(doProf1.stream().allMatch(a -> a.getProfessionalId().equals(profId1)));
    }

    @Test
    void deveRetornarListaVaziaQuandoProfessionalIdNulo() {
        List<Availability> result = availabilityApplicationService.listarPorProfissional(null);

        assertTrue(result.isEmpty());
    }

    // ==================== listarAtivos ====================

    @Test
    void deveListarApenasAtivos() {
        Availability ativo1 = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        Availability ativo2 = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.TERCA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        Availability inativo = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.QUARTA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        availabilityApplicationService.inativarDisponibilidade(inativo.getId());

        List<Availability> ativos = availabilityApplicationService.listarAtivos();

        assertEquals(2, ativos.size());
        assertTrue(ativos.stream().allMatch(Availability::isAtivo));
    }

    // ==================== atualizarDayOfWeek ====================

    @Test
    void deveAtualizarDayOfWeek() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability atualizado = availabilityApplicationService.atualizarDayOfWeek(availability.getId(), DiaSemana.TERCA);

        assertEquals(DiaSemana.TERCA, atualizado.getDayOfWeek());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarDayOfWeekDeInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.atualizarDayOfWeek(AvailabilityId.generate(), DiaSemana.TERCA));
    }

    // ==================== atualizarStartTime ====================

    @Test
    void deveAtualizarStartTime() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability atualizado = availabilityApplicationService.atualizarStartTime(availability.getId(), LocalTime.of(9, 0));

        assertEquals(LocalTime.of(9, 0), atualizado.getStartTime());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarStartTimeMaiorQueEndTime() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.atualizarStartTime(availability.getId(), LocalTime.of(13, 0)));
    }

    // ==================== atualizarEndTime ====================

    @Test
    void deveAtualizarEndTime() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability atualizado = availabilityApplicationService.atualizarEndTime(availability.getId(), LocalTime.of(14, 0));

        assertEquals(LocalTime.of(14, 0), atualizado.getEndTime());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarEndTimeMenorQueStartTime() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.atualizarEndTime(availability.getId(), LocalTime.of(7, 0)));
    }

    // ==================== atualizarHorario ====================

    @Test
    void deveAtualizarHorarioCompleto() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability atualizado = availabilityApplicationService.atualizarHorario(
            availability.getId(), DiaSemana.QUARTA, LocalTime.of(14, 0), LocalTime.of(18, 0));

        assertEquals(DiaSemana.QUARTA, atualizado.getDayOfWeek());
        assertEquals(LocalTime.of(14, 0), atualizado.getStartTime());
        assertEquals(LocalTime.of(18, 0), atualizado.getEndTime());
    }

    @Test
    void deveLancarExcecaoQuandoAtualizarHorarioDeInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.atualizarHorario(
                AvailabilityId.generate(), DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    // ==================== inativarDisponibilidade ====================

    @Test
    void deveInativarDisponibilidade() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        Availability inativado = availabilityApplicationService.inativarDisponibilidade(availability.getId());

        assertEquals(AvailabilityStatus.INATIVO, inativado.getStatus());
        assertFalse(inativado.isAtivo());
    }

    @Test
    void deveLancarExcecaoQuandoInativarInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.inativarDisponibilidade(AvailabilityId.generate()));
    }

    // ==================== ativarDisponibilidade ====================

    @Test
    void deveAtivarDisponibilidade() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        availabilityApplicationService.inativarDisponibilidade(availability.getId());

        Availability ativado = availabilityApplicationService.ativarDisponibilidade(availability.getId());

        assertEquals(AvailabilityStatus.ATIVO, ativado.getStatus());
        assertTrue(ativado.isAtivo());
    }

    @Test
    void deveLancarExcecaoQuandoAtivarInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            availabilityApplicationService.ativarDisponibilidade(AvailabilityId.generate()));
    }

    // ==================== existe ====================

    @Test
    void deveRetornarTrueQuandoExiste() {
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));

        assertTrue(availabilityApplicationService.existe(availability.getId()));
    }

    @Test
    void deveRetornarFalseQuandoNaoExiste() {
        assertFalse(availabilityApplicationService.existe(AvailabilityId.generate()));
    }

    @Test
    void deveRetornarFalseQuandoIdNulo() {
        assertFalse(availabilityApplicationService.existe(null));
    }

    // ==================== Disponibilidade inativada não conflita ====================

    @Test
    void disponibilidadeInativadaNaoImpedeCriacaoDeNova() {
        // Cria e inativa uma disponibilidade
        Availability availability = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(8, 0), LocalTime.of(12, 0));
        availabilityApplicationService.inativarDisponibilidade(availability.getId());

        // Deve ser possível criar outra no mesmo horário (a anterior está inativa)
        Availability nova = availabilityApplicationService.criarDisponibilidade(
            profId1, DiaSemana.SEGUNDA, LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertNotNull(nova);
        assertEquals(AvailabilityStatus.ATIVO, nova.getStatus());
    }
}