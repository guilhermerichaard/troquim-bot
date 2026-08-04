package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.Money;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.service.ServiceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA do catálogo de serviços.
 *
 * SEPARAÇÃO DELIBERADA: o agregado {@code com.troquim_bot.service.Service} não tem nenhuma
 * anotação de persistência. Esta classe é o único lugar que conhece tabela, coluna e
 * nulidade — a tradução nos dois sentidos acontece aqui.
 *
 * PREÇO: a coluna é anulável porque "não precificado" é estado legítimo. Essa nulidade
 * PARA AQUI: para o domínio, ausência de preço vira {@code Optional.empty()}, nunca null
 * e nunca zero. Preço só é reconstituído quando valor E moeda estão presentes; um dos dois
 * sozinho é dado inconsistente e é tratado como ausência, não como zero.
 */
@Entity
@Table(name = "services")
public class ServiceJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "duracao_minutos", nullable = false)
    private int duracaoMinutos;

    /** Nulo = serviço não precificado. Ver nota da classe. */
    @Column(name = "preco_valor", precision = 12, scale = 2)
    private BigDecimal precoValor;

    /** Nulo junto com {@link #precoValor}. */
    @Column(name = "preco_moeda", length = 3)
    private String precoMoeda;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ServiceStatus status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected ServiceJpaEntity() {
        // exigido pelo JPA
    }

    public static ServiceJpaEntity de(Service service) {
        ServiceJpaEntity entity = new ServiceJpaEntity();
        entity.id = service.getId().getValue();
        entity.businessId = service.getBusinessId().getValue();
        entity.nome = service.getNome();
        entity.descricao = service.getDescricao();
        entity.duracaoMinutos = service.getDuracao().getMinutes();
        service.getPreco().ifPresentOrElse(
                preco -> {
                    entity.precoValor = preco.getAmount();
                    entity.precoMoeda = preco.getCurrency();
                },
                () -> {
                    entity.precoValor = null;
                    entity.precoMoeda = null;
                });
        entity.status = service.getStatus();
        entity.criadoEm = service.getCriadoEm();
        entity.atualizadoEm = service.getAtualizadoEm();
        return entity;
    }

    public Service paraDominio() {
        // Valor sem moeda (ou vice-versa) é dado quebrado: tratamos como ausência, jamais
        // completando com moeda default nem convertendo para zero.
        Money preco = (precoValor != null && precoMoeda != null)
                ? new Money(precoValor, precoMoeda)
                : null;

        return Service.reconstituir(
                ServiceId.from(id),
                BusinessId.from(businessId),
                nome,
                descricao,
                ServiceDuration.ofMinutes(duracaoMinutos),
                preco,
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
