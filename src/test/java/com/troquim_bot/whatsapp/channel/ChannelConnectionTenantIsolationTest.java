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
 *
 * O serviço não resolve tenant sozinho (ver ConectarWhatsAppChannelService): cada
 * chamada aqui prova explicitamente de qual negócio está falando, exatamente como um
 * controller real (admin ou dono autenticado) faria.
 */
@DisplayName("Conexao de canal - isolamento entre tenants")
class ChannelConnectionTenantIsolationTest {

    private InMemoryChannelConnectionStore store;
    private FakeMetaOAuthGateway gateway;
    private ConectarWhatsAppChannelService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryChannelConnectionStore();
        gateway = new FakeMetaOAuthGateway("token-da-meta");
        service = new ConectarWhatsAppChannelService(store, new FakeChannelCredentialCipher(), gateway);
    }

    @Test
    @DisplayName("tenant A nao enxerga a conexao do tenant B")
    void consultaNaoAtravessaTenant() {
        service.iniciar(TestTenants.OUTRO.getValue(), Optional.empty());

        Optional<ChannelConnection> vistaPeloPiloto = service.consultar(TestTenants.PILOT.getValue());

        assertTrue(vistaPeloPiloto.isEmpty(),
                "A conexao aberta pelo tenant OUTRO nao pode aparecer para o PILOT");
    }

    @Test
    @DisplayName("tenant A nao finaliza a conexao iniciada pelo tenant B")
    void finalizacaoNaoAtravessaTenant() {
        // B inicia e obtem um state legitimo.
        var inicioDeB = service.iniciar(TestTenants.OUTRO.getValue(), Optional.empty());

        // A tenta usar o state de B — mesmo com um code que a Meta aceitaria.
        assertThrows(ConexaoInvalidaException.class,
                () -> service.finalizar(TestTenants.PILOT.getValue(), Optional.empty(),
                        inicioDeB.state(), "code-valido", "waba-x", "phone-x"),
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
        var inicioPiloto = service.iniciar(TestTenants.PILOT.getValue(), Optional.empty());
        var inicioOutro = service.iniciar(TestTenants.OUTRO.getValue(), Optional.empty());

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
        var inicio = service.iniciar(TestTenants.OUTRO.getValue(), Optional.empty());
        service.finalizar(TestTenants.OUTRO.getValue(), Optional.empty(),
                inicio.state(), "code-valido", "waba-b", "phone-b");

        assertTrue(service.consultar(TestTenants.PILOT.getValue()).isEmpty(),
                "Conexao CONECTADA de outro tenant continua invisivel");

        ChannelConnection deB = store.buscarPorTenant(TestTenants.OUTRO.getValue()).orElseThrow();
        assertEquals(ChannelConnectionStatus.CONECTADO, deB.status());
        assertTrue(deB.credencial().isPresent());
    }

    @Test
    @DisplayName("state de um dono nao e' aceito por outro dono, mesmo no mesmo tenant")
    void stateNaoAtravessaDono() {
        java.util.UUID donoA = java.util.UUID.randomUUID();
        java.util.UUID donoB = java.util.UUID.randomUUID();

        var inicio = service.iniciar(TestTenants.PILOT.getValue(), Optional.of(donoA));

        assertThrows(ConexaoInvalidaException.class,
                () -> service.finalizar(TestTenants.PILOT.getValue(), Optional.of(donoB),
                        inicio.state(), "code-valido", null, null),
                "O nonce foi emitido para o dono A, dono B nao pode finalizar");

        var fim = service.finalizar(TestTenants.PILOT.getValue(), Optional.of(donoA),
                inicio.state(), "code-valido", null, null);
        assertEquals(ChannelConnectionStatus.CONECTADO, fim.status());
    }

    @Test
    @DisplayName("revogar remove a conexao; consultar volta a vazio (nao conectado)")
    void revogarRemoveConexao() {
        var inicio = service.iniciar(TestTenants.PILOT.getValue(), Optional.empty());
        service.finalizar(TestTenants.PILOT.getValue(), Optional.empty(),
                inicio.state(), "code-valido", "waba-1", "phone-1");
        assertTrue(service.consultar(TestTenants.PILOT.getValue()).isPresent());

        service.revogar(TestTenants.PILOT.getValue());

        assertTrue(service.consultar(TestTenants.PILOT.getValue()).isEmpty());
    }

    @Test
    @DisplayName("revogar o tenant A nao afeta a conexao do tenant B")
    void revogarNaoAtravessaTenant() {
        service.iniciar(TestTenants.OUTRO.getValue(), Optional.empty());

        service.revogar(TestTenants.PILOT.getValue());

        assertTrue(service.consultar(TestTenants.OUTRO.getValue()).isPresent(),
                "Revogar o PILOT nao pode remover a conexao do OUTRO");
    }
}
