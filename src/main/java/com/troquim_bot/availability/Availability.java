package com.troquim_bot.availability;

import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Aggregate Root que representa a disponibilidade de um profissional.
 * 
 * Responsabilidades:
 * - Gerenciar horários de disponibilidade (dia da semana, horário início/fim)
 * - Controlar o ciclo de vida da disponibilidade (ATIVO, INATIVO)
 * - Proteger invariants de negócio (startTime < endTime)
 */
public class Availability {

    private final AvailabilityId id;
    private final ProfessionalId professionalId;
    private DiaSemana dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private AvailabilityStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /**
     * Construtor para criação de novo Availability.
     * Inicia com status ATIVO.
     */
    public Availability(AvailabilityId id, ProfessionalId professionalId, DiaSemana dayOfWeek,
                        LocalTime startTime, LocalTime endTime) {
        if (id == null) {
            throw new IllegalArgumentException("AvailabilityId é obrigatório");
        }
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

        this.id = id;
        this.professionalId = professionalId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AvailabilityStatus.ATIVO;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    /**
     * Construtor para reconstituição de Availability existente (ex: do banco de dados).
     * Usado apenas pela infraestrutura.
     */
    public Availability(AvailabilityId id, ProfessionalId professionalId, DiaSemana dayOfWeek,
                        LocalTime startTime, LocalTime endTime,
                        AvailabilityStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("AvailabilityId é obrigatório");
        }
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
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.professionalId = professionalId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    // ==================== GETTERS ====================

    public AvailabilityId getId() {
        return id;
    }

    public ProfessionalId getProfessionalId() {
        return professionalId;
    }

    public DiaSemana getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public AvailabilityStatus getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    // ==================== MÉTODOS DE NEGÓCIO ====================

    /**
     * Verifica se o Availability está ativo.
     */
    public boolean isAtivo() {
        return status == AvailabilityStatus.ATIVO;
    }

    /**
     * Verifica se este horário conflita com outro horário.
     * Dois horários conflitam se são do mesmo profissional, mesmo dia e se sobrepõem.
     */
    public boolean conflitaCom(Availability other) {
        if (!this.professionalId.equals(other.professionalId)) {
            return false;
        }
        if (this.dayOfWeek != other.dayOfWeek) {
            return false;
        }
        // Verifica sobreposição: startA < endB && startB < endA
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }

    /**
     * Atualiza o dia da semana.
     */
    public void atualizarDayOfWeek(DiaSemana dayOfWeek) {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Dia da semana não pode ser nulo");
        }
        this.dayOfWeek = dayOfWeek;
        tocar();
    }

    /**
     * Atualiza o horário de início.
     */
    public void atualizarStartTime(LocalTime startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Horário de início não pode ser nulo");
        }
        if (!startTime.isBefore(this.endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }
        this.startTime = startTime;
        tocar();
    }

    /**
     * Atualiza o horário de fim.
     */
    public void atualizarEndTime(LocalTime endTime) {
        if (endTime == null) {
            throw new IllegalArgumentException("Horário de fim não pode ser nulo");
        }
        if (!this.startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }
        this.endTime = endTime;
        tocar();
    }

    /**
     * Atualiza dia e horários completos.
     */
    public void atualizarHorario(DiaSemana dayOfWeek, LocalTime startTime, LocalTime endTime) {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Dia da semana não pode ser nulo");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Horário de início não pode ser nulo");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("Horário de fim não pode ser nulo");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        tocar();
    }

    /**
     * Inativa o Availability (transição para INATIVO).
     */
    public void inativar() {
        if (status == AvailabilityStatus.ATIVO) {
            this.status = AvailabilityStatus.INATIVO;
            tocar();
        }
    }

    /**
     * Ativa o Availability (transição para ATIVO).
     */
    public void ativar() {
        if (status == AvailabilityStatus.INATIVO) {
            this.status = AvailabilityStatus.ATIVO;
            tocar();
        }
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}