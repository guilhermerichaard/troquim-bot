package com.troquim_bot.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mapeamento da tabela de memoria de interpretacao.
 *
 * A escrita/leitura continua no adapter {@link JpaServiceInterpretationLearningStore}
 * por SQL explicito. Esta entidade tambem garante que profiles de teste que usam
 * ddl-auto=create-drop materializem o mesmo shape basico da migration V15.
 */
@Entity
@Table(
        name = "service_interpretation_aliases",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_service_interpretation_alias_input",
                columnNames = {"business_id", "input_normalized"}))
public class ServiceInterpretationAliasJpaEntity {

    @Id
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "input_normalized", nullable = false, length = 160)
    private String inputNormalized;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "confirmations", nullable = false)
    private int confirmations;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ServiceInterpretationAliasJpaEntity() {
    }
}
