package com.troquim_bot.config;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessCalendar;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.business.BusinessSlug;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.infrastructure.persistence.JpaAvailabilityRepository;
import com.troquim_bot.infrastructure.persistence.JpaBusinessCalendarRepository;
import com.troquim_bot.infrastructure.persistence.JpaBusinessPublicProfileRepository;
import com.troquim_bot.infrastructure.persistence.JpaBusinessRepository;
import com.troquim_bot.infrastructure.persistence.JpaProfessionalRepository;
import com.troquim_bot.infrastructure.persistence.JpaServiceRepository;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.BusinessCalendarRepository;
import com.troquim_bot.repository.BusinessPublicProfileRepository;
import com.troquim_bot.repository.BusinessRepository;
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
 * A guarda que impede produção de subir com catálogo, calendário, identidade de negócio ou
 * perfil público voláteis.
 *
 * O risco que ela cobre é traiçoeiro: sem ela, faltando o adapter JPA, o contexto escolhe
 * o repositório em memória e a aplicação sobe "funcionando" — com o catálogo e o expediente
 * evaporando a cada restart e nenhum erro no log. Trocar isso por falha de inicialização é
 * o ponto.
 */
@DisplayName("Guarda de persistência do catálogo, calendário, negócio e perfil público")
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

    private static BusinessCalendarRepository repositorioVolatilDeExpediente() {
        return new BusinessCalendarRepository() {
            @Override public void salvar(BusinessCalendar c) { }
            @Override public BusinessCalendar buscar(BusinessId b) { return BusinessCalendar.naoConfigurado(b); }
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

    private static BusinessRepository repositorioVolatilDeNegocio() {
        return new BusinessRepository() {
            @Override public Business save(Business b) { return b; }
            @Override public Business findById(BusinessId id) { return null; }
            @Override public boolean exists(BusinessId id) { return false; }
        };
    }

    private static BusinessPublicProfileRepository repositorioVolatilDePerfilPublico() {
        return new BusinessPublicProfileRepository() {
            @Override public BusinessPublicProfile salvar(BusinessPublicProfile p) { return p; }
            @Override public Optional<BusinessPublicProfile> buscarPorBusinessId(BusinessId b) { return Optional.empty(); }
            @Override public Optional<BusinessPublicProfile> buscarPublicadoPorSlug(BusinessSlug s) { return Optional.empty(); }
            @Override public boolean slugDisponivel(BusinessSlug s) { return true; }
        };
    }

    private static JpaServiceRepository servicoJpa() {
        return Mockito.mock(JpaServiceRepository.class);
    }

    private static JpaProfessionalRepository profissionalJpa() {
        return Mockito.mock(JpaProfessionalRepository.class);
    }

    private static JpaBusinessCalendarRepository expedienteJpa() {
        return Mockito.mock(JpaBusinessCalendarRepository.class);
    }

    private static JpaAvailabilityRepository disponibilidadeJpa() {
        return Mockito.mock(JpaAvailabilityRepository.class);
    }

    private static JpaBusinessRepository negocioJpa() {
        return Mockito.mock(JpaBusinessRepository.class);
    }

    private static JpaBusinessPublicProfileRepository perfilPublicoJpa() {
        return Mockito.mock(JpaBusinessPublicProfileRepository.class);
    }

    @Test
    @DisplayName("recusa subir quando o catálogo não é o adapter JPA")
    void recusaRepositorioVolatilDeServico() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                repositorioVolatilDeServico(), profissionalJpa(), expedienteJpa(), disponibilidadeJpa(),
                negocioJpa(), perfilPublicoJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ServiceRepository")
                .hasMessageContaining("Catálogo sem persistência");
    }

    @Test
    @DisplayName("recusa subir quando os profissionais não são o adapter JPA")
    void recusaRepositorioVolatilDeProfissional() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                servicoJpa(), repositorioVolatilDeProfissional(), expedienteJpa(), disponibilidadeJpa(),
                negocioJpa(), perfilPublicoJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ProfessionalRepository");
    }

    @Test
    @DisplayName("recusa subir quando o CALENDÁRIO não é o adapter JPA")
    void recusaExpedienteVolatil() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                servicoJpa(), profissionalJpa(), repositorioVolatilDeExpediente(), disponibilidadeJpa(),
                negocioJpa(), perfilPublicoJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BusinessCalendarRepository");
    }

    @Test
    @DisplayName("recusa subir quando a DISPONIBILIDADE não é o adapter JPA")
    void recusaDisponibilidadeVolatil() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                servicoJpa(), profissionalJpa(), expedienteJpa(), repositorioVolatilDeDisponibilidade(),
                negocioJpa(), perfilPublicoJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AvailabilityRepository");
    }

    @Test
    @DisplayName("recusa subir quando o NEGÓCIO não é o adapter JPA")
    void recusaNegocioVolatil() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                servicoJpa(), profissionalJpa(), expedienteJpa(), disponibilidadeJpa(),
                repositorioVolatilDeNegocio(), perfilPublicoJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BusinessRepository");
    }

    @Test
    @DisplayName("recusa subir quando o PERFIL PÚBLICO não é o adapter JPA")
    void recusaPerfilPublicoVolatil() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                servicoJpa(), profissionalJpa(), expedienteJpa(), disponibilidadeJpa(),
                negocioJpa(), repositorioVolatilDePerfilPublico()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BusinessPublicProfileRepository");
    }

    @Test
    @DisplayName("a mensagem explica a consequência, não só o sintoma")
    void mensagemExplicaConsequencia() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                repositorioVolatilDeServico(), profissionalJpa(), expedienteJpa(), disponibilidadeJpa(),
                negocioJpa(), perfilPublicoJpa()))
                .hasMessageContaining("perderia serviços e habilitações a cada restart");
    }

    @Test
    @DisplayName("aceita quando todos são os adapters JPA")
    void aceitaAdaptersJpa() {
        assertThatCode(() -> new CatalogoPersistenceGuard(
                servicoJpa(), profissionalJpa(), expedienteJpa(), disponibilidadeJpa(),
                negocioJpa(), perfilPublicoJpa()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("repositório nulo também é recusado, com mensagem clara")
    void recusaRepositorioAusente() {
        assertThatThrownBy(() -> new CatalogoPersistenceGuard(
                null, profissionalJpa(), expedienteJpa(), disponibilidadeJpa(),
                negocioJpa(), perfilPublicoJpa()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nenhum bean");
    }

    @Test
    @DisplayName("os duplos em memória estão restritos a perfis explícitos")
    void duplosEmMemoriaTemPerfilRestrito() {
        // Impede a regressão de alguém remover o @Profile e reabrir a porta para o
        // catálogo, o calendário, a identidade do negócio ou o perfil público voláteis
        // entrarem em produção pela injeção.
        var perfilServico = com.troquim_bot.repository.InMemoryServiceRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);
        var perfilProfissional = com.troquim_bot.repository.InMemoryProfessionalRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);
        var perfilExpediente = com.troquim_bot.repository.InMemoryBusinessCalendarRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);
        var perfilDisponibilidade = com.troquim_bot.repository.InMemoryAvailabilityRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);
        var perfilNegocio = com.troquim_bot.repository.InMemoryBusinessRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);
        var perfilPerfilPublico = com.troquim_bot.repository.InMemoryBusinessPublicProfileRepository.class
                .getAnnotation(org.springframework.context.annotation.Profile.class);

        assertThat(perfilServico).isNotNull();
        assertThat(perfilServico.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
        assertThat(perfilProfissional).isNotNull();
        assertThat(perfilProfissional.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
        assertThat(perfilExpediente).isNotNull();
        assertThat(perfilExpediente.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
        assertThat(perfilDisponibilidade).isNotNull();
        assertThat(perfilDisponibilidade.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
        assertThat(perfilNegocio).isNotNull();
        assertThat(perfilNegocio.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
        assertThat(perfilPerfilPublico).isNotNull();
        assertThat(perfilPerfilPublico.value()).containsExactlyInAnyOrder("test", "dev-inmemory");
    }
}
