package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.business.BusinessSlug;
import com.troquim_bot.business.PublicationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA do perfil público do negócio.
 *
 * SEPARAÇÃO DELIBERADA: {@link BusinessPublicProfile} não tem nenhuma anotação de
 * persistência. Esta classe é o único lugar que conhece tabela e coluna de
 * {@code business_public_profiles}.
 *
 * {@code uniqueConstraints} com o MESMO nome da constraint da V14: no PostgreSQL o schema é
 * autoridade do Flyway (Hibernate só valida), mas no H2 dos testes é o Hibernate quem gera o
 * schema a partir desta entidade — nomes divergentes fariam o adapter JPA de teste "acertar"
 * a unicidade sem exercitar a MESMA lógica de tradução de conflito que roda em produção.
 */
@Entity
@Table(
        name = "business_public_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uq_business_public_profiles_slug", columnNames = "slug")
)
public class BusinessPublicProfileJpaEntity {

    @Id
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "slug", nullable = false, length = 63)
    private String slug;

    @Column(name = "nome_publico", nullable = false, length = 120)
    private String nomePublico;

    @Column(name = "descricao_curta", length = 300)
    private String descricaoCurta;

    @Column(name = "telefone_publico", length = 30)
    private String telefonePublico;

    @Column(name = "endereco_publico", length = 255)
    private String enderecoPublico;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20)
    private PublicationStatus publicationStatus;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected BusinessPublicProfileJpaEntity() {
    }

    public static BusinessPublicProfileJpaEntity de(BusinessPublicProfile perfil) {
        BusinessPublicProfileJpaEntity entidade = new BusinessPublicProfileJpaEntity();
        entidade.businessId = perfil.getBusinessId().getValue();
        entidade.slug = perfil.getSlug().getValue();
        entidade.nomePublico = perfil.getNomePublico();
        entidade.descricaoCurta = perfil.getDescricaoCurta();
        entidade.telefonePublico = perfil.getTelefonePublico();
        entidade.enderecoPublico = perfil.getEnderecoPublico();
        entidade.publicationStatus = perfil.getStatus();
        entidade.criadoEm = perfil.getCriadoEm();
        entidade.atualizadoEm = perfil.getAtualizadoEm();
        return entidade;
    }

    /** Reconstitui o Aggregate Root; o Value Object do slug revalida as próprias invariantes. */
    public BusinessPublicProfile paraDominio() {
        return new BusinessPublicProfile(
                BusinessId.from(businessId), BusinessSlug.de(slug), nomePublico, descricaoCurta,
                telefonePublico, enderecoPublico, publicationStatus, criadoEm, atualizadoEm);
    }

    public String getSlug() {
        return slug;
    }
}
