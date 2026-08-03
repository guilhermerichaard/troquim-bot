package com.troquim_bot.catalog;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryProfessionalRepository;
import com.troquim_bot.repository.InMemoryServiceRepository;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catálogo por negócio: isolamento e vínculo profissional x serviço.
 *
 * Estes testes existem para travar duas propriedades que, se quebrarem, quebram em
 * silêncio: um negócio enxergando catálogo do outro, e "quem atende o quê" voltando a
 * depender de texto livre.
 */
@DisplayName("Catálogo - escopo por negócio e habilitação por serviço")
class CatalogoTenantScopeTest {

    private static final BusinessId SALAO_A = BusinessId.from(UUID.randomUUID());
    private static final BusinessId SALAO_B = BusinessId.from(UUID.randomUUID());

    private final ServiceRepository servicos = new InMemoryServiceRepository();
    private final ProfessionalRepository profissionais = new InMemoryProfessionalRepository();

    private Service servico(BusinessId negocio, String nome) {
        return Service.novoSemPreco(ServiceId.generate(), negocio, nome, null,
                ServiceDuration.ofMinutes(60));
    }

    private Professional profissional(BusinessId negocio, String nome, Set<ServiceId> habilitados) {
        return new Professional(ProfessionalId.generate(), negocio, nome, habilitados,
                Set.of(), "+5511999990000");
    }

    @Nested
    @DisplayName("Isolamento entre negócios")
    class Isolamento {

        @Test
        @DisplayName("serviço do salão A não aparece na listagem do salão B")
        void servicoNaoVazaEntreNegocios() {
            servicos.salvar(servico(SALAO_A, "Unhas"));

            assertThat(servicos.listarAtivos(SALAO_A)).hasSize(1);
            assertThat(servicos.listarAtivos(SALAO_B)).isEmpty();
        }

        @Test
        @DisplayName("buscar serviço do salão A com o tenant do salão B devolve vazio")
        void buscaPorIdRespeitaTenant() {
            Service doA = servicos.salvar(servico(SALAO_A, "Cabelo"));

            assertThat(servicos.buscarPorId(SALAO_A, doA.getId())).isPresent();
            // Vazio, e não "encontrado mas negado": não revela que o id existe noutro negócio.
            assertThat(servicos.buscarPorId(SALAO_B, doA.getId())).isEmpty();
        }

        @Test
        @DisplayName("remover com o tenant errado não apaga o dado do outro negócio")
        void remocaoRespeitaTenant() {
            Service doA = servicos.salvar(servico(SALAO_A, "Cílios"));

            servicos.remover(SALAO_B, doA.getId());

            assertThat(servicos.buscarPorId(SALAO_A, doA.getId())).isPresent();
        }

        @Test
        @DisplayName("profissional do salão A não aparece na listagem do salão B")
        void profissionalNaoVazaEntreNegocios() {
            profissionais.salvar(profissional(SALAO_A, "Malu", Set.of()));

            assertThat(profissionais.listarAtivos(SALAO_A)).hasSize(1);
            assertThat(profissionais.listarAtivos(SALAO_B)).isEmpty();
        }

        @Test
        @DisplayName("tenant nulo não é curinga: devolve vazio em vez de tudo")
        void tenantNuloNaoEhCuringa() {
            servicos.salvar(servico(SALAO_A, "Sobrancelha"));

            assertThat(servicos.listarAtivos(null)).isEmpty();
            assertThat(servicos.listarTodos(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Vínculo profissional x serviço")
    class Vinculo {

        @Test
        @DisplayName("profissional sem habilitação não atende nada — não é 'atende tudo'")
        void semHabilitacaoNaoAtende() {
            Service unhas = servicos.salvar(servico(SALAO_A, "Unhas"));
            Professional malu = profissionais.salvar(profissional(SALAO_A, "Malu", Set.of()));

            assertThat(malu.atende(unhas.getId())).isFalse();
            assertThat(profissionais.listarAtivosPorServico(SALAO_A, unhas.getId())).isEmpty();
        }

        @Test
        @DisplayName("habilitar por ServiceId faz o profissional ser ofertado para aquele serviço")
        void habilitacaoPorIdFunciona() {
            Service unhas = servicos.salvar(servico(SALAO_A, "Unhas"));
            Service cabelo = servicos.salvar(servico(SALAO_A, "Cabelo"));
            Professional malu = profissional(SALAO_A, "Malu", Set.of(unhas.getId()));
            profissionais.salvar(malu);

            assertThat(profissionais.listarAtivosPorServico(SALAO_A, unhas.getId()))
                    .extracting(Professional::getNome).containsExactly("Malu");
            // Habilitada para unhas não implica habilitada para cabelo.
            assertThat(profissionais.listarAtivosPorServico(SALAO_A, cabelo.getId())).isEmpty();
        }

        @Test
        @DisplayName("profissional inativo não atende, mesmo habilitado")
        void inativoNaoAtende() {
            Service unhas = servicos.salvar(servico(SALAO_A, "Unhas"));
            Professional malu = profissional(SALAO_A, "Malu", Set.of(unhas.getId()));
            malu.desativar();
            profissionais.salvar(malu);

            assertThat(malu.atende(unhas.getId())).isFalse();
            assertThat(profissionais.listarAtivosPorServico(SALAO_A, unhas.getId())).isEmpty();
        }

        @Test
        @DisplayName("habilitação não atravessa negócios")
        void habilitacaoNaoAtravessaNegocios() {
            Service doA = servicos.salvar(servico(SALAO_A, "Unhas"));
            profissionais.salvar(profissional(SALAO_A, "Malu", Set.of(doA.getId())));

            assertThat(profissionais.listarAtivosPorServico(SALAO_B, doA.getId())).isEmpty();
        }

        @Test
        @DisplayName("especialidades (texto livre) não habilitam ninguém")
        void especialidadesNaoSaoVinculo() {
            Service unhas = servicos.salvar(servico(SALAO_A, "Unhas"));
            Professional malu = new Professional(ProfessionalId.generate(), SALAO_A, "Malu",
                    Set.of(), Set.of("unhas", "manicure"), "+5511999990000");
            profissionais.salvar(malu);

            // O texto bate com o nome do serviço, e ainda assim não habilita.
            assertThat(malu.atende(unhas.getId())).isFalse();
            assertThat(profissionais.listarAtivosPorServico(SALAO_A, unhas.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Invariantes dos agregados")
    class Invariantes {

        @Test
        @DisplayName("serviço sem BusinessId é recusado")
        void servicoExigeTenant() {
            assertThatThrownBy(() -> Service.novoSemPreco(ServiceId.generate(), null, "Unhas", null,
                    ServiceDuration.ofMinutes(60)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BusinessId");
        }

        @Test
        @DisplayName("profissional sem BusinessId é recusado")
        void profissionalExigeTenant() {
            assertThatThrownBy(() -> new Professional(ProfessionalId.generate(), null, "Malu",
                    Set.of(), Set.of(), "+5511999990000"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BusinessId");
        }

        @Test
        @DisplayName("preço é opcional: serviço sem preço é válido e não vira zero")
        void precoOpcionalNaoViraZero() {
            Service semPreco = servicos.salvar(servico(SALAO_A, "Unhas"));

            assertThat(semPreco.temPreco()).isFalse();
            // Contrato do domínio: ausência é Optional.empty(), nunca null.
            assertThat(semPreco.getPreco()).isEmpty();
        }
    }
}
