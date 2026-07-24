package com.troquim_bot.whatsapp.flow;

import com.troquim_bot.application.booking.AberturaDeAgendaResultado;
import com.troquim_bot.application.messaging.FlowMessage;
import com.troquim_bot.application.messaging.OutboundFlowGateway;
import com.troquim_bot.application.messaging.OutboundResult;
import com.troquim_bot.support.OptionalBeans;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.whatsapp.flow.application.AbrirAgendaPorFlowService;
import com.troquim_bot.whatsapp.flow.application.session.FlowConfirmationOutcome;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStatus;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.WhatsAppFlowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caso de uso de abertura da agenda por Flow, isolado (sem Spring).
 *
 * A infraestrutura é substituída por duplos declarados aqui — {@code GatewayEspiao} e
 * {@code SessionStoreEmMemoria} — para que a ORDEM das operações e a compensação sejam
 * observáveis, coisa que um teste de integração esconderia atrás do transporte HTTP.
 */
@DisplayName("Abertura da agenda por WhatsApp Flow")
class AbrirAgendaPorFlowServiceTest {

    private static final String TELEFONE = "5511999990000";

    private SessionStoreEmMemoria store;
    private WhatsAppFlowProperties properties;

    @BeforeEach
    void setUp() {
        store = new SessionStoreEmMemoria();
        properties = new WhatsAppFlowProperties();
        properties.setEnabled(true);
        properties.setFlowId("1234567890");
        properties.setSessaoTtlMinutos(30);
    }

    // ==================== 1-4. Criação segura da sessão ====================

    @Test
    @DisplayName("1. cria a sessão ANTES de enviar a mensagem")
    void sessaoCriadaAntesDoEnvio() {
        GatewayEspiao gateway = new GatewayEspiao();
        AberturaDeAgendaResultado resultado = servico(gateway).abrirPara(TELEFONE);

        assertTrue(resultado.abriu());
        assertEquals(1, store.sessoes.size());
        // O gateway só foi chamado depois de a sessão existir: se fosse ao contrário, um
        // toque imediato no botão encontraria um token desconhecido.
        assertTrue(gateway.sessaoExistiaNoEnvio,
                "A sessão precisa estar persistida antes do envio");
    }

    @Test
    @DisplayName("2. o token é imprevisível e não deriva do telefone")
    void tokenImprevisivel() {
        GatewayEspiao gateway = new GatewayEspiao();
        AbrirAgendaPorFlowService servico = servico(gateway);

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            servico.abrirPara(TELEFONE);
            tokens.add(gateway.ultimaMensagem.flowToken());
        }

