package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA da raiz de identidade do negócio.
 *
 * SEPARAÇÃO DELIBERADA: {@link Business} não tem nenhuma anotação de persistência. Esta
 * classe é o único lugar que conhece tabela e coluna de {@code businesses}.
 */
@Entity
@Table(name = "businesses")
public class BusinessJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "endereco", length = 255)
    private String endereco;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BusinessStatus status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected BusinessJpaEntity() {
    }

    public static BusinessJpaEntity de(Business business) {
        BusinessJpaEntity entidade = new BusinessJpaEntity();
        entidade.id = business.getId().getValue();
        entidade.nome = business.getNome();
        entidade.telefone = business.getTelefone();
        entidade.endereco = business.getEndereco();
        entidade.status = business.getStatus();
        entidade.criadoEm = business.getCriadoEm();
        entidade.atualizadoEm = business.getAtualizadoEm();
        return entidade;
    }

    /** Reconstitui o Aggregate Root a partir da linha. */
    public Business paraDominio() {
        return new Business(BusinessId.from(id), nome, telefone, endereco, status, criadoEm, atualizadoEm);
    }
}
