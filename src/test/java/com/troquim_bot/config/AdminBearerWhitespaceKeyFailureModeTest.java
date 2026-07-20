package com.troquim_bot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documenta uma CLASSE POSSÍVEL de falha de configuração que a validação fail-fast
 * do profile azure passa a evitar — NÃO a causa do incidente da chave rotacionada.
 *
 * IMPORTANTE (evidência operacional): no incidente real os SHA-256 da chave no .env,
 * no container e no curl eram IDÊNTICOS e não havia override de propriedade, então
 * whitespace na chave configurada NÃO foi a causa daquele 401. Aquele 401 vinha de
 * {@code GET /customers/} (barra final) não ter mapping em Spring 6+/7 → encaminhamento
 * interno para {@code /error}, que cai no {@code anyRequest().denyAll()} — ver
 * {@link AdminBearerReachesControllerTest}.
 *
 * Ainda assim, este teste é útil como guarda: prova, pela SecurityFilterChain REAL
 * (sem mockar filtro, chain, SecurityContextHolder ou AuthenticationManager), que SE a
 * chave configurada tivesse whitespace ao redor, um Bearer correto seria rejeitado com
 * 401 silencioso — porque o {@link BearerTokenFilter} compara o token de entrada
 * (trimado) contra a chave configurada sem normalizar. O guard do azure
 * ({@link AdminApiKeyConfig}, cenário 9 de {@link AdminBearerAuthenticationTest})
 * transforma essa configuração malformada em falha CLARA de startup, sem trim silencioso.
 *
 * Roda no profile {@code test} (onde não há o fail-fast do azure) e injeta o valor
 * EXATO com {@code @DynamicPropertySource} (que não trima), exercitando o modo de falha
 * pela cadeia real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Modo de falha de config: chave com whitespace rejeita Bearer correto (guard, nao o incidente)")
class AdminBearerWhitespaceKeyFailureModeTest {

    /** Segredo pretendido pelo operador. Uma config malformada acresce whitespace. */
    private static final String INTENDED_SECRET = "chave-admin-para-o-teste";

    @DynamicPropertySource
    static void chaveComWhitespace(DynamicPropertyRegistry registry) {
        // Valor EXATO de uma config malformada (newline final). Supplier não trima.
        registry.add("troquim.admin.api-key", () -> INTENDED_SECRET + "\n");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Se a chave configurada tivesse whitespace, o Bearer correto viraria 401 (modo de falha evitado pelo guard azure)")
    void chaveComWhitespaceRejeitaBearerCorreto() throws Exception {
        // O cliente envia o segredo pretendido, sem whitespace (o que ele conhece).
        mockMvc.perform(get("/customers")
                .header("Authorization", "Bearer " + INTENDED_SECRET))
            .andExpect(status().isUnauthorized());
    }
}
