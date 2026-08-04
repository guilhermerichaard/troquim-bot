package com.troquim_bot.application.business;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.repository.InMemoryBusinessPublicProfileRepository;
import com.troquim_bot.repository.InMemoryBusinessRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsultarPerfilPublicadoPorSlug - consulta PÚBLICA, só PUBLISHED")
class ConsultarPerfilPublicadoPorSlugTest {

    private InMemoryBusinessRepository businessRepository;
    private InMemoryBusinessPublicProfileRepository perfilRepository;
    private ConfigurarPerfilPublico configurarPerfilPublico;
    private PublicarPerfilPublico publicarPerfilPublico;
    private ConsultarPerfilPublicadoPorSlug consultarPorSlug;
    private ConsultarPerfilPublicoPorBusinessId consultarPorBusinessId;

    private BusinessId id;

    @BeforeEach
    void montar() {
        businessRepository = new InMemoryBusinessRepository();
        perfilRepository = new InMemoryBusinessPublicProfileRepository();
        configurarPerfilPublico = new ConfigurarPerfilPublico(businessRepository, perfilRepository);
        publicarPerfilPublico = new PublicarPerfilPublico(businessRepository, perfilRepository);
        consultarPorSlug = new ConsultarPerfilPublicadoPorSlug(perfilRepository);
        consultarPorBusinessId = new ConsultarPerfilPublicoPorBusinessId(perfilRepository);

        id = BusinessId.from(UUID.randomUUID());
        businessRepository.save(new Business(id, "Negócio", null, null));
    }

    @Test
    @DisplayName("perfil em DRAFT não aparece na consulta pública por slug")
    void draftNaoApareceNaConsultaPublica() {
        configurarPerfilPublico.configurar(id, "salao-da-ana", "Salão da Ana", null, null, null);

        Optional<BusinessPublicProfile> encontrado = consultarPorSlug.consultar("salao-da-ana");

        assertThat(encontrado).isEmpty();
    }

    @Test
    @DisplayName("perfil PUBLISHED aparece na consulta pública por slug")
    void publishedApareceNaConsultaPublica() {
        configurarPerfilPublico.configurar(id, "salao-da-ana", "Salão da Ana", null, null, null);
        publicarPerfilPublico.publicar(id);

        Optional<BusinessPublicProfile> encontrado = consultarPorSlug.consultar("salao-da-ana");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getBusinessId()).isEqualTo(id);
    }

    @Test
    @DisplayName("consulta pública aceita o slug em qualquer variação que normalize igual")
    void consultaPublicaNormalizaOSlugDeEntrada() {
        configurarPerfilPublico.configurar(id, "salao-da-ana", "Salão da Ana", null, null, null);
        publicarPerfilPublico.publicar(id);

        assertThat(consultarPorSlug.consultar("Salão Da Ana")).isPresent();
    }

    @Test
    @DisplayName("slug inexistente devolve vazio, sem estourar")
    void slugInexistenteDevolveVazio() {
        assertThat(consultarPorSlug.consultar("nunca-existiu")).isEmpty();
    }

    @Test
    @DisplayName("slug bruto inválido devolve vazio, sem estourar exceção")
    void slugInvalidoDevolveVazioSemEstourar() {
        assertThat(consultarPorSlug.consultar("!!!")).isEmpty();
        assertThat(consultarPorSlug.consultar("")).isEmpty();
        assertThat(consultarPorSlug.consultar((String) null)).isEmpty();
    }

    @Test
    @DisplayName("despublicar remove o perfil da consulta pública, mesmo que continue existindo administrativamente")
    void despublicarRemoveDaConsultaPublicaMasNaoAdministrativa() {
        configurarPerfilPublico.configurar(id, "salao-da-ana", "Salão da Ana", null, null, null);
        publicarPerfilPublico.publicar(id);
        new DespublicarPerfilPublico(perfilRepository).despublicar(id);

        assertThat(consultarPorSlug.consultar("salao-da-ana")).isEmpty();
        assertThat(consultarPorBusinessId.consultar(id)).isPresent();
    }

    @Test
    @DisplayName("consulta administrativa por BusinessId enxerga o perfil em qualquer status")
    void consultaAdministrativaEnxergaQualquerStatus() {
        configurarPerfilPublico.configurar(id, "salao-da-ana", "Salão da Ana", null, null, null);

        assertThat(consultarPorBusinessId.consultar(id)).isPresent();
    }
}
