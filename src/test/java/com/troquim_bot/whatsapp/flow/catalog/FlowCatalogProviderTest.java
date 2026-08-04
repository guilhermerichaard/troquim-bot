package com.troquim_bot.whatsapp.flow.catalog;

import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryProfessionalRepository;
import com.troquim_bot.repository.InMemoryServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowCatalogProvider;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowProfessionalOption;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowServiceOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O Flow enxerga o catálogo PERSISTIDO — e só ele.
 *
 * Cada teste aqui trava uma propriedade que, se quebrar, quebra em silêncio: um salão
 * vendo o cardápio do outro, um serviço desativado continuando à venda, ou alguém sendo
 * oferecido para um serviço que não sabe fazer.
 */
@DisplayName("Flow - catálogo vem do repositório, nunca de lista fixa")
class FlowCatalogProviderTest {

    private static final BusinessId SALAO_A = BusinessId.from(UUID.randomUUID());
    private static final BusinessId SALAO_B = BusinessId.from(UUID.randomUUID());
    private static final BusinessId SEM_CATALOGO = BusinessId.from(UUID.randomUUID());

    private final InMemoryServiceRepository servicos = new InMemoryServiceRepository();
    private final InMemoryProfessionalRepository profissionais = new InMemoryProfessionalRepository();
    private final FlowCatalogProvider provider =
            new FlowCatalogProvider(new ConsultarCatalogo(servicos, profissionais));

    private Service servicoAtivo(BusinessId negocio, String nome) {
        return servicos.salvar(Service.novoSemPreco(ServiceId.generate(), negocio, nome, null,
                ServiceDuration.ofMinutes(60)));
    }

    private Professional profissional(BusinessId negocio, String nome, Set<ServiceId> habilitados) {
        return profissionais.salvar(new Professional(ProfessionalId.generate(), negocio, nome,
                habilitados, Set.of(), "+5511999990000"));
    }

    @Nested
    @DisplayName("Isolamento entre negócios")
    class Isolamento {

        @Test
        @DisplayName("negócios diferentes recebem catálogos diferentes")
        void catalogosSaoPorNegocio() {
            Service unhasA = servicoAtivo(SALAO_A, "Unhas");
            profissional(SALAO_A, "Malu", Set.of(unhasA.getId()));
            Service barbaB = servicoAtivo(SALAO_B, "Barba");
            profissional(SALAO_B, "Rui", Set.of(barbaB.getId()));

            assertThat(provider.catalogo(SALAO_A).servicos())
                    .extracting(FlowServiceOption::titulo).containsExactly("Unhas");
            assertThat(provider.catalogo(SALAO_B).servicos())
                    .extracting(FlowServiceOption::titulo).containsExactly("Barba");
            assertThat(provider.catalogo(SALAO_A).profissionais())
                    .extracting(FlowProfessionalOption::titulo).containsExactly("Malu");
        }

        @Test
        @DisplayName("serviço de outro negócio não é resolvível pelo id")
        void servicoDeOutroTenantNaoResolve() {
            Service doA = servicoAtivo(SALAO_A, "Unhas");
            profissional(SALAO_A, "Malu", Set.of(doA.getId()));
            String idTextual = doA.getId().getValue().toString();

            assertThat(provider.servicoPorId(SALAO_A, idTextual)).isPresent();
            // Vazio, e não "encontrado mas negado": o salão B não descobre que o id existe.
            assertThat(provider.servicoPorId(SALAO_B, idTextual)).isEmpty();
        }
    }

    @Nested
    @DisplayName("O que NÃO pode ser ofertado")
    class NaoOfertavel {

        @Test
        @DisplayName("serviço inativo não aparece")
        void servicoInativoNaoAparece() {
            Service unhas = servicoAtivo(SALAO_A, "Unhas");
            profissional(SALAO_A, "Malu", Set.of(unhas.getId()));
            assertThat(provider.catalogo(SALAO_A).servicos()).hasSize(1);

            unhas.desativar();
            servicos.salvar(unhas);

            assertThat(provider.catalogo(SALAO_A).servicos()).isEmpty();
            assertThat(provider.servicoPorId(SALAO_A, unhas.getId().getValue().toString())).isEmpty();
        }

        @Test
        @DisplayName("profissional inativo não aparece")
        void profissionalInativoNaoAparece() {
            Service unhas = servicoAtivo(SALAO_A, "Unhas");
            profissional(SALAO_A, "Ativa", Set.of(unhas.getId()));
            Professional afastada = profissional(SALAO_A, "Afastada", Set.of(unhas.getId()));

            afastada.desativar();
            profissionais.salvar(afastada);

            FlowServiceOption servico = provider.servicoPorId(
                    SALAO_A, unhas.getId().getValue().toString()).orElseThrow();
            assertThat(provider.profissionaisPara(SALAO_A, servico))
                    .extracting(FlowProfessionalOption::titulo).containsExactly("Ativa");
            assertThat(provider.profissionalPara(SALAO_A, servico,
                    afastada.getId().getValue().toString())).isEmpty();
        }

        @Test
        @DisplayName("profissional não habilitado para o serviço não aparece")
        void profissionalNaoHabilitadoNaoAparece() {
            Service unhas = servicoAtivo(SALAO_A, "Unhas");
            Service cabelo = servicoAtivo(SALAO_A, "Cabelo");
            Professional soUnhas = profissional(SALAO_A, "Malu", Set.of(unhas.getId()));
            profissional(SALAO_A, "Rita", Set.of(cabelo.getId()));

            FlowServiceOption doCabelo = provider.servicoPorId(
                    SALAO_A, cabelo.getId().getValue().toString()).orElseThrow();

            assertThat(provider.profissionaisPara(SALAO_A, doCabelo))
                    .extracting(FlowProfessionalOption::titulo).containsExactly("Rita");
            assertThat(provider.profissionalPara(SALAO_A, doCabelo,
                    soUnhas.getId().getValue().toString())).isEmpty();
        }

