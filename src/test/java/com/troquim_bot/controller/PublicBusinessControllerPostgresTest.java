package com.troquim_bot.controller;

import com.troquim_bot.application.business.ConfigurarPerfilPublico;
import com.troquim_bot.application.business.PublicarPerfilPublico;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.support.CatalogoDeTeste;
import com.troquim_bot.support.TestTenants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo, ponta a ponta, em PostgreSQL real: perfil publicado → catálogo persistido
 * → disponibilidade persistida → resposta HTTP pública. Migrations V1–V14 aplicadas de
 * verdade — nenhum H2 envolvido.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("azure")
@DisplayName("API pública - fluxo completo em PostgreSQL real")
class PublicBusinessControllerPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("TROQUIM_PILOT_BUSINESS_ID", () -> TestTenants.PILOT.getValue().toString());
        registry.add("TROQUIM_ADMIN_API_KEY", () -> "test-admin-key-for-azure");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ProvisionarNegocio provisionarNegocio;
    @Autowired
    private ConfigurarPerfilPublico configurarPerfilPublico;
    @Autowired
    private PublicarPerfilPublico publicarPerfilPublico;
    @Autowired
    private ConsultarCatalogo consultarCatalogo;

    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    @Test
    @DisplayName("perfil publicado + catálogo persistido + disponibilidade persistida → resposta HTTP correta")
    void fluxoCompletoContraPostgresReal() throws Exception {
        BusinessId id = BusinessId.from(UUID.randomUUID());
        String slug = "gizelle-lash-" + UUID.randomUUID().toString().substring(0, 6);

        businessRepository.save(new Business(id, "Gizelle Lash Designer", null, null));
        CatalogoDeTeste.provisionar(provisionarNegocio, businessRepository, id);
        configurarPerfilPublico.configurar(id, slug, "Gizelle Lash Designer",
                "Design de cílios", "+5511999990000", "Rua das Flores, 10");
        publicarPerfilPublico.publicar(id);

        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);

        // 1. Vitrine pública: catálogo real, persistido em Postgres.
        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.name").value("Gizelle Lash Designer"))
                .andExpect(jsonPath("$.catalogConfigured").value(true))
                .andExpect(jsonPath("$.services[?(@.id=='" + item.id().getValue() + "')]").exists());

        // 2. Disponibilidade pública: expediente e disponibilidade persistidos em Postgres.
        LocalDate quarta = proximaQuarta();
        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slug)
                        .param("serviceId", item.id().getValue().toString())
                        .param("professionalId", item.profissionais().get(0).id().getValue().toString())
                        .param("date", quarta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condition").value("AVAILABLE"))
                .andExpect(jsonPath("$.times").isArray())
                .andExpect(jsonPath("$.times[0]").value("09:00"));

        // 3. Sem credencial nenhuma — a API pública não exige Authorization.
        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isOk());
    }
}
