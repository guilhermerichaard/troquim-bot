package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.AvailabilityStatus;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;

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
 * Entidade JPA de UM período de disponibilidade de um profissional.
 *
 * SEPARAÇÃO DELIBERADA: o agregado {@code Availability} não tem anotação de persistência.
 *
 * O {@code business_id} não é redundante em relação ao profissional: é ele que participa da
 * FK COMPOSTA {@code (business_id, professional_id)} declarada na V12, e é essa FK que faz o
 * banco recusar disponibilidade cruzada entre negócios.
 */
@Entity
@Table(name = "professional_availability")
public class AvailabilityJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DiaSemana diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AvailabilityStatus status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected AvailabilityJpaEntity() {
    }

    public static AvailabilityJpaEntity de(Availability disponibilidade) {
        AvailabilityJpaEntity entidade = new AvailabilityJpaEntity();
        entidade.id = disponibilidade.getId().getValue();
        entidade.businessId = disponibilidade.getBusinessId().getValue();
        entidade.professionalId = disponibilidade.getProfessionalId().getValue();
        entidade.diaSemana = disponibilidade.getDayOfWeek();
        entidade.horaInicio = disponibilidade.getStartTime();
        entidade.horaFim = disponibilidade.getEndTime();
        entidade.status = disponibilidade.getStatus();
        entidade.criadoEm = disponibilidade.getCriadoEm();
        entidade.atualizadoEm = disponibilidade.getAtualizadoEm();
        return entidade;
    }

    /** Reconstitui o agregado inteiro, Value Objects inclusive. */
    public Availability paraDominio() {
        return new Availability(
                AvailabilityId.from(id),
                BusinessId.from(businessId),
                ProfessionalId.from(professionalId),
                diaSemana,
                IntervaloDeHorario.de(horaInicio, horaFim),
                status,
                criadoEm,
                atualizadoEm);
    }
}
