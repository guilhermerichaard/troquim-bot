package com.troquim_bot.application.business;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.PublicationStatus;
import com.troquim_bot.repository.InMemoryBusinessPublicProfileRepository;
import com.troquim_bot.repository.InMemoryBusinessRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DespublicarPerfilPublico - PUBLISHED para DRAFT, idempotente")
class DespublicarPerfilPublicoTest {

    private InMemoryBusinessRepository businessRepository;
    private InMemoryBusinessPublicProfileRepository perfilRepository;
    private ConfigurarPerfilPublico configurarPerfilPublico;
    private PublicarPerfilPublico publicarPerfilPublico;
    private DespublicarPerfilPublico despublicarPerfilPublico;

    private BusinessId id;

    @BeforeEach
    void montar() {
        businessRepository = new InMemoryBusinessRepository();
        perfilRepository = new InMemoryBusinessPublicProfileRepository();
        configurarPerfilPublico = new ConfigurarPerfilPublico(businessRepository, perfilRepository);
        publicarPerfilPublico = new PublicarPerfilPublico(businessRepository, perfilRepository);
        despublicarPerfilPublico = new DespublicarPerfilPublico(perfilRepository);

        id = BusinessId.from(UUID.randomUUID());
        businessRepository.save(new Business(id, "Negócio", null, null));
        configurarPerfilPublico.configurar(id, "salao-da-ana", "Salão da Ana", null, null, null);
    }

    @Test
    @DisplayName("despublica um perfil PUBLISHED")
    void despublicaPerfilPublicado() {
        publicarPerfilPublico.publicar(id);

        despublicarPerfilPublico.despublicar(id);

        assertThat(perfilRepository.buscarPorBusinessId(id).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    @DisplayName("despublicar é idempotente: despublicar um DRAFT não falha")
    void despublicarEhIdempotente() {
        assertThatCode(() -> despublicarPerfilPublico.despublicar(id)).doesNotThrowAnyException();
        assertThatCode(() -> despublicarPerfilPublico.despublicar(id)).doesNotThrowAnyException();
        assertThat(perfilRepository.buscarPorBusinessId(id).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    @DisplayName("recusa despublicar sem perfil configurado")
    void recusaDespublicarSemPerfil() {
        BusinessId semPerfil = BusinessId.from(UUID.randomUUID());
        businessRepository.save(new Business(semPerfil, "Outro", null, null));

        assertThatThrownBy(() -> despublicarPerfilPublico.despublicar(semPerfil))
                .isInstanceOf(IllegalStateException.class);
    }
}
