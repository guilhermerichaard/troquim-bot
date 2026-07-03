package com.troquim_bot.application.availability;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.AvailabilityStatus;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Application Service para gerenciar Availabilities.
 * 
 * Responsabilidades:
 * - Criar disponibilidades
 * - Listar disponibilidades
 * - Buscar disponibilidades
 * - Atualizar disponibilidades
 * - Inativar disponibilidades
 * - Prevenir horários sobrepostos
 */
@org.springframework.stereotype.Service
public class AvailabilityApplicationService {

    private final AvailabilityRepository availabilityRepository;

    /**
     * Construtor para MVP com repositório em memória.
     */
    public AvailabilityApplicationService() {
        this(new InMemoryAvailabilityRepository());
    }

    /**
     * Construtor com injeção de dependência (para testes ou futura implementação JPA).
     */
    public AvailabilityApplicationService(AvailabilityRepository availabilityRepository) {
        this.availabilityRepository = availabilityRepository;
    }

    /**
     * Cria uma nova disponibilidade.
     * 
     * @param professionalId ID do profissional
     * @param dayOfWeek Dia da semana
     * @param startTime Horário de início
     * @param endTime Horário de fim
     * @return Availability criada com status ATIVO
     * @throws IllegalArgumentException se houver conflito de horário
     */
    public Availability criarDisponibilidade(ProfessionalId professionalId, DiaSemana dayOfWeek,
                                              LocalTime startTime, LocalTime endTime) {
        if (professionalId == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório");
        }
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Dia da semana é obrigatório");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Horário de início é obrigatório");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("Horário de fim é obrigatório");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }

        AvailabilityId id = AvailabilityId.generate();

        Availability newAvailability = new Availability(id, professionalId, dayOfWeek, startTime, endTime);

        // Verifica conflito com disponibilidades existentes do mesmo profissional
        List<Availability> existentes = availabilityRepository.findByProfessionalIdAndDayOfWeek(professionalId, dayOfWeek);
        for (Availability existing : existentes) {
            if (existing.isAtivo() && newAvailability.conflitaCom(existing)) {
                throw new IllegalArgumentException("Já existe uma disponibilidade neste horário para este profissional");
            }
        }

        return availabilityRepository.save(newAvailability);
    }

    /**
     * Busca disponibilidade por ID.
     * 
     * @param id ID da disponibilidade
     * @return Optional com o Availability se encontrado
     */
    public Optional<Availability> buscarPorId(AvailabilityId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(availabilityRepository.findById(id));
    }

    /**
     * Lista todas as disponibilidades.
     * 
     * @return Lista de todos os availabilities
     */
    public List<Availability> listarTodos() {
        return availabilityRepository.findAll();
    }

    /**
     * Lista disponibilidades de um profissional.
     * 
     * @param professionalId ID do profissional
     * @return Lista de availabilities do profissional
     */
    public List<Availability> listarPorProfissional(ProfessionalId professionalId) {
        if (professionalId == null) {
            return List.of();
        }
        return availabilityRepository.findByProfessionalId(professionalId);
    }

    /**
     * Lista apenas disponibilidades ativas.
     * 
     * @return Lista de availabilities ativos
     */
    public List<Availability> listarAtivos() {
        return availabilityRepository.findAll().stream()
            .filter(Availability::isAtivo)
            .toList();
    }

    /**
     * Atualiza o dia da semana.
     * 
     * @param id ID da disponibilidade
     * @param novoDayOfWeek Novo dia da semana
     * @return Availability atualizado
     */
    public Availability atualizarDayOfWeek(AvailabilityId id, DiaSemana novoDayOfWeek) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.atualizarDayOfWeek(novoDayOfWeek);
        return availabilityRepository.save(availability);
    }

    /**
     * Atualiza o horário de início.
     * 
     * @param id ID da disponibilidade
     * @param novoStartTime Novo horário de início
     * @return Availability atualizado
     */
    public Availability atualizarStartTime(AvailabilityId id, LocalTime novoStartTime) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.atualizarStartTime(novoStartTime);
        return availabilityRepository.save(availability);
    }

    /**
     * Atualiza o horário de fim.
     * 
     * @param id ID da disponibilidade
     * @param novoEndTime Novo horário de fim
     * @return Availability atualizado
     */
    public Availability atualizarEndTime(AvailabilityId id, LocalTime novoEndTime) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.atualizarEndTime(novoEndTime);
        return availabilityRepository.save(availability);
    }

    /**
     * Atualiza dia e horários completos.
     * 
     * @param id ID da disponibilidade
     * @param dayOfWeek Novo dia da semana
     * @param startTime Novo horário de início
     * @param endTime Novo horário de fim
     * @return Availability atualizado
     */
    public Availability atualizarHorario(AvailabilityId id, DiaSemana dayOfWeek,
                                          LocalTime startTime, LocalTime endTime) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.atualizarHorario(dayOfWeek, startTime, endTime);
        return availabilityRepository.save(availability);
    }

    /**
     * Inativa uma disponibilidade.
     * 
     * @param id ID da disponibilidade
     * @return Availability inativado
     */
    public Availability inativarDisponibilidade(AvailabilityId id) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.inativar();
        return availabilityRepository.save(availability);
    }

    /**
     * Ativa uma disponibilidade.
     * 
     * @param id ID da disponibilidade
     * @return Availability ativado
     */
    public Availability ativarDisponibilidade(AvailabilityId id) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.ativar();
        return availabilityRepository.save(availability);
    }

    /**
     * Verifica se uma disponibilidade existe.
     * 
     * @param id ID da disponibilidade
     * @return true se existe
     */
    public boolean existe(AvailabilityId id) {
        if (id == null) {
            return false;
        }
        return availabilityRepository.exists(id);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private Availability getAvailabilityOrThrow(AvailabilityId id) {
        Availability availability = availabilityRepository.findById(id);
        if (availability == null) {
            throw new IllegalArgumentException("Disponibilidade não encontrada");
        }
        return availability;
    }
}