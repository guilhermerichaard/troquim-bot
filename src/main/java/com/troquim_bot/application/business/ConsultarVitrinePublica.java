package com.troquim_bot.application.business;

import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.business.BusinessPublicProfile;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Vitrine pública: perfil + catálogo de um negócio, resolvidos a partir do slug.
 *
 * NÃO REIMPLEMENTA regra de serviço, profissional ou preço — o catálogo vem inteiro de
 * {@link ConsultarCatalogo}, a MESMA fronteira que o Flow e a área administrativa usam. Esta
 * classe só soma duas consultas já existentes atrás de uma única porta pública.
 */
@Component
public class ConsultarVitrinePublica {

    private final ResolverNegocioPublicoPorSlug resolverNegocioPublicoPorSlug;
    private final ConsultarCatalogo consultarCatalogo;

    public ConsultarVitrinePublica(ResolverNegocioPublicoPorSlug resolverNegocioPublicoPorSlug,
                                   ConsultarCatalogo consultarCatalogo) {
        this.resolverNegocioPublicoPorSlug = resolverNegocioPublicoPorSlug;
        this.consultarCatalogo = consultarCatalogo;
    }

    /** Contrato neutro da Application: nada aqui sabe o que é HTTP, JSON ou slug de URL. */
    public record VitrinePublica(BusinessPublicProfile perfil, ConsultarCatalogo.Catalogo catalogo) {
    }

    /** Vazio nos MESMOS casos que {@link ResolverNegocioPublicoPorSlug#resolver}. */
    @Transactional(readOnly = true)
    public Optional<VitrinePublica> consultar(String slugBruto) {
        return resolverNegocioPublicoPorSlug.resolver(slugBruto)
                .map(negocio -> new VitrinePublica(
                        negocio.perfil(), consultarCatalogo.consultar(negocio.businessId())));
    }
}
