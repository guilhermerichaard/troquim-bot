package com.troquim_bot.whatsapp.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.messaging.FlowMessage;
import com.troquim_bot.application.messaging.OutboundFlowGateway;
import com.troquim_bot.application.messaging.OutboundResult;
import com.troquim_bot.conversation.StrictMvpMenuService;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.whatsapp.flow.support.FlowTestCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Cenário 20 — o caminho INTEIRO, pela cadeia real:
 *
 * <pre>
 * conversa ("1" = agendar)
 *   → capacidade de abertura de agenda
 *   → sessão persistida + mensagem interativa enviada
 *   → cliente toca no botão → Data Endpoint (INIT)
 *   → telas → CONFIRM
 *   → Appointment persistido
 * </pre>
 *
 * O ÚNICO duplo é o gateway de saída (não há Meta para receber a mensagem); ele captura
 * o {@code flow_token} realmente enviado, que é o mesmo usado nas chamadas seguintes ao
 * endpoint. Tudo o mais — Security, criptografia, sessões, domínio, H2 — é real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Ponta a ponta: conversa -> Flow -> agendamento")
class ConversaAteConfirmacaoTest {

    private static final String ROTA = "/api/v1/whatsapp/flows";
    private static final String TELEFONE = "5511955554444";
    private static final FlowTestCrypto CRYPTO = new FlowTestCrypto();

    @DynamicPropertySource
    static void configuracao(DynamicPropertyRegistry registry) {
        registry.add("troquim.integrations.whatsapp.flow.enabled", () -> "true");
        registry.add("troquim.integrations.whatsapp.flow.private-key", CRYPTO::privateKeyPem);
        registry.add("troquim.integrations.whatsapp.flow.flow-id", () -> "1234567890");
        registry.add("conversation.mode", () -> "STRICT_MVP");
    }

    /** Único duplo: não há Meta do outro lado para receber a mensagem. */
    @TestConfiguration
    static class GatewayDeTeste {
        static final List<FlowMessage> ENVIADAS = new ArrayList<>();

