package com.troquim_bot.whatsapp.channel;

import com.troquim_bot.support.TestTenants;
import com.troquim_bot.whatsapp.channel.application.ChannelConnection;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService;
import com.troquim_bot.whatsapp.channel.application.EncryptedCredential;
import com.troquim_bot.whatsapp.channel.infrastructure.crypto.AesGcmChannelCredentialCipher;
import com.troquim_bot.whatsapp.channel.infrastructure.crypto.ChannelCryptoProperties;
import com.troquim_bot.whatsapp.channel.support.FakeChannelCredentialCipher;
import com.troquim_bot.whatsapp.channel.support.FakeMetaOAuthGateway;
import com.troquim_bot.whatsapp.channel.support.InMemoryChannelConnectionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O segredo não pode escapar por nenhuma das saídas baratas: {@code toString},
 * log, ou o que fica gravado.
 *
 * Estes testes existem porque vazamento de credencial quase nunca acontece por uma
 * decisão — acontece por um {@code log.info("{}", objeto)} e um record que imprime
 * todos os campos por padrão.
 */
@DisplayName("Credencial do canal - sigilo")
class ChannelCredentialSigiloTest {

    private static final String TOKEN = "EAAsegredo-super-secreto-da-meta-123456";

    private static AesGcmChannelCredentialCipher cipherReal() {
        ChannelCryptoProperties props = new ChannelCryptoProperties();
        // Chave de teste: 32 bytes determinísticos, gerados aqui e não reutilizados
        // em lugar nenhum. Não é segredo de produção.
        props.setKey(Base64.getEncoder().encodeToString("chave-de-teste-com-32-bytes!!!!!".getBytes()));
        props.setKeyVersion(1);
        return new AesGcmChannelCredentialCipher(props);
    }

    @Test
    @DisplayName("toString da conexao nunca revela credencial nem nonce")
    void toStringNaoRevela() {
        InMemoryChannelConnectionStore store = new InMemoryChannelConnectionStore();
        var service = new ConectarWhatsAppChannelService(
                store, new FakeChannelCredentialCipher(),
                new FakeMetaOAuthGateway(TOKEN), TestTenants.pilot());

        var inicio = service.iniciar();
        ChannelConnection pendente = store.buscarPorTenant(TestTenants.PILOT.getValue()).orElseThrow();
        assertFalse(pendente.toString().contains(inicio.state()),
                "O nonce nao pode aparecer no toString");

        service.finalizar(inicio.state(), "code-valido", "waba-1", "phone-1");
        ChannelConnection conectada = store.buscarPorTenant(TestTenants.PILOT.getValue()).orElseThrow();

        String texto = conectada.toString();
        assertFalse(texto.contains(TOKEN), "O token nao pode aparecer no toString");
        assertFalse(texto.contains(conectada.credencial().orElseThrow().cipherTextBase64()),
                "Nem o texto cifrado deve ser impresso");
        assertTrue(texto.contains("<cifrada>"), "Deve indicar a presenca sem revelar o conteudo");
    }

    @Test
    @DisplayName("toString da credencial cifrada nao imprime bytes")
    void toStringDaCredencial() {
        EncryptedCredential credencial = cipherReal().cifrar(TOKEN);

        String texto = credencial.toString();
        assertFalse(texto.contains(credencial.cipherTextBase64()));
        assertFalse(texto.contains(credencial.ivBase64()));
        assertTrue(texto.contains("keyVersion=1"));
    }

    @Test
    @DisplayName("AES-GCM: ida e volta funciona e o cifrado nao contem o claro")
    void idaEVolta() {
        AesGcmChannelCredentialCipher cipher = cipherReal();

        EncryptedCredential credencial = cipher.cifrar(TOKEN);

        assertFalse(credencial.cipherTextBase64().contains(TOKEN));
        assertFalse(new String(Base64.getDecoder().decode(credencial.cipherTextBase64()))
                .contains(TOKEN));
        assertEquals(TOKEN, cipher.decifrar(credencial));
    }

    @Test
    @DisplayName("cada cifragem usa um IV novo — dois cifrados do mesmo token diferem")
    void ivNuncaSeRepete() {
        AesGcmChannelCredentialCipher cipher = cipherReal();

        EncryptedCredential primeira = cipher.cifrar(TOKEN);
        EncryptedCredential segunda = cipher.cifrar(TOKEN);

        assertNotEquals(primeira.ivBase64(), segunda.ivBase64(),
                "Reusar IV em GCM quebra a confidencialidade");
        assertNotEquals(primeira.cipherTextBase64(), segunda.cipherTextBase64());
        assertEquals(TOKEN, cipher.decifrar(primeira));
        assertEquals(TOKEN, cipher.decifrar(segunda));
    }

    @Test
    @DisplayName("GCM detecta adulteracao do texto cifrado")
    void adulteracaoEhDetectada() {
        AesGcmChannelCredentialCipher cipher = cipherReal();
        EncryptedCredential original = cipher.cifrar(TOKEN);

        byte[] bytes = Base64.getDecoder().decode(original.cipherTextBase64());
        bytes[0] ^= 0x01;   // um bit trocado, como um dump editado a mao
        EncryptedCredential adulterada = new EncryptedCredential(
                Base64.getEncoder().encodeToString(bytes), original.ivBase64(), original.keyVersion());

        assertThrows(IllegalStateException.class, () -> cipher.decifrar(adulterada),
                "GCM precisa falhar em vez de devolver lixo silenciosamente");
    }

    @Test
    @DisplayName("credencial de outra versao de chave nao e' decifrada as cegas")
    void versaoDeChaveIncompativel() {
        AesGcmChannelCredentialCipher cipher = cipherReal();
        EncryptedCredential original = cipher.cifrar(TOKEN);

        EncryptedCredential deOutraVersao = new EncryptedCredential(
                original.cipherTextBase64(), original.ivBase64(), 99);

        assertThrows(IllegalStateException.class, () -> cipher.decifrar(deOutraVersao));
    }

    @Test
    @DisplayName("chave ausente ou de tamanho errado falha explicitamente")
    void chaveInvalidaFalhaRapido() {
        ChannelCryptoProperties semChave = new ChannelCryptoProperties();
        assertThrows(IllegalStateException.class, semChave::chaveDecodificada);

        ChannelCryptoProperties curta = new ChannelCryptoProperties();
        curta.setKey(Base64.getEncoder().encodeToString("curta-demais".getBytes()));
        assertThrows(IllegalStateException.class, curta::chaveDecodificada,
                "Uma chave de 16 bytes aceita em silencio faria pensar que e' AES-256");
    }
}
