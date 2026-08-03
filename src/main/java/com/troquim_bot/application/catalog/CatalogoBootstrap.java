package com.troquim_bot.application.catalog;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;

/**
 * Bootstrap OPCIONAL do catálogo do primeiro negócio.
 *
 * DESLIGADO POR PADRÃO ({@code troquim.bootstrap.catalogo.enabled=false}). Só existe como
 * bean quando explicitamente ligado, e é REMOVÍVEL depois do primeiro provisionamento:
 * apagar esta classe e as properties não afeta nada já persistido.
 *
 * Nenhum dado de cliente vive aqui — nome do salão, da profissional, telefone e serviços
 * chegam por configuração. Trocar de piloto é trocar configuração, não código.
 *
 * Idempotência é do caso de uso ({@link ProvisionarNegocio}), não deste gatilho: rodar a
 * aplicação dez vezes com a flag ligada provisiona uma vez só.
 */
@Configuration
@EnableConfigurationProperties(CatalogoBootstrapProperties.class)
@ConditionalOnProperty(prefix = "troquim.bootstrap.catalogo", name = "enabled", havingValue = "true")
public class CatalogoBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CatalogoBootstrap.class);

    @Bean
    public ApplicationRunner provisionarCatalogoInicial(ProvisionarNegocio provisionarNegocio,
                                                        CatalogoBootstrapProperties propriedades,
                                                        TenantProvider tenantProvider) {
        return args -> {
            BusinessId alvo = resolverNegocio(propriedades, tenantProvider);

            if (propriedades.getServicos().isEmpty()) {
                throw new IllegalStateException(
                        "Bootstrap do catálogo ligado sem nenhum serviço configurado em "
                                + "troquim.bootstrap.catalogo.servicos — recusando provisionar vazio.");
            }

            List<ProvisionarNegocio.ServicoDesejado> servicos = propriedades.getServicos().stream()
                    .map(s -> new ProvisionarNegocio.ServicoDesejado(s.getNome(), s.getDuracaoMinutos()))
                    .toList();

            ProvisionarNegocio.ProfissionalDesejado profissional = null;
            CatalogoBootstrapProperties.Profissional configurado = propriedades.getProfissional();
            if (configurado != null && configurado.getNome() != null && !configurado.getNome().isBlank()) {
                // Sem lista explícita, habilita para todos os serviços provisionados.
                List<String> habilitar = configurado.getServicos().isEmpty()
                        ? servicos.stream().map(ProvisionarNegocio.ServicoDesejado::nome).toList()
                        : configurado.getServicos();
                profissional = new ProvisionarNegocio.ProfissionalDesejado(
                        configurado.getNome(), configurado.getTelefone(), habilitar);
            }

            ProvisionarNegocio.Resultado resultado =
                    provisionarNegocio.provisionar(alvo, servicos, profissional);

            if (resultado.fezAlgo()) {
                log.info("Bootstrap do catálogo aplicou mudanças. Depois de conferir, desligue "
                        + "troquim.bootstrap.catalogo.enabled — ele não precisa rodar de novo.");
            } else {
                log.info("Bootstrap do catálogo não teve o que fazer: já estava provisionado.");
            }
        };
    }

    /**
     * Negócio alvo: o configurado explicitamente ou, na ausência, o tenant corrente.
     * Configuração inválida falha o startup em vez de provisionar no negócio errado.
     */
    private BusinessId resolverNegocio(CatalogoBootstrapProperties propriedades,
                                       TenantProvider tenantProvider) {
        String configurado = propriedades.getBusinessId();
        if (configurado == null || configurado.isBlank()) {
            BusinessId corrente = tenantProvider.currentBusinessId();
            if (corrente == null) {
                throw new IllegalStateException(
                        "Bootstrap do catálogo ligado sem business-id e sem tenant corrente resolvido.");
            }
            return corrente;
        }
        try {
            return BusinessId.from(UUID.fromString(configurado.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "troquim.bootstrap.catalogo.business-id não é um UUID válido", e);
        }
    }
}
