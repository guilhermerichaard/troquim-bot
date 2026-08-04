package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Entidade JPA de UM período do expediente do negócio.
 *
 * SEPARAÇÃO DELIBERADA: {@code BusinessHours} e {@code IntervaloDeHorario} não têm nenhuma
 * anotação de persistência. Esta classe é o único lugar que conhece tabela e coluna.
 *
 * GRANULARIDADE: uma linha por PERÍODO, não por dia. É o que permite almoço (duas linhas
 * no mesmo dia) e sábado diferente sem coluna condicional nenhuma. O agregado remonta o
 * expediente da semana a partir das linhas.
 */
@Entity
@Table(name = "business_hours")
public class BusinessHoursJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DiaSemana diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected BusinessHoursJpaEntity() {
    }

    public static BusinessHoursJpaEntity de(BusinessId businessId, DiaSemana dia,
                                            IntervaloDeHorario periodo, LocalDateTime agora) {
        BusinessHoursJpaEntity entidade = new BusinessHoursJpaEntity();
        entidade.id = UUID.randomUUID();
        entidade.businessId = businessId.getValue();
        entidade.diaSemana = dia;
        entidade.horaInicio = periodo.inicio();
        entidade.horaFim = periodo.fim();
        entidade.criadoEm = agora;
        entidade.atualizadoEm = agora;
        return entidade;
    }

    public DiaSemana getDiaSemana() {
        return diaSemana;
    }

    /** Reconstitui o Value Object; as invariantes são revalidadas pelo próprio VO. */
    public IntervaloDeHorario paraPeriodo() {
        return IntervaloDeHorario.de(horaInicio, horaFim);
    }
}
