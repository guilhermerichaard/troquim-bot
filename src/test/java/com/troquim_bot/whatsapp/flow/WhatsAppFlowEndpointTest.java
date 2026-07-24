package com.troquim_bot.whatsapp.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import com.troquim_bot.whatsapp.flow.support.FlowTestCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Endpoint do WhatsApp Flow pela cadeia REAL (SecurityFilterChain + criptografia +
 * Application + persistência H2), profile test — CONTRATO CANÔNICO
 * (SERVICO → AGENDA → CLIENTE → CONFIRMACAO → SUCCESS reservada).
 *
 * O par de chaves RSA é gerado em memória por execução e injetado via
 * {@link DynamicPropertySource}. O lado cliente do protocolo é implementado
 * separadamente em {@link FlowTestCrypto}, de modo que o round-trip prove
 * interoperabilidade e não apenas simetria do mesmo código.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("WhatsApp Flow - Data Endpoint")
class WhatsAppFlowEndpointTest {

    private static final String ROTA = "/api/v1/whatsapp/flows";
    private static final String TELEFONE = "5511999990000";
    private static final String SUCCESS = "SUCCESS";

    private static final FlowTestCrypto CRYPTO = new FlowTestCrypto();

    @DynamicPropertySource
    static void chaves(DynamicPropertyRegistry registry) {
        registry.add("troquim.integrations.whatsapp.flow.enabled", () -> "true");
        registry.add("troquim.integrations.whatsapp.flow.private-key", CRYPTO::privateKeyPem);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FlowSessionStore sessionStore;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private AppointmentApplicationService appointmentApplicationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FlowSession sessao;

    @BeforeEach
    void abrirSessao() {
        appointmentRepository.findAll().forEach(a -> appointmentRepository.delete(a.getId()));
        sessao = sessionStore.abrir(TELEFONE, TestTenants.PILOT.getValue(),
                LocalDateTime.now().plusHours(1));
    }

    // ==================== 1. Health check ====================

    @Test
    @DisplayName("1. ping responde status active, sem campo version")
    void ping() throws Exception {
        JsonNode resposta = trocar("""
                {"version":"3.0","action":"ping","flow_token":"qualquer"}""");
        assertEquals("active", resposta.path("data").path("status").asText());
        // Conforme o exemplo oficial da Meta, a resposta NÃO carrega campo version.
        assertTrue(resposta.path("version").isMissingNode());
    }

    // ==================== 2-4. Envelope e criptografia ====================

    @Test
    @DisplayName("2. payload criptografado válido devolve a tela inicial SERVICO")
    void payloadValido() throws Exception {
        JsonNode resposta = trocar(init());
        assertEquals("SERVICO", resposta.path("screen").asText());
        assertTrue(resposta.path("data").path("servicos").size() > 0);
        assertFalse(resposta.path("data").path("profissional_habilitado").asBoolean(),
                "No INIT o profissional começa desabilitado");
    }

    @Test
    @DisplayName("3. envelope malformado devolve 400")
    void envelopeInvalido() throws Exception {
        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"encrypted_flow_data":"nao-e-base64!!","encrypted_aes_key":"","initial_vector":""}"""))
                .andExpect(result -> assertEquals(400, result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("4. falha ao decifrar a chave AES devolve 421")
    void chaveDessincronizada() throws Exception {
        FlowTestCrypto outraMeta = new FlowTestCrypto();
        FlowTestCrypto.Sessao cripto = outraMeta.novaSessao();
        String envelope = """
                {"encrypted_flow_data":"%s","encrypted_aes_key":"%s","initial_vector":"%s"}"""
                .formatted(outraMeta.cifrarCorpo(init(), cripto),
                        outraMeta.cifrarChaveAes(cripto.aesKey()),
                        java.util.Base64.getEncoder().encodeToString(cripto.iv()));
        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(envelope))
                .andExpect(result -> assertEquals(421, result.getResponse().getStatus()));
    }

    // ==================== 5. Evento desconhecido ====================

    @Test
    @DisplayName("5. evento desconhecido recomeça em SERVICO, sem 500")
    void acaoDesconhecida() throws Exception {
        JsonNode resposta = trocar(dataExchange("SERVICO",
                "\"flow_action\":\"EXPLODIR\""));
        assertEquals("SERVICO", resposta.path("screen").asText());
        assertFalse(resposta.path("data").path("error_message").asText().isEmpty());
    }

    // ==================== 6-8. Entrada não confiável ====================

    @Test
    @DisplayName("6. serviço inexistente não avança de tela")
    void servicoInexistente() throws Exception {
        JsonNode resposta = trocar(dataExchange("SERVICO", """
                "flow_action":"SERVICO_SELECIONADO","servico_id":"tatuagem" """));
        assertEquals("SERVICO", resposta.path("screen").asText());
        assertFalse(resposta.path("data").path("error_message").asText().isEmpty());
    }

    @Test
    @DisplayName("7. profissional incompatível é recusado na tela SERVICO")
    void profissionalIncompativel() throws Exception {
        JsonNode resposta = trocar(dataExchange("SERVICO", """
                "flow_action":"BUSCAR_DATAS","servico_id":"unha","profissional_id":"inexistente" """));
        assertEquals("SERVICO", resposta.path("screen").asText());
        assertFalse(resposta.path("data").path("error_message").asText().isEmpty());
    }

    @Test
    @DisplayName("8. data no passado volta à AGENDA com erro")
    void dataInvalida() throws Exception {
        JsonNode resposta = trocar(dataExchange("AGENDA", """
                "flow_action":"DATA_SELECIONADA","servico_id":"unha","profissional_id":"qualquer",
                "data":"2020-01-01" """));
        assertEquals("AGENDA", resposta.path("screen").asText());
        assertFalse(resposta.path("data").path("error_message").asText().isEmpty());
    }

    // ==================== 9-10. Disponibilidade ====================

    @Test
    @DisplayName("9. domingo não tem horários e a AGENDA sinaliza (horarios_habilitado=false)")
    void semHorarios() throws Exception {
        LocalDate domingo = proximo(java.time.DayOfWeek.SUNDAY);
        JsonNode resposta = trocar(dataExchange("AGENDA", """
                "flow_action":"DATA_SELECIONADA","servico_id":"unha","profissional_id":"qualquer",
                "data":"%s" """.formatted(domingo)));

        assertEquals("AGENDA", resposta.path("screen").asText());
        assertFalse(resposta.path("data").path("horarios_habilitado").asBoolean());
    }

    @Test
    @DisplayName("10. dia útil devolve horários; o dropdown de datas exclui domingos")
    void consultaDisponibilidade() throws Exception {
        LocalDate util = proximo(java.time.DayOfWeek.WEDNESDAY);
        JsonNode horarios = trocar(dataExchange("AGENDA", """
                "flow_action":"DATA_SELECIONADA","servico_id":"unha","profissional_id":"qualquer",
                "data":"%s" """.formatted(util)));

        assertTrue(horarios.path("data").path("horarios_habilitado").asBoolean());
        assertTrue(horarios.path("data").path("horarios").size() > 0);

        // BUSCAR_DATAS monta o dropdown de datas ELEGÍVEIS — domingos não entram.
        JsonNode agenda = trocar(dataExchange("SERVICO", """
                "flow_action":"BUSCAR_DATAS","servico_id":"unha","profissional_id":"qualquer" """));
        LocalDate domingo = proximo(java.time.DayOfWeek.SUNDAY);
        List<String> ids = new ArrayList<>();
        agenda.path("data").path("datas").forEach(d -> ids.add(d.path("id").asText()));
        assertFalse(ids.contains(domingo.toString()),
                "O próximo domingo não pode aparecer entre as datas elegíveis");
    }

    // ==================== 11-14. Confirmação ====================

    @Test
    @DisplayName("11. confirmação válida persiste e encerra na tela reservada SUCCESS")
    void confirmacaoValida() throws Exception {
        LocalDate dia = proximo(java.time.DayOfWeek.WEDNESDAY);
        JsonNode resposta = trocar(confirm(dia, "10:00"));

        assertEquals(SUCCESS, resposta.path("screen").asText());
        assertEquals(sessao.flowToken(), resposta.path("data")
                .path("extension_message_response").path("params").path("flow_token").asText());
        assertEquals(1, appointmentApplicationService.listarAtivos().size());
    }

    @Test
    @DisplayName("12. horário ocupado na corrida volta à AGENDA e não duplica")
    void horarioOcupadoNaCorrida() throws Exception {
        LocalDate dia = proximo(java.time.DayOfWeek.WEDNESDAY);

        FlowSession outra = sessionStore.abrir("5511988887777", TestTenants.PILOT.getValue(),
                LocalDateTime.now().plusHours(1));
        JsonNode primeira = trocarComToken(confirmToken(outra.flowToken(), dia, "11:00"));
        assertEquals(SUCCESS, primeira.path("screen").asText());

        JsonNode segunda = trocar(confirm(dia, "11:00"));
        assertNotEquals(SUCCESS, segunda.path("screen").asText());
        assertEquals("AGENDA", segunda.path("screen").asText());
        assertEquals(1, appointmentApplicationService.listarAtivos().size(),
                "A corrida não pode produzir um segundo agendamento no mesmo slot");
    }

    @Test
    @DisplayName("13. confirmação duplicada (mesmo token E mesmos dados) é idempotente")
    void confirmacaoDuplicada() throws Exception {
        LocalDate dia = proximo(java.time.DayOfWeek.WEDNESDAY);

        JsonNode primeira = trocar(confirm(dia, "14:00"));
        JsonNode segunda = trocar(confirm(dia, "14:00"));

        assertEquals(SUCCESS, primeira.path("screen").asText());
        assertEquals(SUCCESS, segunda.path("screen").asText());
        assertEquals(1, appointmentApplicationService.listarAtivos().size(),
                "A reentrega do mesmo flow_token não pode duplicar o agendamento");
    }

    @Test
    @DisplayName("13b. REGRA DO MVP: mesmo token com dados diferentes é recusado")
    void mesmoTokenDadosDiferentes() throws Exception {
        LocalDate dia = proximo(java.time.DayOfWeek.WEDNESDAY);

        JsonNode primeira = trocar(confirm(dia, "09:00"));
        assertEquals(SUCCESS, primeira.path("screen").asText());

        JsonNode segunda = trocar(confirm(dia, "10:00"));
        assertNotEquals(SUCCESS, segunda.path("screen").asText(),
                "Um flow_token vale por UM agendamento");
        assertEquals("CONFIRMACAO", segunda.path("screen").asText());

        String mensagem = segunda.path("data").path("error_message").asText();
        assertTrue(mensagem.toLowerCase().contains("peça a agenda novamente"),
                "Deve orientar a abrir um novo Flow: " + mensagem);
        assertFalse(mensagem.toLowerCase().contains("ocupado"));
        assertFalse(mensagem.toLowerCase().contains("tente novamente"));

        assertEquals(1, appointmentApplicationService.listarAtivos().size());
    }

    @Test
    @DisplayName("14. flow_token desconhecido devolve 427 e não agenda nada")
    void tokenInvalido() throws Exception {
        FlowTestCrypto.Sessao cripto = CRYPTO.novaSessao();
        String corpo = """
                {"version":"3.0","action":"data_exchange","screen":"CONFIRMACAO",
                 "flow_token":"token-que-nao-existe","data":{"flow_action":"CONFIRMAR_AGENDAMENTO"}}""";

        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(CRYPTO.envelope(corpo, cripto)))
                .andExpect(result -> assertEquals(427, result.getResponse().getStatus()));

        assertTrue(appointmentApplicationService.listarAtivos().isEmpty());
    }

    // ==================== 15-16. Protocolo e sigilo ====================

    @Test
    @DisplayName("15. a resposta é decifrável pelo cliente com o IV invertido")
    void respostaDecifravel() throws Exception {
        FlowTestCrypto.Sessao cripto = CRYPTO.novaSessao();
        MvcResult resultado = mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(CRYPTO.envelope(init(), cripto)))
                .andReturn();

        String base64 = resultado.getResponse().getContentAsString();
        assertFalse(base64.trim().startsWith("{"), "O corpo é base64 puro, nunca JSON em claro");

        String claro = CRYPTO.decifrarResposta(base64, cripto);
        assertEquals("SERVICO", objectMapper.readTree(claro).path("screen").asText());
    }

    @Test
    @DisplayName("16. nem o payload decifrado nem o telefone aparecem no log")
    void semVazamentoEmLog(CapturedOutput saida) throws Exception {
        LocalDate dia = proximo(java.time.DayOfWeek.WEDNESDAY);
        trocar(confirm(dia, "15:00"));
        trocar(dataExchange("SERVICO", "\"flow_action\":\"EXPLODIR\""));

        String log = saida.getAll();
        assertFalse(log.contains(TELEFONE), "O telefone do cliente não pode aparecer em log");
        assertFalse(log.contains(sessao.flowToken()), "O flow_token não pode aparecer em log");
        assertFalse(log.contains("\"flow_action\""), "O payload decifrado não pode aparecer em log");
        assertFalse(log.contains("BEGIN PRIVATE KEY"), "A chave privada não pode aparecer em log");
    }

    // ==================== 17. Fluxo completo ====================

    @Test
    @DisplayName("17. fluxo completo INIT -> SUCCESS pelas 4 telas canônicas")
    void fluxoCompleto() throws Exception {
        LocalDate dia = proximo(java.time.DayOfWeek.THURSDAY);

        assertEquals("SERVICO", trocar(init()).path("screen").asText());

        // on-select do serviço: re-renderiza SERVICO com profissional habilitado.
        JsonNode servico = trocar(dataExchange("SERVICO", """
                "flow_action":"SERVICO_SELECIONADO","servico_id":"cabelo" """));
        assertEquals("SERVICO", servico.path("screen").asText());
        assertTrue(servico.path("data").path("profissional_habilitado").asBoolean());

        // footer de SERVICO: navega para AGENDA.
        JsonNode agenda = trocar(dataExchange("SERVICO", """
                "flow_action":"BUSCAR_DATAS","servico_id":"cabelo","profissional_id":"qualquer" """));
        assertEquals("AGENDA", agenda.path("screen").asText());

        // on-select da data: re-renderiza AGENDA com horários.
        JsonNode comHorarios = trocar(dataExchange("AGENDA", """
                "flow_action":"DATA_SELECIONADA","servico_id":"cabelo","profissional_id":"qualquer",
                "data":"%s" """.formatted(dia)));
        assertEquals("AGENDA", comHorarios.path("screen").asText());
        String horario = comHorarios.path("data").path("horarios").get(0).path("id").asText();

        // footer de AGENDA: navega para CLIENTE.
        JsonNode cliente = trocar(dataExchange("AGENDA", """
                "flow_action":"HORARIO_SELECIONADO","servico_id":"cabelo","profissional_id":"qualquer",
                "data":"%s","horario":"%s" """.formatted(dia, horario)));
        assertEquals("CLIENTE", cliente.path("screen").asText());

        // footer de CLIENTE: navega para CONFIRMACAO.
        JsonNode confirmacao = trocar(dataExchange("CLIENTE", """
                "flow_action":"MONTAR_RESUMO","servico_id":"cabelo","profissional_id":"qualquer",
                "data":"%s","horario":"%s","nome":"Ana Souza" """.formatted(dia, horario)));
        assertEquals("CONFIRMACAO", confirmacao.path("screen").asText());
        assertEquals("Cabelo", confirmacao.path("data").path("resumo_servico").asText());

        // footer de CONFIRMACAO: confirma e encerra em SUCCESS.
        JsonNode sucesso = trocar(dataExchange("CONFIRMACAO", """
                "flow_action":"CONFIRMAR_AGENDAMENTO","servico_id":"cabelo","profissional_id":"qualquer",
                "data":"%s","horario":"%s","nome":"Ana Souza" """.formatted(dia, horario)));
        assertEquals(SUCCESS, sucesso.path("screen").asText());
        assertEquals(1, appointmentApplicationService.listarAtivos().size());
    }

    // ==================== helpers ====================

    private JsonNode trocar(String corpoClaro) throws Exception {
        return trocarComToken(corpoClaro);
    }

    private JsonNode trocarComToken(String corpoClaro) throws Exception {
        FlowTestCrypto.Sessao cripto = CRYPTO.novaSessao();
        MvcResult resultado = mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(CRYPTO.envelope(corpoClaro, cripto)))
                .andReturn();
        assertEquals(200, resultado.getResponse().getStatus(), "Esperava 200 do endpoint do Flow");
        return objectMapper.readTree(
                CRYPTO.decifrarResposta(resultado.getResponse().getContentAsString(), cripto));
    }

    private String init() {
        return """
                {"version":"3.0","action":"INIT","flow_token":"%s","data":{}}"""
                .formatted(sessao.flowToken());
    }

    private String dataExchange(String tela, String campos) {
        return """
                {"version":"3.0","action":"data_exchange","screen":"%s","flow_token":"%s",
                 "data":{%s}}""".formatted(tela, sessao.flowToken(), campos);
    }

    private String confirm(LocalDate dia, String horario) {
        return confirmToken(sessao.flowToken(), dia, horario);
    }

    private String confirmToken(String token, LocalDate dia, String horario) {
        return """
                {"version":"3.0","action":"data_exchange","screen":"CONFIRMACAO","flow_token":"%s",
                 "data":{"flow_action":"CONFIRMAR_AGENDAMENTO","servico_id":"unha","profissional_id":"qualquer",
                 "data":"%s","horario":"%s","nome":"Ana Souza"}}"""
                .formatted(token, dia, horario);
    }

    private static LocalDate proximo(java.time.DayOfWeek alvo) {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != alvo) {
            data = data.plusDays(1);
        }
        return data;
    }
}
