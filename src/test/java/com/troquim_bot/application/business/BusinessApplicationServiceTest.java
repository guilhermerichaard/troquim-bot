package com.troquim_bot.application.business;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessStatus;
import com.troquim_bot.repository.InMemoryBusinessRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BusinessApplicationService — toda operação exige BusinessId, e uma alteração num negócio
 * nunca pode vazar para outro.
 */
@DisplayName("BusinessApplicationService - operações tenant-scoped, isoladas por negócio")
class BusinessApplicationServiceTest {

    private InMemoryBusinessRepository businessRepository;
    private BusinessApplicationService businessApplicationService;

    private BusinessId a;
    private BusinessId b;

    @BeforeEach
    void montar() {
        businessRepository = new InMemoryBusinessRepository();
        businessApplicationService = new BusinessApplicationService(businessRepository);

        a = BusinessId.from(UUID.randomUUID());
        b = BusinessId.from(UUID.randomUUID());
        businessRepository.save(new Business(a, "Negócio A", "(11) 11111-1111", null));
        businessRepository.save(new Business(b, "Negócio B", "(11) 22222-2222", null));
    }

    @Test
    @DisplayName("ativar A não altera o status de B")
    void ativarNaoAlteraOOutro() {
        businessApplicationService.ativarBusiness(a);

        assertThat(businessApplicationService.buscarPorId(a).orElseThrow().getStatus())
                .isEqualTo(BusinessStatus.ATIVO);
        assertThat(businessApplicationService.buscarPorId(b).orElseThrow().getStatus())
                .isEqualTo(BusinessStatus.TRIAL);
    }

    @Test
    @DisplayName("desativar A não altera o status de B")
    void desativarNaoAlteraOOutro() {
        businessApplicationService.ativarBusiness(a);
        businessApplicationService.ativarBusiness(b);

        businessApplicationService.desativarBusiness(a);

        assertThat(businessApplicationService.buscarPorId(a).orElseThrow().getStatus())
                .isEqualTo(BusinessStatus.INATIVO);
        assertThat(businessApplicationService.buscarPorId(b).orElseThrow().getStatus())
                .isEqualTo(BusinessStatus.ATIVO);
    }

    @Test
    @DisplayName("atualizar nome de A não altera nome nem contato de B")
    void atualizarNomeNaoAlteraOOutro() {
        businessApplicationService.atualizarNome(a, "Negócio A Renomeado");

        assertThat(businessApplicationService.buscarPorId(a).orElseThrow().getNome())
                .isEqualTo("Negócio A Renomeado");
        assertThat(businessApplicationService.buscarPorId(b).orElseThrow().getNome())
                .isEqualTo("Negócio B");
        assertThat(businessApplicationService.buscarPorId(b).orElseThrow().getTelefone())
                .isEqualTo("(11) 22222-2222");
    }

    @Test
    @DisplayName("nome técnico migrado (V13) pode ser substituído explicitamente pelo BusinessId")
    void nomeTecnicoMigradoPodeSerAtualizado() {
        BusinessId migrado = BusinessId.from(UUID.randomUUID());
        String prefixo = migrado.getValue().toString().substring(0, 8);
        businessRepository.save(new Business(migrado, "Negocio migrado " + prefixo, null, null));

        Business atualizado = businessApplicationService.atualizarNome(migrado, "Salão da Gizelle");
        businessApplicationService.atualizarTelefone(migrado, "(11) 90000-0000");
        businessApplicationService.atualizarEndereco(migrado, "Rua Real, 1");

        assertThat(atualizado.getNome()).isEqualTo("Salão da Gizelle");
        Business recarregado = businessApplicationService.buscarPorId(migrado).orElseThrow();
        assertThat(recarregado.getNome()).isEqualTo("Salão da Gizelle");
        assertThat(recarregado.getTelefone()).isEqualTo("(11) 90000-0000");
        assertThat(recarregado.getEndereco()).isEqualTo("Rua Real, 1");
    }

    @Test
    @DisplayName("existeBusiness e isBusinessAtivo respeitam o BusinessId informado, não um estado global")
    void existeEAtivoSaoPorBusinessId() {
        BusinessId inexistente = BusinessId.from(UUID.randomUUID());

        assertThat(businessApplicationService.existeBusiness(a)).isTrue();
        assertThat(businessApplicationService.existeBusiness(inexistente)).isFalse();

        businessApplicationService.ativarBusiness(a);
        businessApplicationService.desativarBusiness(b);
        assertThat(businessApplicationService.isBusinessAtivo(a)).isTrue();
        assertThat(businessApplicationService.isBusinessAtivo(b)).isFalse();
    }
}
