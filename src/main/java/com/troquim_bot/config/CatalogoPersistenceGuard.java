package com.troquim_bot.config;

import com.troquim_bot.infrastructure.persistence.JpaAvailabilityRepository;
import com.troquim_bot.infrastructure.persistence.JpaBusinessCalendarRepository;
import com.troquim_bot.infrastructure.persistence.JpaBusinessPublicProfileRepository;
import com.troquim_bot.infrastructure.persistence.JpaBusinessRepository;
import com.troquim_bot.infrastructure.persistence.JpaProfessionalRepository;
import com.troquim_bot.infrastructure.persistence.JpaServiceRepository;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.BusinessCalendarRepository;
import com.troquim_bot.repository.BusinessPublicProfileRepository;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.repository.ServiceRepository;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Impede que a aplicação suba, fora de perfis de teste/dev, usando catálogo, calendário ou
 * identidade de negócio voláteis.
 *
 * POR QUE ISTO EXISTE: depender apenas de {@code @Primary} no adapter JPA é frágil — basta
 * o bean JPA não ser criado (entidade fora do scan, datasource ausente, engano de perfil)
 * para o contexto escolher silenciosamente o repositório em memória. O sintoma seria o
 * pior possível: aplicação no ar, catálogo aparentemente funcionando e agendamentos
 * sumindo a cada restart, sem um único erro no log.
 *
 * A guarda troca essa degradação silenciosa por falha de inicialização explícita.
 *
 * Ativa em todo perfil que não seja {@code test} nem {@code dev-inmemory} — exatamente os
 * dois perfis onde o duplo em memória é legítimo.
 */
@Configuration
@Profile("!test & !dev-inmemory")
public class CatalogoPersistenceGuard {

    public CatalogoPersistenceGuard(ServiceRepository serviceRepository,
                                    ProfessionalRepository professionalRepository,
                                    BusinessCalendarRepository businessCalendarRepository,
                                    AvailabilityRepository availabilityRepository,
                                    BusinessRepository businessRepository,
                                    BusinessPublicProfileRepository businessPublicProfileRepository) {
        exigirAdapterJpa("ServiceRepository", serviceRepository, JpaServiceRepository.class);
        exigirAdapterJpa("ProfessionalRepository", professionalRepository, JpaProfessionalRepository.class);
        // Calendário e disponibilidade correm o MESMO risco do catálogo: em memória, a
        // agenda parece funcionar e some no restart seguinte, sem um único erro no log.
        exigirAdapterJpa("BusinessCalendarRepository", businessCalendarRepository,
                JpaBusinessCalendarRepository.class);
        exigirAdapterJpa("AvailabilityRepository", availabilityRepository,
                JpaAvailabilityRepository.class);
        // A raiz de identidade do tenant não pode evaporar a cada restart: sem ela, todo o
        // resto (catálogo, calendário, agendamentos) fica orfão de um negócio que "some".
        exigirAdapterJpa("BusinessRepository", businessRepository, JpaBusinessRepository.class);
        // O mesmo vale para o perfil público: um slug "publicado" que evapora no restart
        // devolveria 404 para quem já divulgou o link.
        exigirAdapterJpa("BusinessPublicProfileRepository", businessPublicProfileRepository,
                JpaBusinessPublicProfileRepository.class);
    }

    private static void exigirAdapterJpa(String porta, Object implementacao, Class<?> esperada) {
        if (!esperada.isInstance(implementacao)) {
            throw new IllegalStateException(
                    "Catálogo sem persistência: " + porta + " resolvido para "
                            + (implementacao == null ? "nenhum bean" : implementacao.getClass().getName())
                            + ", esperado " + esperada.getSimpleName() + ". "
                            + "Fora dos perfis 'test' e 'dev-inmemory' o catálogo DEVE ser persistido — "
                            + "subir com repositório em memória perderia serviços e habilitações a cada restart.");
        }
    }
}