        assertEquals(50, tokens.stream().distinct().count(), "Tokens não podem repetir");
        for (String token : tokens) {
            assertFalse(token.contains(TELEFONE), "O token não pode conter o telefone");
            assertTrue(token.length() >= 40, "Token curto demais para 256 bits: " + token.length());
        }
    }

    @Test
    @DisplayName("3-4. a sessão fica vinculada ao telefone e ao businessId corretos")
    void vinculoComClienteETenant() {
        GatewayEspiao gateway = new GatewayEspiao();
        servico(gateway).abrirPara(TELEFONE);

        FlowSession sessao = store.sessoes.values().iterator().next();
        assertEquals(TELEFONE, sessao.telefone());
        assertEquals(TestTenants.PILOT.getValue(), sessao.businessId());
        assertEquals(FlowSessionStatus.ABERTA, sessao.status());
        assertTrue(sessao.pertenceA(TELEFONE, TestTenants.PILOT.getValue()));
    }

    @Test
    @DisplayName("8-9. o token não serve para outro cliente nem para outro negócio")
    void tokenNaoServeParaOutroClienteOuTenant() {
        servico(new GatewayEspiao()).abrirPara(TELEFONE);
        FlowSession sessao = store.sessoes.values().iterator().next();

        assertFalse(sessao.pertenceA("5511911112222", TestTenants.PILOT.getValue()),
                "Outro cliente não pode usar este token");
        assertFalse(sessao.pertenceA(TELEFONE, TestTenants.OUTRO.getValue()),
                "Outro tenant não pode usar este token");
    }

    // ==================== 10-12. Envio ====================

    @Test
    @DisplayName("12. a mensagem enviada carrega Flow, token e o botão 'Abrir agenda'")
    void mensagemInterativaCorreta() {
        GatewayEspiao gateway = new GatewayEspiao();
        servico(gateway).abrirPara(TELEFONE);

        FlowMessage mensagem = gateway.ultimaMensagem;
        assertEquals("1234567890", mensagem.flowId());
        assertEquals("Abrir agenda", mensagem.cta());
        assertEquals(TELEFONE, gateway.ultimoDestino);
        assertFalse(mensagem.corpo().isBlank());
        assertFalse(mensagem.modoRascunho(), "Produção não pode enviar em modo draft");
        // O token enviado é EXATAMENTE o da sessão criada.
        assertEquals(store.sessoes.keySet().iterator().next(), mensagem.flowToken());
    }

    @Test
    @DisplayName("10-11. falha no envio invalida a sessão (compensação)")
    void falhaNoEnvioInvalidaSessao() {
        GatewayEspiao gateway = new GatewayEspiao();
        gateway.falhar = true;

        AberturaDeAgendaResultado resultado = servico(gateway).abrirPara(TELEFONE);

        assertFalse(resultado.abriu());
        assertEquals(AberturaDeAgendaResultado.Status.FALHA_NO_ENVIO, resultado.status());

        FlowSession sessao = store.sessoes.values().iterator().next();
        assertEquals(FlowSessionStatus.INVALIDADA, sessao.status(),
                "Um token que ninguém recebeu não pode continuar valendo");
        assertFalse(sessao.utilizavel(LocalDateTime.now()));
    }

    // ==================== 13. Configuração ausente ====================

    @Test
    @DisplayName("13. sem flow-id nem flow-name a abertura fica indisponível, sem exceção")
    void configuracaoAusente() {
        properties.setFlowId(null);
        properties.setFlowName(null);
        GatewayEspiao gateway = new GatewayEspiao();
        AbrirAgendaPorFlowService servico = servico(gateway);

        assertFalse(servico.disponivel());
        AberturaDeAgendaResultado resultado = servico.abrirPara(TELEFONE);

        assertEquals(AberturaDeAgendaResultado.Status.INDISPONIVEL, resultado.status());
        assertTrue(store.sessoes.isEmpty(), "Sem envio possível, nenhuma sessão deve sobrar");
        assertEquals(0, gateway.chamadas.get());
    }

    @Test
    @DisplayName("13b. canal sem suporte a Flow não gera sessão órfã")
    void canalSemSuporte() {
        AbrirAgendaPorFlowService servico = new AbrirAgendaPorFlowService(
                store, TestTenants.pilot(), properties, OptionalBeans.ausente());

        assertFalse(servico.disponivel());
        assertEquals(AberturaDeAgendaResultado.Status.CANAL_NAO_SUPORTA,
                servico.abrirPara(TELEFONE).status());
        assertTrue(store.sessoes.isEmpty());
    }

    @Test
    @DisplayName("recurso desligado nunca é considerado disponível")
    void recursoDesligado() {
        properties.setEnabled(false);
        assertFalse(servico(new GatewayEspiao()).disponivel());
    }

    // ==================== 5-7. TTL e estados ====================

    @Test
    @DisplayName("5. sessão expirada não é utilizável, mesmo sem rotina de limpeza")
    void sessaoExpirada() {
        FlowSession vencida = new FlowSession("t", TELEFONE, TestTenants.PILOT.getValue(),
                FlowSessionStatus.ABERTA, LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusMinutes(1), Optional.empty());

        assertTrue(vencida.expirada(LocalDateTime.now()));
        assertFalse(vencida.utilizavel(LocalDateTime.now()));
    }

    @Test
    @DisplayName("7. sessão concluída continua legível — é o que reconhece a repetição")
    void sessaoConcluidaAindaLegivel() {
        FlowSession concluida = new FlowSession("t", TELEFONE, TestTenants.PILOT.getValue(),
                FlowSessionStatus.CONCLUIDA, LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(10),
                Optional.of(new FlowConfirmationOutcome("Unhas", "2026-08-05", "10:00")));

        assertTrue(concluida.utilizavel(LocalDateTime.now()),
                "Apagar/bloquear a sessão concluída quebraria a idempotência do CONFIRM");
        assertTrue(concluida.jaConfirmada());
    }

    @Test
    @DisplayName("sessão invalidada nunca volta a ser utilizável")
    void sessaoInvalidadaNaoVolta() {
        FlowSession invalidada = new FlowSession("t", TELEFONE, TestTenants.PILOT.getValue(),
                FlowSessionStatus.INVALIDADA, LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(10), Optional.empty());

        assertFalse(invalidada.utilizavel(LocalDateTime.now()));
    }

    @Test
    @DisplayName("o TTL configurado é o que define o vencimento")
    void ttlConfiguravel() {
        properties.setSessaoTtlMinutos(5);
        servico(new GatewayEspiao()).abrirPara(TELEFONE);

        FlowSession sessao = store.sessoes.values().iterator().next();
        LocalDateTime esperado = LocalDateTime.now().plusMinutes(5);
        assertTrue(sessao.expiraEm().isAfter(esperado.minusMinutes(1))
                        && sessao.expiraEm().isBefore(esperado.plusMinutes(1)),
                "expiraEm deveria refletir o TTL configurado: " + sessao.expiraEm());
        assertNotEquals(sessao.criadaEm(), sessao.expiraEm());
    }

    private AbrirAgendaPorFlowService servico(GatewayEspiao gateway) {
        return new AbrirAgendaPorFlowService(
                store, TestTenants.pilot(), properties, OptionalBeans.de(gateway));
    }

    // ==================== duplos ====================

    /** Gateway falso: registra o que foi enviado e permite simular falha de envio. */
    private final class GatewayEspiao implements OutboundFlowGateway {
        private final AtomicInteger chamadas = new AtomicInteger();
        private boolean falhar = false;
        private FlowMessage ultimaMensagem;
        private String ultimoDestino;
        private boolean sessaoExistiaNoEnvio;

        @Override
        public OutboundResult sendFlow(String toPhone, FlowMessage message) {
            chamadas.incrementAndGet();
            this.ultimoDestino = toPhone;
            this.ultimaMensagem = message;
            this.sessaoExistiaNoEnvio = store.sessoes.containsKey(message.flowToken());
            if (falhar) {
                throw new IllegalStateException("Falha simulada de envio");
            }
            return new OutboundResult("wamid.TESTE", "sent");
        }
    }

    /** Store falso, com a mesma semântica de estado do adaptador JPA. */
    private static final class SessionStoreEmMemoria implements FlowSessionStore {
        private final Map<String, FlowSession> sessoes = new HashMap<>();
        private final AtomicInteger contador = new AtomicInteger();

        @Override
        public FlowSession abrir(String telefone, UUID businessId, LocalDateTime expiraEm) {
            // Token longo e distinto por chamada, como o SecureRandom do adaptador real.
            String token = UUID.randomUUID() + "-" + UUID.randomUUID() + "-" + contador.incrementAndGet();
            FlowSession sessao = new FlowSession(token, telefone, businessId,
                    FlowSessionStatus.ABERTA, LocalDateTime.now(), expiraEm, Optional.empty());
            sessoes.put(token, sessao);
            return sessao;
        }

        @Override
        public Optional<FlowSession> buscar(String flowToken) {
            return Optional.ofNullable(sessoes.get(flowToken));
        }

        @Override
        public FlowConfirmationOutcome registrarConfirmacao(String flowToken,
                                                            FlowConfirmationOutcome outcome) {
            FlowSession atual = sessoes.get(flowToken);
            if (atual.jaConfirmada()) {
                return atual.resultado().orElseThrow();
            }
            sessoes.put(flowToken, new FlowSession(atual.flowToken(), atual.telefone(),
                    atual.businessId(), FlowSessionStatus.CONCLUIDA, atual.criadaEm(),
                    atual.expiraEm(), Optional.of(outcome)));
            return outcome;
        }

        @Override
        public void invalidar(String flowToken) {
            FlowSession atual = sessoes.get(flowToken);
            if (atual != null) {
                sessoes.put(flowToken, new FlowSession(atual.flowToken(), atual.telefone(),
                        atual.businessId(), FlowSessionStatus.INVALIDADA, atual.criadaEm(),
                        atual.expiraEm(), atual.resultado()));
            }
        }
    }
}
