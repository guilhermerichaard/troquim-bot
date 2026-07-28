package com.troquim_bot.whatsapp.channel;

import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.whatsapp.channel.application.ChannelConnection;
import com.troquim_bot.whatsapp.channel.application.ChannelConnectionStatus;
import com.troquim_bot.whatsapp.channel.application.ChannelConnectionStore;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService.ConexaoInvalidaException;
import com.troquim_bot.whatsapp.channel.support.FakeChannelCredentialCipher;
import com.troquim_bot.whatsapp.channel.support.FakeMetaOAuthGateway;
import com.troquim_bot.whatsapp.channel.support.InMemoryChannelConnectionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolamento entre tenants no vínculo de canal.
 *
 * A conexão carrega a credencial de uma conta WhatsApp Business inteira: se o tenant A
 * conseguisse ler ou concluir a conexão do tenant B, ele passaria a mandar mensagem em
 * nome de outro negócio. É a falha mais cara possível neste módulo, então cada caminho
 * (consulta, finalização, reinício) é prendido separadamente.
 */
@DisplayName("Conexao de canal - isolamento entre tenants")
class ChannelConnectionTenantIsolationTest {

    private InMemoryChannelConnectionStore store;
    private FakeMetaOAuthGateway gateway;

    @BeforeEach
    void setUp() {
        store = new InMemoryChannelConnectionStore();
        gateway = new FakeMetaOAuthGateway("token-da-meta");
    }

    private ConectarWhatsAppChannelService servicoDe(TenantProvider tenant) {
        return new ConectarWhatsAppChannelService(
                store, new FakeChannelCredentialCipher(), gateway, tenant);
    }

    @Test
    @DisplayName("tenant A nao enxerga a conexao do tenant B")
    void consultaNaoAtravessaTenant() {
        servicoDe(TestTenants.of(TestTenants.OUTRO)).iniciar();

        Optional<ChannelConnection> vistaPeloPiloto =
                servicoDe(TestTenants.pilot()).consultar();

        assertTrue(vistaPeloPiloto.isEmpty(),
                "A conexao aberta pelo tenant OUTRO nao pode aparecer para o PILOT");
    }

    @Test
    @DisplayName("tenant A nao finaliza a conexao iniciada pelo tenant B")
    void finalizacaoNaoAtravessaTenant() {
        // B inicia e obtem um state legitimo.
        var inicioDeB = servicoDe(TestTenants.of(TestTenants.OUTRO)).iniciar();

        // A tenta usar o state de B — mesmo com um code que a Meta aceitaria.
        assertThrows(ConexaoInvalidaException.class,
                () -> servicoDe(TestTenants.pilot())
                        .finalizar(inicioDeB.state(), "code-valido", "waba-x", "phone-x"),
                "O state de outro tenant deve ser recusado");

        assertEquals(0, gateway.chamadas(),
                "A Meta nem pode ser chamada: o state e' validado antes da troca");

        // E a conexao de B continua intacta, aguardando a finalizacao legitima.
        ChannelConnection deB = store.buscarPorTenant(TestTenants.OUTRO.getValue()).orElseThrow();
        assertEquals(ChannelConnectionStatus.PENDENTE, deB.status());
        assertTrue(deB.stateToken().isPresent(), "A tentativa alheia nao pode consumir o nonce");
    }

    @Test
    @DisplayName("cada tenant tem a sua conexao, sem colisao")
    void cadaTenantTemASuaConexao() {
        var inicioPiloto = servicoDe(TestTenants.pilot()).iniciar();
        var inicioOutro = servicoDe(TestTenants.of(TestTenants.OUTRO)).iniciar();

        assertFalse(inicioPiloto.state().equals(inicioOutro.state()),
                "Cada inicio emite um nonce distinto");

        ChannelConnection doPiloto = store.buscarPorTenant(TestTenants.PILOT.getValue()).orElseThrow();
        ChannelConnection doOutro = store.buscarPorTenant(TestTenants.OUTRO.getValue()).orElseThrow();

        assertFalse(doPiloto.id().equals(doOutro.id()), "Sao linhas diferentes");
        assertTrue(doPiloto.pertenceAoTenant(TestTenants.PILOT.getValue()));
        assertFalse(doPiloto.pertenceAoTenant(TestTenants.OUTRO.getValue()));
    }

    @Test
    @DisplayName("a credencial conectada do tenant B nao vaza para o tenant A")
    void credencialNaoAtravessaTenant() {
        var inicio = servicoDe(TestTenants.of(TestTenants.OUTRO)).iniciar();
        servicoDe(TestTenants.of(TestTenants.OUTRO))
                .finalizar(inicio.state(), "code-valido", "waba-b", "phone-b");

        assertTrue(servicoDe(TestTenants.pilot()).consultar().isEmpty(),
                "Conexao CONECTADA de outro tenant continua invisivel");

        ChannelConnection deB = store.buscarPorTenant(TestTenants.OUTRO.getValue()).orElseThrow();
        assertEquals(ChannelConnectionStatus.CONECTADO, deB.status());
        assertTrue(deB.credencial().isPresent());
    }
}
