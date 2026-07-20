package com.troquim_bot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integração da autenticação administrativa Bearer pela SecurityFilterChain
 * REAL — nada é mockado (nem BearerTokenFilter, nem a chain, nem SecurityContextHolder,
 * nem AuthenticationManager). Separa autenticação das regras de domínio via um endpoint
 * funcional test-only, {@code GET /customers/__authprobe__}, coberto pela mesma regra
 * {@code /customers/** → hasRole("ADMIN")}. O endpoint é exato, então vence
 * {@code /customers/{id}} por precedência e não toca o domínio.
 *
 * Cobre os cenários mínimos do contrato:
 *   1. health público sem token → 200
 *   2. webhook público sem token → não 401/403
 *   3. protegido sem token → 401
 *   4. protegido com token incorreto → 401
 *   5. protegido com token correto → não 401/403
 *   6. token correto cria Authentication autenticado com autoridade compatível (ROLE_ADMIN)
 *   7. prefixo "Bearer" case-insensitive (contrato existente: regionMatches ignoreCase)
 *   8. token vazio ou só com whitespace é rejeitado → 401
 *   9. azure sem chave válida (ausente/placeholder/whitespace) continua fail-fast no startup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Autenticacao administrativa Bearer - SecurityFilterChain real")
class AdminBearerAuthenticationTest {

    private static final String AUTHORIZATION = "Authorization";
    private static final String VALID_TOKEN = "test-admin-key";      // = application-test.properties
    private static final String PROBE = "/customers/__authprobe__";  // protegido por /customers/** (ADMIN)

    @Autowired
    private MockMvc mockMvc;

    // ==================== 1 — health público ====================

    @Test
    @DisplayName("1. health e' publico sem token → 200")
    void healthPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    // ==================== 2 — webhook público ====================

    @Test
    @DisplayName("2. webhook publico sem token → nao 401/403")
    void webhookPublico() throws Exception {
        mockMvc.perform(post("/webhook/whatsapp")
                .contentType(MediaType.APPLICATION_JSON).content("{\"test\":true}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/webhook/whatsapp/messages-upsert")
                .contentType(MediaType.APPLICATION_JSON).content("{\"test\":true}"))
            .andExpect(status().isOk());
    }

    // ==================== 3 — protegido sem token ====================

    @Test
    @DisplayName("3. endpoint protegido sem token → 401")
    void protegidoSemToken() throws Exception {
        mockMvc.perform(get(PROBE))
            .andExpect(status().isUnauthorized());
    }

    // ==================== 4 — token incorreto ====================

    @Test
    @DisplayName("4. endpoint protegido com token incorreto → 401")
    void protegidoTokenIncorreto() throws Exception {
        mockMvc.perform(get(PROBE).header(AUTHORIZATION, "Bearer token-incorreto"))
            .andExpect(status().isUnauthorized());
    }

    // ==================== 5 — token correto ====================

    @Test
    @DisplayName("5. endpoint protegido com token correto → nao 401/403 (200)")
    void protegidoTokenCorreto() throws Exception {
        mockMvc.perform(get(PROBE).header(AUTHORIZATION, "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk());
    }

    // ==================== 6 — Authentication + authorities ====================

    @Test
    @DisplayName("6. token correto cria Authentication autenticado com ROLE_ADMIN (compativel com hasRole)")
    void tokenCorretoCriaAuthenticationComRoleAdmin() throws Exception {
        mockMvc.perform(get(PROBE).header(AUTHORIZATION, "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.name").value("admin"))
            .andExpect(jsonPath("$.authorities", hasItem("ROLE_ADMIN")));
    }

    // ==================== 7 — Bearer case-insensitive ====================

    @Test
    @DisplayName("7. prefixo Bearer e' case-insensitive → 200")
    void bearerCaseInsensitive() throws Exception {
        mockMvc.perform(get(PROBE).header(AUTHORIZATION, "bearer " + VALID_TOKEN))
            .andExpect(status().isOk());
        mockMvc.perform(get(PROBE).header(AUTHORIZATION, "BEARER " + VALID_TOKEN))
            .andExpect(status().isOk());
    }

    // ==================== 8 — token vazio / whitespace ====================

    @Test
    @DisplayName("8. token vazio ou so com whitespace → 401")
    void tokenVazioOuWhitespaceRejeitado() throws Exception {
        mockMvc.perform(get(PROBE).header(AUTHORIZATION, "Bearer "))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROBE).header(AUTHORIZATION, "Bearer      "))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROBE).header(AUTHORIZATION, "Bearer \t"))
            .andExpect(status().isUnauthorized());
    }

    // ==================== 9 — azure fail-fast ====================

    @Test
    @DisplayName("9. azure sem chave valida (ausente/placeholder/whitespace) → fail-fast no startup")
    void azureFailFastSemChaveValida() {
        // ausente (vazia)
        assertAzureStartupFails("", "must be configured");
        // placeholder
        assertAzureStartupFails("change-me", "must be configured");
        // whitespace ao redor: classe possível de config malformada (NÃO foi a causa do
        // incidente da chave rotacionada — SHA-256 idênticos) → falha CLARA, sem trim silencioso
        assertAzureStartupFails("valid-looking-key\n", "whitespace");
        assertAzureStartupFails("  valid-looking-key  ", "whitespace");

        // controle: uma chave limpa NAO deve falhar o startup (fail-fast e' especifico).
        new ApplicationContextRunner()
            .withInitializer(azureWithKey("valid-looking-key"))
            .withUserConfiguration(AdminApiKeyConfig.class)
            .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    private void assertAzureStartupFails(String key, String expectedFragment) {
        new ApplicationContextRunner()
            .withInitializer(azureWithKey(key))
            .withUserConfiguration(AdminApiKeyConfig.class)
            .run(ctx -> assertThat(ctx)
                .hasFailed()
                .getFailure()
                .hasStackTraceContaining(expectedFragment));
    }

    /** Ativa o profile azure e injeta o valor EXATO da chave (sem trim). */
    private static org.springframework.context.ApplicationContextInitializer<
            org.springframework.context.ConfigurableApplicationContext> azureWithKey(String key) {
        return ctx -> {
            ctx.getEnvironment().setActiveProfiles("azure");
            ctx.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("adminKeyUnderTest",
                    Map.of("troquim.admin.api-key", key)));
        };
    }

    // ==================== endpoint funcional test-only ====================

    /**
     * Endpoint funcional (RouterFunction) test-only. Fica no contexto SÓ deste teste
     * (nested @TestConfiguration, fora do component-scan), então não vaza para outros
     * @SpringBootTest. Ecoa o Authentication presente no momento do handler — que só
     * roda se a autorização (hasRole ADMIN) tiver passado, provando que o Authentication
     * autenticado com ROLE_ADMIN chegou à autorização.
     */
    @TestConfiguration
    static class ProbeConfig {
        @Bean
        RouterFunction<ServerResponse> authProbe() {
            return RouterFunctions.route()
                .GET(PROBE, request -> {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    return ServerResponse.ok().body(Map.of(
                        "name", auth.getName(),
                        "authenticated", auth.isAuthenticated(),
                        "authorities", auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority).sorted().toList()));
                })
                .build();
        }
    }
}