        // @Primary: o gateway real da Cloud API tambem existe neste contexto (a
        // integracao esta ligada em application-test.properties). Aqui queremos o duplo.
        @Bean
        @Primary
        OutboundFlowGateway outboundFlowGatewayDeTeste() {
            return (toPhone, message) -> {
                ENVIADAS.add(message);
                return new OutboundResult("wamid.E2E", "sent");
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StrictMvpMenuService menu;

    @Autowired
    private ConversationStateService conversationStateService;

    @Autowired
    private AppointmentApplicationService appointmentApplicationService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String flowToken;

    @BeforeEach
    void limpar() {
        GatewayDeTeste.ENVIADAS.clear();
        appointmentRepository.findAll().forEach(a -> appointmentRepository.delete(a.getId()));
        conversationStateService.limparEstado(TELEFONE);
    }

    @Test
    @DisplayName("20. da conversa ao Appointment persistido")
    void fluxoCompleto() throws Exception {
        // 1. O cliente pede para agendar na conversa.
        ConversationState estado = conversationStateService.buscarPorNumero(TELEFONE);
        String resposta = menu.processarMenu(TELEFONE, "1", estado);

        // A conversa oferece o botão, com texto natural — sem "digite 1" para a agenda.
        assertTrue(resposta.contains("Abrir agenda"),
                "A conversa deveria anunciar o botão: " + resposta);
        assertEquals(1, GatewayDeTeste.ENVIADAS.size(), "Uma mensagem de Flow deveria ter saído");

        // 2. O token veio do servidor, nunca do cliente.
        FlowMessage enviada = GatewayDeTeste.ENVIADAS.get(0);
        assertEquals("Abrir agenda", enviada.cta());
        assertEquals("1234567890", enviada.flowId());
        flowToken = enviada.flowToken();
        assertFalse(flowToken.contains(TELEFONE));

        // 3. O cliente toca no botão: a Meta chama o endpoint com INIT.
        JsonNode servicos = trocar("""
                {"version":"3.0","action":"INIT","flow_token":"%s","data":{}}""".formatted(flowToken));
        assertEquals("SERVICO", servicos.path("screen").asText());

        // 4. Percorre as telas do contrato canônico.
        LocalDate dia = proximaQuinta();
        assertEquals("SERVICO", trocar(dataExchange("SERVICO", """
                "flow_action":"SERVICO_SELECIONADO","servico_id":"unha" """)).path("screen").asText());
        assertEquals("AGENDA", trocar(dataExchange("SERVICO", """
                "flow_action":"BUSCAR_DATAS","servico_id":"unha",
                "profissional_id":"qualquer" """)).path("screen").asText());

        JsonNode horarios = trocar(dataExchange("AGENDA", """
                "flow_action":"DATA_SELECIONADA","servico_id":"unha","profissional_id":"qualquer",
                "data":"%s" """.formatted(dia)));
        assertEquals("AGENDA", horarios.path("screen").asText());
        String horario = horarios.path("data").path("horarios").get(0).path("id").asText();

        assertEquals("CLIENTE", trocar(dataExchange("AGENDA", """
                "flow_action":"HORARIO_SELECIONADO","servico_id":"unha","profissional_id":"qualquer",
                "data":"%s","horario":"%s" """.formatted(dia, horario))).path("screen").asText());
        assertEquals("CONFIRMACAO", trocar(dataExchange("CLIENTE", """
                "flow_action":"MONTAR_RESUMO","servico_id":"unha","profissional_id":"qualquer",
                "data":"%s","horario":"%s","nome":"Ana Souza" """
                .formatted(dia, horario))).path("screen").asText());

        // 5. Confirma: o agendamento é persistido de verdade.
        JsonNode sucesso = trocar(confirm(dia, horario));
        assertEquals("SUCCESS", sucesso.path("screen").asText());
        assertEquals(1, appointmentApplicationService.listarAtivos().size());

        // 6. A disponibilidade compartilhada já reflete o agendamento: o horário some da
        //    lista que a MESMA fronteira devolve. Se conversa e Flow tivessem fontes
        //    diferentes, este horário continuaria aparecendo como livre.
        JsonNode horariosDepois = trocar(dataExchange("AGENDA", """
                "flow_action":"DATA_SELECIONADA","servico_id":"unha","profissional_id":"qualquer",
                "data":"%s" """.formatted(dia)));
        List<String> livres = new ArrayList<>();
        horariosDepois.path("data").path("horarios").forEach(h -> livres.add(h.path("id").asText()));
        assertFalse(livres.contains(horario),
                "O horário agendado não pode continuar sendo oferecido");
    }

    @Test
    @DisplayName("19. sem Flow disponível, a conversa textual segue intacta")
    void conversaPreservadaSemFlow() {
        // O menu textual continua respondendo quando o cliente ignora o botão e escreve.
        ConversationState estado = conversationStateService.buscarPorNumero(TELEFONE);
        menu.processarMenu(TELEFONE, "1", estado);

        ConversationState depois = conversationStateService.buscarPorNumero(TELEFONE);
        String resposta = menu.processarMenu(TELEFONE, "unha", depois);

        assertTrue(resposta.toLowerCase().contains("dia"),
                "O caminho textual deveria seguir para a escolha do dia: " + resposta);
    }

    // ==================== helpers ====================

    private JsonNode trocar(String corpoClaro) throws Exception {
        FlowTestCrypto.Sessao cripto = CRYPTO.novaSessao();
        MvcResult resultado = mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                .content(CRYPTO.envelope(corpoClaro, cripto))).andReturn();

        assertEquals(200, resultado.getResponse().getStatus());
        return objectMapper.readTree(
                CRYPTO.decifrarResposta(resultado.getResponse().getContentAsString(), cripto));
    }

    private String dataExchange(String tela, String campos) {
        return """
                {"version":"3.0","action":"data_exchange","screen":"%s","flow_token":"%s",
                 "data":{%s}}""".formatted(tela, flowToken, campos);
    }

    private String confirm(LocalDate dia, String horario) {
        return dataExchange("CONFIRMACAO", """
                "flow_action":"CONFIRMAR_AGENDAMENTO","servico_id":"unha","profissional_id":"qualquer",
                "data":"%s","horario":"%s","nome":"Ana Souza" """.formatted(dia, horario));
    }

    private static LocalDate proximaQuinta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != java.time.DayOfWeek.THURSDAY) {
            data = data.plusDays(1);
        }
        return data;
    }
}
