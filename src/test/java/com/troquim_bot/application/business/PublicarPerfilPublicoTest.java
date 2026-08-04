package com.troquim_bot.application.business;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.business.PublicationStatus;
import com.troquim_bot.repository.InMemoryBusinessPublicProfileRepository;
import com.troquim_bot.repository.InMemoryBusinessRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PublicarPerfilPublico - DRAFT para PUBLISHED, idempotente")
class PublicarPerfilPublicoTest {

    private InMemoryBusinessRepository businessRepository;
    private InMemoryBusinessPublicProfileRepository perfilRepository;
    private ConfigurarPerfilPublico configurarPerfilPublico;
    private PublicarPerfilPublico publicarPerfilPublico;

    private BusinessId id;

    @BeforeEach
    void montar() {
        businessRepository = new InMemoryBusinessRepository();
        perfilRepository = new InMemoryBusinessPublicProfileRepository();
        configurarPerfilPublico = new ConfigurarPerfilPublico(businessRepository, perfilRepository);
        publicarPerfilPublico = new PublicarPerfilPublico(businessRepository, perfilRepository);

        id = BusinessId.from(UUID.randomUUID());
        businessRepository.save(new Business(id, "Negócio", null, null));
    }

    @Test
    @DisplayName("publica o perfil configurado")
    void publicaOPerfilConfigurado() {
        configurarPerfilPublico.configurar(id, "salao-da-ana", "Salão da Ana", null, null, null);

        BusinessPublicProfile publicado = publicarPerfilPublico.publicar(id);

        assertThat(publicado.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    }

    @Test
    @DisplayName("publicar é idempotente: publicar duas vezes não falha")
    void publicarEhIdempotente() {
        configurarPerfilPublico.configurar(id, "salao-da-ana", "Salão da Ana", null, null, null);
        publicarPerfilPublico.publicar(id);

        assertThatCode(() -> publicarPerfilPublico.publicar(id)).doesNotThrowAnyException();
        assertThat(perfilRepository.buscarPorBusinessId(id).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.PUBLISHED);
    }

    @Test
    @DisplayName("recusa publicar sem perfil configurado")
    void recusaPublicarSemPerfilConfigurado() {
        assertThatThrownBy(() -> publicarPerfilPublico.publicar(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ConfigurarPerfilPublico");
    }

    @Test
    @DisplayName("recusa publicar de negócio inexistente")
    void recusaPublicarNegocioInexistente() {
        BusinessId inexistente = BusinessId.from(UUID.randomUUID());

        assertThatThrownBy(() -> publicarPerfilPublico.publicar(inexistente))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = com.troquim_bot.business.BusinessStatus.class, names = {"INATIVO", "SUSPENSO", "DELETADO"})
    @DisplayName("negócio inativo, suspenso ou deletado não pode publicar")
    void negocioInativoSuspensoOuDeletadoNaoPublica(com.troquim_bot.business.BusinessStatus status) {
        BusinessId outro = BusinessId.from(UUID.randomUUID());
        Business negocio = new Business(outro, "Negócio Fechado", null, null);
        if (status == com.troquim_bot.business.BusinessStatus.INATIVO) {
            negocio.desativar();
        } else if (status == com.troquim_bot.business.BusinessStatus.SUSPENSO) {
            negocio.suspender();
        } else {
            negocio.deletar();
        }
        businessRepository.save(negocio);
        configurarPerfilPublico.configurar(outro, "salao-fechado", "Salão Fechado", null, null, null);

        assertThatThrownBy(() -> publicarPerfilPublico.publicar(outro))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(status.name());
    }

    @Test
    @DisplayName("negócio TRIAL pode publicar")
    void negocioTrialPodePublicar() {
        // O fixture já cria em TRIAL (default de Business).
        configurarPerfilPublico.configurar(id, "salao-trial", "Salão Trial", null, null, null);

        assertThatCode(() -> publicarPerfilPublico.publicar(id)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("negócio ATIVO pode publicar")
    void negocioAtivoPodePublicar() {
        Business negocio = businessRepository.findById(id);
        negocio.ativar();
        businessRepository.save(negocio);
        configurarPerfilPublico.configurar(id, "salao-ativo", "Salão Ativo", null, null, null);

        assertThatCode(() -> publicarPerfilPublico.publicar(id)).doesNotThrowAnyException();
    }
}
