package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.professional.ProfessionalStatus;
import com.troquim_bot.service.ServiceId;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Entidade JPA de profissional.
 *
 * SEPARAÇÃO DELIBERADA: o agregado {@code Professional} não tem anotação de persistência.
 *
 * VÍNCULO COM SERVIÇOS: {@code professional_services} guarda a associação OFICIAL, por id,
 * e carrega {@code business_id} para que as FKs compostas do banco impeçam associar um
 * profissional do negócio A a um serviço do negócio B. O isolamento não depende só do
 * código de aplicação: é garantido pelo PostgreSQL.
 *
 * {@code especialidades} é METADADO TEXTUAL. Fica numa tabela à parte por ser multivalorado,
 * e nada no Flow, na disponibilidade ou na confirmação consulta esse campo.
 */
@Entity
@Table(name = "professionals")
public class ProfessionalJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProfessionalStatus status;

    /**
     * Vínculo oficial profissional x serviço. O {@code business_id} é repetido aqui de
     * propósito: é ele que permite as FKs compostas contra {@code professionals} e
     * {@code services}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "professional_services",
            joinColumns = {
                    @JoinColumn(name = "professional_id", referencedColumnName = "id"),
                    @JoinColumn(name = "business_id", referencedColumnName = "business_id")
            })
    @Column(name = "service_id", nullable = false)
    private Set<UUID> servicosHabilitados = new HashSet<>();

    /** Metadado textual, sem papel em regra de negócio. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "professional_especialidades",
            joinColumns = @JoinColumn(name = "professional_id"))
    @Column(name = "especialidade", nullable = false, length = 120)
    private Set<String> especialidades = new HashSet<>();

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected ProfessionalJpaEntity() {
        // exigido pelo JPA
    }

    public static ProfessionalJpaEntity de(Professional professional) {
        ProfessionalJpaEntity entity = new ProfessionalJpaEntity();
        entity.id = professional.getId().getValue();
        entity.businessId = professional.getBusinessId().getValue();
        entity.nome = professional.getNome();
        entity.telefone = professional.getTelefone();
        entity.status = professional.getStatus();
        entity.servicosHabilitados = professional.getServicosHabilitados().stream()
                .map(ServiceId::getValue)
                .collect(Collectors.toCollection(HashSet::new));
        entity.especialidades = new HashSet<>(professional.getEspecialidades());
        entity.criadoEm = professional.getCriadoEm();
        entity.atualizadoEm = professional.getAtualizadoEm();
        return entity;
    }

    public Professional paraDominio() {
        Set<ServiceId> habilitados = servicosHabilitados.stream()
                .map(ServiceId::from)
                .collect(Collectors.toCollection(HashSet::new));

        return new Professional(
                ProfessionalId.from(id),
                BusinessId.from(businessId),
                nome,
                habilitados,
                new HashSet<>(especialidades),
                telefone,
                status,
                criadoEm,
                atualizadoEm);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }
}