        @Test
        @DisplayName("especialidade em texto livre não habilita serviço nenhum")
        void especialidadeTextualNaoHabilita() {
            Service unhas = servicoAtivo(SALAO_A, "Unhas");
            // O texto bate com o nome do serviço — e ainda assim não vale como vínculo.
            profissionais.salvar(new Professional(ProfessionalId.generate(), SALAO_A, "Malu",
                    Set.of(), Set.of("unhas", "manicure"), "+5511999990000"));

            assertThat(provider.catalogo(SALAO_A).servicos()).isEmpty();
            assertThat(provider.servicoPorId(SALAO_A, unhas.getId().getValue().toString())).isEmpty();
        }

        @Test
        @DisplayName("serviço sem ninguém habilitado não é ofertável")
        void servicoSemProfissionalNaoEhOfertavel() {
            Service unhas = servicoAtivo(SALAO_A, "Unhas");

            assertThat(provider.catalogo(SALAO_A).servicos()).isEmpty();
            assertThat(provider.profissionaisPara(SALAO_A,
                    new FlowServiceOption(unhas.getId(), "Unhas", java.time.Duration.ofHours(1))))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Negócio sem catálogo")
    class SemCatalogo {

        @Test
        @DisplayName("recebe condição EXPLÍCITA de não configurado — nunca lista de emergência")
        void condicaoExplicita() {
            FlowCatalogProvider.CatalogoDoFlow vazio = provider.catalogo(SEM_CATALOGO);

            assertThat(vazio.naoConfigurado()).isTrue();
            assertThat(vazio.servicos()).isEmpty();
            assertThat(vazio.profissionais()).isEmpty();
            // Nenhum id resolve: não há catálogo alternativo escondido em lugar nenhum.
            assertThat(provider.servicoPorId(SEM_CATALOGO, UUID.randomUUID().toString())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Identidade do serviço")
    class Identidade {

        @Test
        @DisplayName("o id trafegado é o UUID do ServiceId, sem transformação")
        void idEhOUuidDoServico() {
            Service unhas = servicoAtivo(SALAO_A, "Unhas");
            profissional(SALAO_A, "Malu", Set.of(unhas.getId()));

            FlowServiceOption opcao = provider.catalogo(SALAO_A).servicos().get(0);

            assertThat(opcao.servicoId()).isEqualTo(unhas.getId());
            assertThat(opcao.id()).isEqualTo(unhas.getId().getValue().toString());
        }

        @Test
        @DisplayName("id que não é UUID é recusado, sem derivar outro identificador")
        void uuidInvalidoEhRecusado() {
            Service unhas = servicoAtivo(SALAO_A, "Unhas");
            profissional(SALAO_A, "Malu", Set.of(unhas.getId()));

            for (String lixo : new String[] {"unha", "Unhas", "", "   ", "nao-e-uuid",
                    "11111111-1111-1111-1111-11111111111"}) {
                assertThat(provider.servicoPorId(SALAO_A, lixo))
                        .as("id inválido '%s' não pode resolver serviço", lixo)
                        .isEmpty();
            }
            assertThat(provider.servicoPorId(SALAO_A, null)).isEmpty();
        }

        @Test
        @DisplayName("UUID bem formado porém desconhecido é recusado")
        void uuidDesconhecidoEhRecusado() {
            Service unhas = servicoAtivo(SALAO_A, "Unhas");
            profissional(SALAO_A, "Malu", Set.of(unhas.getId()));

            assertThat(provider.servicoPorId(SALAO_A, UUID.randomUUID().toString())).isEmpty();
        }
    }

    @Nested
    @DisplayName("O provider não é dono de catálogo")
    class SemListaPropria {

        @Test
        @DisplayName("não existe nenhuma coleção estática de serviços/profissionais na classe")
        void semColecaoEstatica() {
            for (Field campo : FlowCatalogProvider.class.getDeclaredFields()) {
                if (!Modifier.isStatic(campo.getModifiers())) {
                    continue;
                }
                assertThat(Collection.class.isAssignableFrom(campo.getType())
                        || Map.class.isAssignableFrom(campo.getType())
                        || campo.getType().isArray())
                        .as("campo estático '%s' parece uma lista fixa de catálogo", campo.getName())
                        .isFalse();
            }
        }

        @Test
        @DisplayName("sem repositório populado, o Flow simplesmente não tem o que oferecer")
        void semRepositorioNaoHaCatalogo() {
            // Se sobrasse qualquer lista embutida, ela apareceria exatamente aqui.
            FlowCatalogProvider semNada = new FlowCatalogProvider(
                    new ConsultarCatalogo(new InMemoryServiceRepository(),
                            new InMemoryProfessionalRepository()));

            assertThat(semNada.catalogo(SALAO_A).servicos()).isEmpty();
            assertThat(semNada.catalogo(SALAO_A).profissionais()).isEmpty();
            assertThat(semNada.catalogo(SALAO_A).naoConfigurado()).isTrue();
        }

        @Test
        @DisplayName("profissional só é resolvido dentro de um serviço; sem serviço, vazio")
        void semServicoNaoHaProfissional() {
            Optional<FlowProfessionalOption> nenhum =
                    provider.profissionalPara(SALAO_A, null, UUID.randomUUID().toString());
            assertThat(nenhum).isEmpty();
        }
    }
}
