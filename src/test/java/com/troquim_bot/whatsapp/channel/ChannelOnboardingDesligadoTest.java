package com.troquim_bot.whatsapp.channel;

import com.troquim_bot.whatsapp.channel.application.ChannelCredentialCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Instância SEM o onboarding configurado — o estado normal de produção hoje.
 *
 * Regressão que este teste prende: a chave de cifragem chegou a ser validada no
 * construtor do cipher, e isso derrubava o contexto inteiro de qualquer instalação que
 * não usasse a feature. Uma integração opcional e desligada não pode impedir a
 * aplicação de subir.
 *
 * O outro lado da moeda também é prendido: quem tentar cifrar sem chave recebe um erro
 * que diz qual variável definir, em vez de gravar credencial fraca em silêncio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Onboarding desligado - aplicacao sobe normalmente")
class ChannelOnboardingDesligadoTest {

    private static final String ROTA = "/api/v1/admin/whatsapp/connections";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChannelCredentialCipher cipher;

    @Test
    @DisplayName("o contexto sobe sem TROQUIM_CHANNEL_CRYPTO_KEY configurada")
    void contextoSobeSemChave() {
        assertNotNull(cipher, "O bean existe mesmo sem chave: a chave e' resolvida no uso");
    }

    @Test
    @DisplayName("com a feature desligada, os endpoints respondem 503")
    void endpointsIndisponiveis() throws Exception {
        mockMvc.perform(post(ROTA + "/start").header("Authorization", "Bearer test-admin-key"))
                .andExpect(result -> assertEquals(503, result.getResponse().getStatus(),
                        "Desligado deve ser indisponivel, nao erro interno"));
    }

    @Test
    @DisplayName("cifrar sem chave falha dizendo qual variavel definir")
    void cifrarSemChaveFalhaComMensagemUtil() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> cipher.cifrar("um-token-qualquer"));

        assertTrue(erro.getMessage().contains("TROQUIM_CHANNEL_CRYPTO_KEY"),
                "A mensagem precisa dizer o que configurar; veio: " + erro.getMessage());
    }
}
