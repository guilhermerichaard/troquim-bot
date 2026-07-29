package com.troquim_bot.whatsapp.channel;

import com.troquim_bot.support.TestTenants;
import com.troquim_bot.whatsapp.channel.application.ChannelConnection;
import com.troquim_bot.whatsapp.channel.application.ChannelConnectionStatus;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService.ConexaoInvalidaException;
import com.troquim_bot.whatsapp.channel.support.FakeChannelCredentialCipher;
import com.troquim_bot.whatsapp.channel.support.FakeMetaOAuthGateway;
import com.troquim_bot.whatsapp.channel.support.InMemoryChannelConnectionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fluxo de conexão: o que é aceito, o que é recusado e o que nunca é escrito.
 *
 * O foco é o nonce. Ele é a única amarração entre o diálogo que a Meta conduziu no
 * navegador e o tenant que pediu a conexão — se ele for aceito fora de hora, reusado
 * ou dispensado, um code avulso vira credencial gravada.
 */
@DisplayName("ConectarWhatsAppChannelService - aceitacao e recusa")
class ConectarWhatsAppChannelServiceTest {

    private InMemoryChannelConnectionStore store;
    private FakeMetaOAuthGateway gateway;
    private ConectarWhatsAppChannelService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryChannelConnectionStore();
        gateway = new FakeMetaOAuthGateway("token-da-meta");
        service = new ConectarWhatsAppChannelService(
                store, new FakeChannelCredentialCipher(), gateway);
    }

    @Test
    @DisplayName("iniciar registra PENDENTE e emite um nonce; nada e' conectado")
    void iniciarNaoConectaNada() {
        var resultado = service.iniciar(TestTenants.PILOT.getValue(), java.util.Optional.empty());

        assertEquals(ChannelConnectionStatus.PENDENTE, resultado.status());
        assertFalse(resultado.state().isBlank());

        ChannelConnection conexao = store.buscarPorTenant(TestTenants.PILOT.getValue()).orElseThrow();
        assertEquals(ChannelConnectionStatus.PENDENTE, conexao.status());
        assertTrue(conexao.credencial().isEmpty(), "Iniciar nao pode gravar credencial");
        assertEquals(0, gateway.chamadas(), "Iniciar nao fala com a Meta");
    }

    @Test
    @DisplayName("fluxo completo: finalizar cifra a credencial e marca CONECTADO")
    void fluxoCompleto() {
        var inicio = service.iniciar(TestTenants.PILOT.getValue(), java.util.Optional.empty());

        var fim = service.finalizar(TestTenants.PILOT.getValue(), java.util.Optional.empty(), inicio.state(), "code-valido", "waba-1", "phone-1");

        assertEquals(ChannelConnectionStatus.CONECTADO, fim.status());
        assertEquals("waba-1", fim.wabaId().orElseThrow());

        ChannelConnection conexao = store.buscarPorTenant(TestTenants.PILOT.getValue()).orElseThrow();
        assertTrue(conexao.credencial().isPresent(), "A credencial precisa estar gravada");
        assertFalse(conexao.credencial().orElseThrow().cipherTextBase64().contains("token-da-meta"),
                "A credencial nao pode estar em claro no que foi persistido");
        assertTrue(conexao.stateToken().isEmpty(), "O nonce e' consumido na finalizacao");
    }

    @Test
    @DisplayName("state desconhecido e' recusado sem chamar a Meta")
    void stateDesconhecido() {
        service.iniciar(TestTenants.PILOT.getValue(), java.util.Optional.empty());

        assertThrows(ConexaoInvalidaException.class,
                () -> service.finalizar(TestTenants.PILOT.getValue(), java.util.Optional.empty(), "state-inventado", "code-valido", null, null));
        assertEquals(0, gateway.chamadas());
    }

    @Test
    @DisplayName("o mesmo nonce nao serve duas vezes (replay)")
    void nonceNaoServeDuasVezes() {
        var inicio = service.iniciar(TestTenants.PILOT.getValue(), java.util.Optional.empty());
        service.finalizar(TestTenants.PILOT.getValue(), java.util.Optional.empty(), inicio.state(), "code-valido", "waba-1", "phone-1");

        assertThrows(ConexaoInvalidaException.class,
                () -> service.finalizar(TestTenants.PILOT.getValue(), java.util.Optional.empty(), inicio.state(), "code-valido", "waba-1", "phone-1"),
                "Reapresentar o nonce ja consumido deve falhar");
    }

    @Test
    @DisplayName("code recusado pela Meta marca FALHOU e nao grava credencial")
    void codeRecusado() {
        ConectarWhatsAppChannelService comRecusa = new ConectarWhatsAppChannelService(
                store, new FakeChannelCredentialCipher(),
                FakeMetaOAuthGateway.queRecusa());

        var inicio = comRecusa.iniciar(TestTenants.PILOT.getValue(), java.util.Optional.empty());

        assertThrows(ConexaoInvalidaException.class,
                () -> comRecusa.finalizar(TestTenants.PILOT.getValue(), java.util.Optional.empty(), inicio.state(), "code-ruim", null, null));

        ChannelConnection conexao = store.buscarPorTenant(TestTenants.PILOT.getValue()).orElseThrow();
        assertEquals(ChannelConnectionStatus.FALHOU, conexao.status());
        assertTrue(conexao.credencial().isEmpty(), "Falha nao pode deixar credencial parcial");
        assertTrue(conexao.stateToken().isEmpty(),
                "O nonce tambem e' descartado: repetir exige um novo inicio");
    }

    @Test
    @DisplayName("reiniciar reaproveita a linha do tenant e invalida o nonce anterior")
    void reiniciarInvalidaNonceAnterior() {
        var primeiro = service.iniciar(TestTenants.PILOT.getValue(), java.util.Optional.empty());
        var segundo = service.iniciar(TestTenants.PILOT.getValue(), java.util.Optional.empty());

        assertEquals(1, store.total(), "Um tenant tem no maximo uma conexao");
        assertThrows(ConexaoInvalidaException.class,
                () -> service.finalizar(TestTenants.PILOT.getValue(), java.util.Optional.empty(), primeiro.state(), "code-valido", null, null),
                "O nonce antigo deixa de valer assim que outro e' emitido");

        var fim = service.finalizar(TestTenants.PILOT.getValue(), java.util.Optional.empty(), segundo.state(), "code-valido", null, null);
        assertEquals(ChannelConnectionStatus.CONECTADO, fim.status());
    }
}
