package com.troquim_bot.config;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.infrastructure.persistence.JpaProfessionalRepository;
import com.troquim_bot.infrastructure.persistence.JpaServiceRepository;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A guarda que impede produção de subir com catálogo volátil.
 *
 * O risco que ela cobre é traiçoeiro: sem ela, faltando o adapter JPA, o contexto escolhe
 * o repositório em memória e a aplicação sobe "funcionando" — com o catálogo evaporando a
 * cada restart e nenhum erro no log. Trocar isso por falha de inicialização é o ponto.
 */
@DisplayName("Guarda de persistência do catálogo")
class CatalogoPersistenceGuardTest {

    /** Duplo em memória qualquer, representando o que NÃO pode ser aceito em produção. */
    private static ServiceRepository repositorioVolatilDeServico() {
        return new ServiceRepository() {
            @Override public Service salvar(Service service) { return service; }
            @Override public Optional<Service> buscarPorId(BusinessId b, ServiceId i) { return Optional.empty(); }
            @Override public List<Service> listarAtivos(BusinessId b) { return List.of(); }
            @Override public List<Service> listarTodos(BusinessId b) { return List.of(); }
            @Override public void remover(BusinessId b, ServiceId i) { }
        };
    }

    private static ProfessionalRepository repositorioVolatilDeProfissional() {
        return new ProfessionalRepository() {
            @Override public Professional salvar(Professional p) { return p; }
            @Override public Optional<Professional> buscarPorId(BusinessId b, ProfessionalId i) { return Optional.empty(); }
            @Override public List<Professional> listarAtivos(BusinessId b) { return List.of(); }
            @Override public List<Professional> listarAtivosPorServico(BusinessId b, ServiceId s) { return List.of(); }
            @Override public List<Professional> listarTodos(BusinessId b) { return List.of(); }
            @Override public void remover(BusinessId b, ProfessionalId i) { }
        };
    }

    @Test
    @DisplayName("recusa subir quando o catálogo não é o adapter JPA")
    void recusaRepositorioVolatilDeServico() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                repositorioVolatilDeServico(),
                Mockito.mock(JpaProfessionalRepository.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ServiceRepository")
                .hasMessageContaining("Catálogo sem persistência");
    }

    @Test
    @DisplayName("recusa subir quando os profissionais não são o adapter JPA")
    void recusaRepositorioVolatilDeProfissional() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                Mockito.mock(JpaServiceRepository.class),
                repositorioVolatilDeProfissional()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ProfessionalRepository");
    }

    @Test
    @DisplayName("a mensagem explica a consequência, não só o sintoma")
    void mensagemExplicaConsequencia() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                repositorioVolatilDeServico(),
                Mockito.mock(JpaProfessionalRepository.class)))
                .hasMessageContaining("perderia serviços e habilitações a cada restart");
    }

    @Test
    @DisplayName("aceita quando ambos são os adapters JPA")
    void aceitaAdaptersJpa() {
        assertThatCode(() -> new CatalogoPersistenceGuard(
                Mockito.mock(JpaServiceRepository.class),
                Mockito.mock(JpaProfessionalRepository.class)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("repositório nulo também é recusado, com mensagem clara")
    void recusaRepositorioAusente() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                null, Mockito.mock(JpaProfessionalRepository.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nenhum bean");
    }

    @Test
    @DisplayName("os duplos em memória estão restritos a perfis explícitos")
    void duplosEmMemoriaTemPerfilRestrito() {
        // Impede a regressão de alguém remover o @Profile e reabrir a porta para o
        // catálogo volátil entrar em produção pela injeção.
        var perfilServico = com.troquim_bot.repository.InMemoryServiceRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);
        var perfilProfissional = com.troquim_bot.repository.InMemoryProfessionalRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);

        assertThat(perfilServico).isNotNull();
        assertThat(perfilServico.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
        assertThat(perfilProfissional).isNotNull();
        assertThat(perfilProfissional.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
    }
}
