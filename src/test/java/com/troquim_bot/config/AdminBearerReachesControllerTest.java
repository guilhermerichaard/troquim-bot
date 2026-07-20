package com.troquim_bot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirma, pela SecurityFilterChain REAL, o diagnóstico do incidente da chave
 * rotacionada: a autenticação Bearer FUNCIONA e a requisição com token ALCANÇA o
 * Controller. Espelha a evidência operacional (curl):
 *
 *   GET /customers/not-a-valid-uuid  SEM token  → 401 (default-deny)
 *   GET /customers/not-a-valid-uuid  COM token   → 400 (autenticou, chegou ao
 *                                                  Controller, UUID inválido → 400)
 *   GET /customers                   COM token   → 200 (autenticou, listou)
 *
 * O 400 (não 401/403) na rota /{id} prova que o BearerTokenFilter criou um
 * Authentication ROLE_ADMIN, a autorização (hasRole ADMIN) passou e o
 * CustomerController.buscarPorId executou {@code UUID.fromString(...)} — que lança
 * IllegalArgumentException e o handler traduz para 400. Nada disso ocorreria se a
 * autenticação tivesse falhado.
 *
 * Sobre {@code GET /customers/} (barra final): NÃO existe mapping para ela
 * (CustomerController expõe {@code GET /customers} e {@code GET /customers/{id}}), e o
 * Spring 6+/7 não faz mais trailing-slash match. Sem handler → encaminhamento interno
 * para {@code /error}, que não está na allowlist e cai em {@code anyRequest().denyAll()}.
 * No dispatch de ERROR o {@link BearerTokenFilter} (OncePerRequestFilter, que por padrão
 * não filtra ERROR dispatch) NÃO roda, então a requisição chega anônima e o
 * AuthenticationEntryPoint responde 401 — daí ser 401 e não 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Autenticacao funciona e alcanca o Controller (diagnostico do incidente)")
class AdminBearerReachesControllerTest {

    private static final String AUTHORIZATION = "Authorization";
    private static final String VALID_TOKEN = "test-admin-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /customers/{invalido} SEM token → 401 (default-deny, nao chega ao Controller)")
    void rotaComIdSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/customers/not-a-valid-uuid"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /customers/{invalido} COM token → 400 (autenticou e chegou ao Controller)")
    void rotaComIdComTokenRetorna400() throws Exception {
        mockMvc.perform(get("/customers/not-a-valid-uuid")
                .header(AUTHORIZATION, "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /customers COM token → 200 (autenticou e listou)")
    void listagemComTokenRetorna200() throws Exception {
        mockMvc.perform(get("/customers")
                .header(AUTHORIZATION, "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk());
    }
}
