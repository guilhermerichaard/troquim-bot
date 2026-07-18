package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.TroquimBotApplication;
import com.troquim_bot.application.customer.CustomerApplicationService;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova de persistência real em PostgreSQL (Testcontainers), SEM H2.
 *
 * Um Customer criado pela camada de aplicação (o mesmo caso de uso de
 * POST /customers) em um contexto Spring deve sobreviver ao encerramento desse
 * contexto e reaparecer, pelo caminho de GET /customers (listarTodos), em um
 * SEGUNDO contexto apontando para o MESMO container PostgreSQL.
 *
 * Usa o profile "azure" (PostgreSQL) — nunca H2. Desabilita-se automaticamente
 * quando não há Docker (disabledWithoutDocker), para não quebrar CI sem Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class CustomerPostgresPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("troquim")
                    .withUsername("troquim")
                    .withPassword("troquim-test");

    /** Inicia um contexto Spring completo (sem camada web) apontando para o container. */
    private ConfigurableApplicationContext startContext() {
        return new SpringApplicationBuilder(TroquimBotApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("azure")
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        // azure exige TROQUIM_PILOT_BUSINESS_ID (sem default); fornecido aqui como
                        // a variável que o profile referencia (resolve o tenant e o placeholder Flyway).
                        "TROQUIM_PILOT_BUSINESS_ID=11111111-1111-1111-1111-111111111111",
                        // azure também exige a chave administrativa sem default.
                        "TROQUIM_ADMIN_API_KEY=test-admin-key-for-azure")
                .run();
    }

    @Test
    void customerPersisteEntreDoisContextosNoMesmoPostgres() {
        String phone = "5511900000001";           // fictício
        String nome = "Cliente Testcontainers";    // fictício

        // Contexto A: cria o Customer (caso de uso de POST /customers) e encerra.
        try (ConfigurableApplicationContext ctxA = startContext()) {
            CustomerApplicationService svcA = ctxA.getBean(CustomerApplicationService.class);
            svcA.criarCliente(TestTenants.PILOT, nome, phone, null);
            assertEquals(1, svcA.listarTodos(TestTenants.PILOT).size(),
                    "O container começa vazio; após criar, deve haver exatamente 1 Customer no contexto A");
        }

        // Contexto B: novo contexto, MESMO banco → o Customer e seu BusinessId devem permanecer.
        try (ConfigurableApplicationContext ctxB = startContext()) {
            CustomerApplicationService svcB = ctxB.getBean(CustomerApplicationService.class);
            List<Customer> todos = svcB.listarTodos(TestTenants.PILOT);
            assertEquals(1, todos.size(),
                    "Após reiniciar o contexto contra o mesmo PostgreSQL, o Customer deve persistir");
            Customer preservado = todos.get(0);
            assertTrue(nome.equals(preservado.getName().getFullName()),
                    "O Customer criado no contexto A deve reaparecer no contexto B (mesmo nome)");
            assertEquals(TestTenants.PILOT, preservado.getBusinessId(),
                    "O BusinessId deve ser preservado entre reinícios");
        }
    }
}
