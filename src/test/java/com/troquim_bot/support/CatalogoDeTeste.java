package com.troquim_bot.support;

import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.business.BusinessId;

import java.util.List;

/**
 * Catálogo de fixture para os testes que exercitam o Flow ponta a ponta.
 *
 * Existe porque o Flow deixou de ter lista fixa: sem catálogo PERSISTIDO não há serviço
 * para escolher. O provisionamento passa pelo caso de uso real ({@link ProvisionarNegocio},
 * idempotente por nome), então chamar em cada {@code @BeforeEach} é seguro mesmo com o
 * contexto Spring reaproveitado entre classes.
 *
 * Os ids são LIDOS do catálogo depois de provisionar — nunca inventados pelo teste. É o
 * que faz as asserções provarem identidade real de ponta a ponta.
 */
public final class CatalogoDeTeste {

    public static final String UNHAS = "Unhas";
    public static final String CABELO = "Cabelo";
    public static final String PROFISSIONAL = "Malu";

    private static final int DURACAO_MINUTOS = 60;
    private static final String TELEFONE_PROFISSIONAL = "+5511900000000";

    private CatalogoDeTeste() {
    }

    /** Provisiona dois serviços ativos e uma profissional habilitada para ambos. */
    public static void provisionar(ProvisionarNegocio provisionarNegocio, BusinessId tenant) {
        provisionarNegocio.provisionar(tenant,
                List.of(new ProvisionarNegocio.ServicoDesejado(UNHAS, DURACAO_MINUTOS),
                        new ProvisionarNegocio.ServicoDesejado(CABELO, DURACAO_MINUTOS)),
                new ProvisionarNegocio.ProfissionalDesejado(
                        PROFISSIONAL, TELEFONE_PROFISSIONAL, List.of(UNHAS, CABELO)));
    }

    public static ConsultarCatalogo.ItemDeCatalogo item(ConsultarCatalogo consultarCatalogo,
                                                        BusinessId tenant, String nome) {
        return consultarCatalogo.consultar(tenant).itens().stream()
                .filter(item -> item.nome().equalsIgnoreCase(nome))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Serviço '" + nome + "' não está no catálogo do negócio de teste"));
    }

    /** UUID textual do serviço, exatamente como o Flow o trafega. */
    public static String servicoId(ConsultarCatalogo consultarCatalogo, BusinessId tenant, String nome) {
        return item(consultarCatalogo, tenant, nome).id().getValue().toString();
    }

    /** UUID textual do primeiro profissional habilitado para o serviço. */
    public static String profissionalId(ConsultarCatalogo consultarCatalogo, BusinessId tenant, String nome) {
        return item(consultarCatalogo, tenant, nome).profissionais().get(0).id().getValue().toString();
    }
}
