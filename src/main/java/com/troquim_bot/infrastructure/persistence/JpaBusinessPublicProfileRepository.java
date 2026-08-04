package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.application.business.SlugIndisponivelException;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.business.BusinessSlug;
import com.troquim_bot.business.PublicationStatus;
import com.troquim_bot.repository.BusinessPublicProfileRepository;

import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Adapter JPA do perfil público — o repositório de PRODUÇÃO.
 *
 * A unicidade do slug é do banco, não desta classe: ela só TRADUZ a violação. Identificação
 * pela SQLState 23505 (unique_violation) E pelo nome da constraint na cadeia de causas — o
 * mesmo padrão de {@code InboundReceiptProcessor} — para não tratar QUALQUER erro de
 * integridade como conflito de slug (um FK quebrado, por exemplo, deve propagar como veio).
 */
@Repository
@Primary
public class JpaBusinessPublicProfileRepository implements BusinessPublicProfileRepository {

    private static final String CONSTRAINT_SLUG_UNICO = "uq_business_public_profiles_slug";
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    private final SpringDataBusinessPublicProfileRepository springDataRepository;

    public JpaBusinessPublicProfileRepository(SpringDataBusinessPublicProfileRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    @Transactional
    public BusinessPublicProfile salvar(BusinessPublicProfile perfil) {
        if (perfil == null) {
            throw new IllegalArgumentException("Perfil é obrigatório");
        }
        try {
            BusinessPublicProfileJpaEntity salvo =
                    springDataRepository.saveAndFlush(BusinessPublicProfileJpaEntity.de(perfil));
            return salvo.paraDominio();
        } catch (DataIntegrityViolationException violacao) {
            if (violaSlugUnico(violacao)) {
                throw new SlugIndisponivelException(perfil.getSlug().getValue(), violacao);
            }
            throw violacao;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessPublicProfile> buscarPorBusinessId(BusinessId businessId) {
        if (businessId == null) {
            return Optional.empty();
        }
        return springDataRepository.findById(businessId.getValue())
                .map(BusinessPublicProfileJpaEntity::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessPublicProfile> buscarPublicadoPorSlug(BusinessSlug slug) {
        if (slug == null) {
            return Optional.empty();
        }
        return springDataRepository.findBySlugAndPublicationStatus(slug.getValue(), PublicationStatus.PUBLISHED)
                .map(BusinessPublicProfileJpaEntity::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean slugDisponivel(BusinessSlug slug) {
        return slug != null && !springDataRepository.existsBySlug(slug.getValue());
    }

    /**
     * Verdadeiro somente se a violação for a UNIQUE(slug) deste perfil — nunca trata
     * genericamente qualquer {@link DataIntegrityViolationException} como conflito de slug.
     */
    private static boolean violaSlugUnico(DataIntegrityViolationException exception) {
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sqlException) {
                boolean unicidadeViolada = SQLSTATE_UNIQUE_VIOLATION.equals(sqlException.getSQLState());
                String mensagem = sqlException.getMessage() == null ? "" : sqlException.getMessage();
                if (unicidadeViolada && mensagem.toLowerCase().contains(CONSTRAINT_SLUG_UNICO)) {
                    return true;
                }
            }
        }
        return false;
    }
}
