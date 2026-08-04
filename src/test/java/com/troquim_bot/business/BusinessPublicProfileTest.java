package com.troquim_bot.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BusinessPublicProfile - ciclo de publicação")
class BusinessPublicProfileTest {

    private BusinessId negocio() {
        return BusinessId.from(UUID.randomUUID());
    }

    @Test
    @DisplayName("configuração começa em DRAFT")
    void configuracaoComecaEmDraft() {
        BusinessPublicProfile perfil = new BusinessPublicProfile(negocio(),
                BusinessSlug.normalizarDe("salao-da-ana"), "Salão da Ana", null, null, null);

        assertThat(perfil.getStatus()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(perfil.publicado()).isFalse();
    }

    @Test
    @DisplayName("publicar muda DRAFT para PUBLISHED")
    void publicarMudaParaPublished() {
        BusinessPublicProfile perfil = new BusinessPublicProfile(negocio(),
                BusinessSlug.normalizarDe("salao-da-ana"), "Salão da Ana", null, null, null);

        perfil.publicar();

        assertThat(perfil.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(perfil.publicado()).isTrue();
    }

    @Test
    @DisplayName("publicar é idempotente: publicar de novo não falha e mantém PUBLISHED")
    void publicarEhIdempotente() {
        BusinessPublicProfile perfil = new BusinessPublicProfile(negocio(),
                BusinessSlug.normalizarDe("salao-da-ana"), "Salão da Ana", null, null, null);

        perfil.publicar();
        assertThatCode(perfil::publicar).doesNotThrowAnyException();
        assertThat(perfil.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    }

    @Test
    @DisplayName("despublicar é idempotente: despublicar um DRAFT não falha e mantém DRAFT")
    void despublicarEhIdempotente() {
        BusinessPublicProfile perfil = new BusinessPublicProfile(negocio(),
                BusinessSlug.normalizarDe("salao-da-ana"), "Salão da Ana", null, null, null);

        assertThatCode(perfil::despublicar).doesNotThrowAnyException();
        assertThat(perfil.getStatus()).isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    @DisplayName("despublicar volta PUBLISHED para DRAFT")
    void despublicarVoltaParaDraft() {
        BusinessPublicProfile perfil = new BusinessPublicProfile(negocio(),
                BusinessSlug.normalizarDe("salao-da-ana"), "Salão da Ana", null, null, null);
        perfil.publicar();

        perfil.despublicar();

        assertThat(perfil.getStatus()).isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    @DisplayName("BusinessId é obrigatório")
    void businessIdEhObrigatorio() {
        assertThatThrownBy(() -> new BusinessPublicProfile(null,
                BusinessSlug.normalizarDe("salao-da-ana"), "Salão da Ana", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("slug é obrigatório")
    void slugEhObrigatorio() {
        assertThatThrownBy(() -> new BusinessPublicProfile(negocio(), null, "Salão da Ana", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nome público é obrigatório")
    void nomePublicoEhObrigatorio() {
        assertThatThrownBy(() -> new BusinessPublicProfile(negocio(),
                BusinessSlug.normalizarDe("salao-da-ana"), "  ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("atualizar configuração não muda o status de publicação")
    void atualizarConfiguracaoNaoMudaStatus() {
        BusinessPublicProfile perfil = new BusinessPublicProfile(negocio(),
                BusinessSlug.normalizarDe("salao-da-ana"), "Salão da Ana", null, null, null);
        perfil.publicar();

        perfil.atualizarConfiguracao(BusinessSlug.normalizarDe("novo-slug"), "Novo Nome", null, null, null);

        assertThat(perfil.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(perfil.getSlug().getValue()).isEqualTo("novo-slug");
        assertThat(perfil.getNomePublico()).isEqualTo("Novo Nome");
    }

    @Test
    @DisplayName("descrição, telefone e endereço são opcionais e ficam nulos quando em branco")
    void camposOpcionaisFicamNulosQuandoEmBranco() {
        BusinessPublicProfile perfil = new BusinessPublicProfile(negocio(),
                BusinessSlug.normalizarDe("salao-da-ana"), "Salão da Ana", "  ", "", null);

        assertThat(perfil.getDescricaoCurta()).isNull();
        assertThat(perfil.getTelefonePublico()).isNull();
        assertThat(perfil.getEnderecoPublico()).isNull();
    }
}
