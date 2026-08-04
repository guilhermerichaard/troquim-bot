package com.troquim_bot.support;

import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.repository.BusinessRepository;

import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    /** Manhã e tarde, com o almoço no meio — a semana típica do salão-piloto. */
    public static final IntervaloDeHorario MANHA =
            IntervaloDeHorario.de(LocalTime.of(9, 0), LocalTime.of(12, 0));
    public static final IntervaloDeHorario TARDE =
            IntervaloDeHorario.de(LocalTime.of(13, 0), LocalTime.of(18, 0));
    /** Sábado fecha mais cedo — o caso que o modelo antigo de janela única não expressava. */
    public static final IntervaloDeHorario SABADO =
            IntervaloDeHorario.de(LocalTime.of(9, 0), LocalTime.of(14, 0));

    /** Segunda a sexta com almoço, sábado curto, domingo FECHADO (ausência de período). */
    public static BusinessHours expedientePadrao() {
        Map<DiaSemana, List<IntervaloDeHorario>> semana = new EnumMap<>(DiaSemana.class);
        for (DiaSemana dia : List.of(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA,
                DiaSemana.QUINTA, DiaSemana.SEXTA)) {
            semana.put(dia, List.of(MANHA, TARDE));
        }
        semana.put(DiaSemana.SABADO, List.of(SABADO));
        return BusinessHours.deSemana(semana);
    }

    /** A profissional atende exatamente dentro do expediente do negócio. */
    private static Map<DiaSemana, List<IntervaloDeHorario>> disponibilidadePadrao() {
        return expedientePadrao().porDiaDaSemana();
    }

    /**
     * Provisiona dois serviços ativos, uma profissional habilitada para ambos, o expediente
     * do negócio e a disponibilidade semanal dela. Tudo pelo caso de uso real, idempotente.
     *
     * Garante primeiro que o {@link Business} do tenant existe: ProvisionarNegocio recusa
     * provisionar um negócio inexistente, e o fixture de teste não pode depender de seed
     * externo (a semente de Flyway do piloto não roda contra o H2 do profile de teste,
     * que usa {@code ddl-auto=create-drop}).
     */
    public static void provisionar(ProvisionarNegocio provisionarNegocio,
                                   BusinessRepository businessRepository, BusinessId tenant) {
        if (!businessRepository.exists(tenant)) {
            businessRepository.save(new Business(tenant, "Negócio de Teste", null, null));
        }
        provisionarNegocio.provisionar(tenant,
                List.of(new ProvisionarNegocio.ServicoDesejado(UNHAS, DURACAO_MINUTOS),
                        new ProvisionarNegocio.ServicoDesejado(CABELO, DURACAO_MINUTOS)),
                new ProvisionarNegocio.ProfissionalDesejado(
                        PROFISSIONAL, TELEFONE_PROFISSIONAL, List.of(UNHAS, CABELO),
                        disponibilidadePadrao()),
                expedientePadrao());
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
