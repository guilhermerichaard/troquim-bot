package com.troquim_bot.whatsapp.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Fronteira HTTP do onboarding.
 *
 * Duas garantias: a rota é administrativa (o Embedded Signup vincula uma conta ao
 * tenant, então não pode ser iniciado por anônimo) e a resposta carrega só dados
 * públicos — o App Secret não tem caminho até o navegador.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "troquim.integrations.whatsapp.embedded-signup.enabled=true",
        "troquim.integrations.whatsapp.embedded-signup.app-id=app-id-de-teste",
        "troquim.integrations.whatsapp.embedded-signup.config-id=973012265764230",
        "troquim.integrations.whatsapp.embedded-signup.app-secret=segredo-que-nunca-pode-vazar",
        "troquim.integrations.whatsapp.embedded-signup.graph-api-version=vtest",
        "troquim.integrations.whatsapp.embedded-signup.base-url=http://localhost:59999",
        "troquim.integrations.whatsapp.channel.crypto.key=Y2hhdmUtZGUtdGVzdGUtY29tLTMyLWJ5dGVzISEhISE="
})
@DisplayName("Endpoint de conexao de canal WhatsApp")
class WhatsAppChannelConnectionEndpointTest {

    private static final String ROTA = "/api/v1/admin/whatsapp/connections";
    private static final String ADMIN = "Bearer test-admin-key";
    private static final String APP_SECRET = "segredo-que-nunca-pode-vazar";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sem credencial admin, o onboarding nao pode ser iniciado")
    void exigeAdmin() throws Exception {
        mockMvc.perform(post(ROTA + "/start"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401
                                || result.getResponse().getStatus() == 403,
                        "Anonimo nao pode iniciar vinculo de canal; veio "
                                + result.getResponse().getStatus()));

        mockMvc.perform(get(ROTA + "/current"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401
                                || result.getResponse().getStatus() == 403));
    }

    @Test
    @DisplayName("start devolve config_id e app_id, nunca o app secret")
    void startNaoVazaSegredo() throws Exception {
        MvcResult resultado = mockMvc.perform(post(ROTA + "/start").header("Authorization", ADMIN))
                .andReturn();

        assertEquals(200, resultado.getResponse().getStatus());
        String corpo = resultado.getResponse().getContentAsString();

        assertTrue(corpo.contains("973012265764230"), "config_id e publico e deve vir");
        assertTrue(corpo.contains("app-id-de-teste"), "app_id e publico e deve vir");
        assertTrue(corpo.contains("PENDENTE"));
        assertFalse(corpo.contains(APP_SECRET), "O App Secret NUNCA pode chegar ao frontend");
        assertFalse(corpo.toLowerCase().contains("secret"),
                "Nem o nome do campo deve aparecer na resposta");
    }

    @Test
    @DisplayName("finish rejeita corpo vazio, sem state ou sem code")
    void finishRejeitaEntradaInvalida() throws Exception {
        mockMvc.perform(post(ROTA + "/finish").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertEquals(400, result.getResponse().getStatus()));

        mockMvc.perform(post(ROTA + "/finish").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"abc\"}"))
                .andExpect(result -> assertEquals(400, result.getResponse().getStatus(),
                        "Sem state nao ha como amarrar a volta ao inicio"));

        mockMvc.perform(post(ROTA + "/finish").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"abc\"}"))
                .andExpect(result -> assertEquals(400, result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("finish com state inventado responde 400 seco, sem detalhe")
    void finishComStateInventado() throws Exception {
        MvcResult resultado = mockMvc.perform(post(ROTA + "/finish").header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"state-que-nunca-existiu\",\"code\":\"code-qualquer\"}"))
                .andReturn();

        assertEquals(400, resultado.getResponse().getStatus());
        String corpo = resultado.getResponse().getContentAsString();
        assertTrue(corpo.isBlank(),
                "Distinguir 'nonce desconhecido' de 'code recusado' ajudaria quem sonda");
    }

    @Test
    @DisplayName("current nao expoe credencial e parte de nao-conectado")
    void currentNaoExpoeCredencial() throws Exception {
        MvcResult resultado = mockMvc.perform(get(ROTA + "/current").header("Authorization", ADMIN))
                .andReturn();

        assertEquals(200, resultado.getResponse().getStatus());
        String corpo = resultado.getResponse().getContentAsString();
        assertFalse(corpo.contains(APP_SECRET));
        assertFalse(corpo.toLowerCase().contains("token"),
                "Nenhum campo de token pode existir nesta resposta");
        assertTrue(corpo.contains("\"conectado\":false"));
    }
}
