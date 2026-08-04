package com.troquim_bot.config;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.infrastructure.persistence.JpaAvailabilityRepository;
import com.troquim_bot.infrastructure.persistence.JpaBusinessHoursRepository;
import com.troquim_bot.infrastructure.persistence.JpaProfessionalRepository;
import com.troquim_bot.infrastructure.persistence.JpaServiceRepository;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.BusinessHoursRepository;
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
 * A guarda que impede produção de subir com catálogo ou AGENDA voláteis.
 *
 * O risco que ela cobre é traiçoeiro: sem ela, faltando o adapter JPA, o contexto escolhe
 * o repositório em memória e a aplicação sobe "funcionando" — com o catálogo e o expediente
 * evaporando a cada restart e nenhum erro no log. Trocar isso por falha de inicialização é
 * o ponto.
 */
@DisplayName("Guarda de persistência do catálogo e da agenda")
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

    private static BusinessHoursRepository repositorioVolatilDeExpediente() {
        return new BusinessHoursRepository() {
            @Override public void salvar(BusinessId b, BusinessHours h) { }
            @Override public BusinessHours buscar(BusinessId b) { return BusinessHours.naoConfigurado(); }
            @Override public boolean configurado(BusinessId b) { return false; }
        };
    }

    private static AvailabilityRepository repositorioVolatilDeDisponibilidade() {
        return new AvailabilityRepository() {
            @Override public Availability salvar(Availability a) { return a; }
            @Override public Optional<Availability> buscarPorId(BusinessId b, AvailabilityId i) { return Optional.empty(); }
            @Override public boolean existe(BusinessId b, AvailabilityId i) { return false; }
            @Override public List<Availability> listarPorNegocio(BusinessId b) { return List.of(); }
            @Override public List<Availability> listarPorProfissional(BusinessId b, ProfessionalId p) { return List.of(); }
            @Override public List<Availability> listarAtivasPorProfissionalEDia(BusinessId b, ProfessionalId p, DiaSemana d) { return List.of(); }
            @Override public void remover(BusinessId b, AvailabilityId i) { }
        };
    }

    private static JpaServiceRepository servicoJpa() {
        return Mockito.mock(JpaServiceRepository.class);
    }

    private static JpaProfessionalRepository profissionalJpa() {
        return Mockito.mock(JpaProfessionalRepository.class);
    }

    private static JpaBusinessHoursRepository expedienteJpa() {
        return Mockito.mock(JpaBusinessHoursRepository.class);
    }

    private static JpaAvailabilityRepository disponibilidadeJpa() {
        return Mockito.mock(JpaAvailabilityRepository.class);
    }

    @Test
    @DisplayName("recusa subir quando o catálogo não é o adapter JPA")
    void recusaRepositorioVolatilDeServico() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                repositorioVolatilDeServico(), profissionalJpa(), expedienteJpa(), disponibilidadeJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ServiceRepository")
                .hasMessageContaining("Catálogo sem persistência");
    }

    @Test
    @DisplayName("recusa subir quando os profissionais não são o adapter JPA")
    void recusaRepositorioVolatilDeProfissional() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                servicoJpa(), repositorioVolatilDeProfissional(), expedienteJpa(), disponibilidadeJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ProfessionalRepository");
    }

    @Test
    @DisplayName("recusa subir quando o EXPEDIENTE não é o adapter JPA")
    void recusaExpedienteVolatil() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                servicoJpa(), profissionalJpa(), repositorioVolatilDeExpediente(), disponibilidadeJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BusinessHoursRepository");
    }

    @Test
    @DisplayName("recusa subir quando a DISPONIBILIDADE não é o adapter JPA")
    void recusaDisponibilidadeVolatil() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                servicoJpa(), profissionalJpa(), expedienteJpa(), repositorioVolatilDeDisponibilidade()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AvailabilityRepository");
    }

    @Test
    @DisplayName("a mensagem explica a consequência, não só o sintoma")
    void mensagemExplicaConsequencia() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                repositorioVolatilDeServico(), profissionalJpa(), expedienteJpa(), disponibilidadeJpa()))
                .hasMessageContaining("perderia serviços e habilitações a cada restart");
    }

    @Test
    @DisplayName("aceita quando todos são os adapters JPA")
    void aceitaAdaptersJpa() {
        assertThatCode(() -> new CatalogoPersistenceGuard(
                servicoJpa(), profissionalJpa(), expedienteJpa(), disponibilidadeJpa()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("repositório nulo também é recusado, com mensagem clara")
    void recusaRepositorioAusente() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                null, profissionalJpa(), expedienteJpa(), disponibilidadeJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nenhum bean");
    }

    @Test
    @DisplayName("os duplos em memória estão restritos a perfis explícitos")
    void duplosEmMemoriaTemPerfilRestrito() {
        // Impede a regressão de alguém remover o @Profile e reabrir a porta para o
        // catálogo ou a agenda voláteis entrarem em produção pela injeção.
        var perfilServico = com.troquim_bot.repository.InMemoryServiceRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);
        var perfilProfissional = com.troquim_bot.repository.InMemoryProfessionalRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);
        var perfilExpediente = com.troquim_bot.repository.InMemoryBusinessHoursRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);
        var perfilDisponibilidade = com.troquim_bot.repository.InMemoryAvailabilityRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);

        assertThat(perfilServico).isNotNull();
        assertThat(perfilServico.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
        assertThat(perfilProfissional).isNotNull();
        assertThat(perfilProfissional.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
        assertThat(perfilExpediente).isNotNull();
        assertThat(perfilExpediente.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
        assertThat(perfilDisponibilidade).isNotNull();
        assertThat(perfilDisponibilidade.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
    }
}
