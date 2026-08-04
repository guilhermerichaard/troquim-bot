package com.troquim_bot.application.business;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.repository.InMemoryBusinessPublicProfileRepository;
import com.troquim_bot.repository.InMemoryBusinessRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConfigurarPerfilPublico - cria/atualiza somente o perfil do Business informado")
class ConfigurarPerfilPublicoTest {

    private InMemoryBusinessRepository businessRepository;
    private InMemoryBusinessPublicProfileRepository perfilRepository;
    private ConfigurarPerfilPublico configurarPerfilPublico;

    private BusinessId a;
    private BusinessId b;

    @BeforeEach
    void montar() {
        businessRepository = new InMemoryBusinessRepository();
        perfilRepository = new InMemoryBusinessPublicProfileRepository();
        configurarPerfilPublico = new ConfigurarPerfilPublico(businessRepository, perfilRepository);

        a = BusinessId.from(UUID.randomUUID());
        b = BusinessId.from(UUID.randomUUID());
        businessRepository.save(new Business(a, "Negócio A", null, null));
        businessRepository.save(new Business(b, "Negócio B", null, null));
    }

    @Test
    @DisplayName("recusa configurar perfil de negócio inexistente")
    void recusaNegocioInexistente() {
        BusinessId inexistente = BusinessId.from(UUID.randomUUID());

        assertThatThrownBy(() -> configurarPerfilPublico.configurar(
                inexistente, "salao-x", "Salão X", null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CadastrarNegocio");
    }

    @Test
    @DisplayName("cria o perfil em DRAFT, sem publicar automaticamente")
    void criaPerfilSemPublicarAutomaticamente() {
        BusinessPublicProfile perfil = configurarPerfilPublico.configurar(
                a, "Salão da Ana", "Salão da Ana", "Descrição", "+5511999990000", "Rua A, 1");

        assertThat(perfil.getSlug().getValue()).isEqualTo("salao-da-ana");
        assertThat(perfil.publicado()).isFalse();
    }

    @Test
    @DisplayName("reconfigurar o mesmo negócio ATUALIZA o perfil, não cria outro")
    void reconfigurarAtualizaOPerfilExistente() {
        configurarPerfilPublico.configurar(a, "slug-inicial", "Nome Inicial", null, null, null);
        configurarPerfilPublico.configurar(a, "slug-novo", "Nome Novo", null, null, null);

        BusinessPublicProfile perfil = perfilRepository.buscarPorBusinessId(a).orElseThrow();
        assertThat(perfil.getSlug().getValue()).isEqualTo("slug-novo");
        assertThat(perfil.getNomePublico()).isEqualTo("Nome Novo");
    }

    @Test
    @DisplayName("dois negócios não podem usar o mesmo slug")
    void doisNegociosNaoUsamOMesmoSlug() {
        configurarPerfilPublico.configurar(a, "salao-popular", "Negócio A", null, null, null);

        assertThatThrownBy(() -> configurarPerfilPublico.configurar(
                b, "salao-popular", "Negócio B", null, null, null))
                .isInstanceOf(SlugIndisponivelException.class);
    }

    @Test
    @DisplayName("atualizar o perfil de A não altera o perfil de B")
    void atualizarPerfilDeANaoAlteraB() {
        configurarPerfilPublico.configurar(a, "salao-a", "Negócio A", null, null, null);
        configurarPerfilPublico.configurar(b, "salao-b", "Negócio B", null, null, null);

        configurarPerfilPublico.configurar(a, "salao-a-renomeado", "Negócio A Renomeado", null, null, null);

        BusinessPublicProfile perfilB = perfilRepository.buscarPorBusinessId(b).orElseThrow();
        assertThat(perfilB.getSlug().getValue()).isEqualTo("salao-b");
        assertThat(perfilB.getNomePublico()).isEqualTo("Negócio B");
    }

    @Test
    @DisplayName("mudar o slug de A libera o slug antigo para outro negócio usar")
    void mudarSlugLiberaOAntigo() {
        configurarPerfilPublico.configurar(a, "slug-compartilhavel", "Negócio A", null, null, null);
        configurarPerfilPublico.configurar(a, "slug-novo-de-a", "Negócio A", null, null, null);

        // Agora B pode usar o slug que A abandonou.
        BusinessPublicProfile perfilB = configurarPerfilPublico.configurar(
                b, "slug-compartilhavel", "Negócio B", null, null, null);

        assertThat(perfilB.getSlug().getValue()).isEqualTo("slug-compartilhavel");
    }

    @Test
    @DisplayName("BusinessId é obrigatório")
    void businessIdEhObrigatorio() {
        assertThatThrownBy(() -> configurarPerfilPublico.configurar(
                null, "slug", "Nome", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
