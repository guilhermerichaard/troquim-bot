package com.troquim_bot.application.catalog;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.repository.InMemoryBusinessCalendarRepository;
import com.troquim_bot.repository.InMemoryBusinessRepository;
import com.troquim_bot.repository.InMemoryProfessionalRepository;
import com.troquim_bot.repository.InMemoryServiceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * ProvisionarNegocio EXIGE que o Business já exista — catálogo, expediente e disponibilidade
 * não podem ser criados silenciosamente para um negócio que nunca foi cadastrado.
 */
@DisplayName("ProvisionarNegocio - recusa negócio inexistente")
class ProvisionarNegocioExigeBusinessTest {

    private InMemoryBusinessRepository businessRepository;
    private ProvisionarNegocio provisionarNegocio;

    @BeforeEach
    void montar() {
        businessRepository = new InMemoryBusinessRepository();
        provisionarNegocio = new ProvisionarNegocio(
                new InMemoryServiceRepository(),
                new InMemoryProfessionalRepository(),
                new InMemoryBusinessCalendarRepository(),
                new InMemoryAvailabilityRepository(),
                businessRepository);
    }

    @Test
    @DisplayName("recusa provisionar um negócio que nunca foi cadastrado")
    void recusaNegocioInexistente() {
        BusinessId inexistente = BusinessId.from(UUID.randomUUID());

        assertThatThrownBy(() -> provisionarNegocio.provisionar(inexistente,
                List.of(new ProvisionarNegocio.ServicoDesejado("Corte", 30)), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(inexistente.toString())
                .hasMessageContaining("CadastrarNegocio");
    }

    @Test
    @DisplayName("aceita provisionar assim que o negócio é cadastrado")
    void aceitaAposCadastro() {
        BusinessId id = BusinessId.from(UUID.randomUUID());
        businessRepository.save(new Business(id, "Negócio de Teste", null, null));

        assertThatCode(() -> provisionarNegocio.provisionar(id,
                List.of(new ProvisionarNegocio.ServicoDesejado("Corte", 30)), null))
                .doesNotThrowAnyException();
    }
}
