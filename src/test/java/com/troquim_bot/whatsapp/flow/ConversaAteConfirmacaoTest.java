package com.troquim_bot.whatsapp.flow;

import com.troquim_bot.support.CatalogoDeTeste;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.support.TestDias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.appointment.AppointmentStatus;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.application.messaging.FlowMessage;
import com.troquim_bot.application.messaging.OutboundFlowGateway;
import com.troquim_bot.application.messaging.OutboundResult;
import com.troquim_bot.conversation.StrictMvpMenuService;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.repository.BusinessRepository;
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

    @Autowired
    private ProvisionarNegocio provisionarNegocio;
    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private ConsultarCatalogo consultarCatalogo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String flowToken;

    /** Ids REAIS do catálogo persistido — o Flow não tem mais lista própria. */
    private String unhas;
    private String profissional;

    @BeforeEach
    void limpar() {
        GatewayDeTeste.ENVIADAS.clear();
        appointmentRepository.findAll().forEach(a -> appointmentRepository.delete(a.getId()));
        conversationStateService.limparEstado(TELEFONE);
        CatalogoDeTeste.provisionar(provisionarNegocio, businessRepository, TestTenants.PILOT);
        unhas = CatalogoDeTeste.servicoId(consultarCatalogo, TestTenants.PILOT, CatalogoDeTeste.UNHAS);
        profissional = CatalogoDeTeste.profissionalId(
                consultarCatalogo, TestTenants.PILOT, CatalogoDeTeste.UNHAS);
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
                "flow_action":"SERVICO_SELECIONADO","servico_id":"%s" """
                .formatted(unhas))).path("screen").asText());
        assertEquals("AGENDA", trocar(dataExchange("SERVICO", """
                "flow_action":"BUSCAR_DATAS","servico_id":"%s",
                "profissional_id":"%s" """.formatted(unhas, profissional))).path("screen").asText());

        JsonNode horarios = trocar(dataExchange("AGENDA", """
                "flow_action":"DATA_SELECIONADA","servico_id":"%s","profissional_id":"%s",
                "data":"%s" """.formatted(unhas, profissional, dia)));
        assertEquals("AGENDA", horarios.path("screen").asText());
        String horario = horarios.path("data").path("horarios").get(0).path("id").asText();

        assertEquals("CLIENTE", trocar(dataExchange("AGENDA", """
                "flow_action":"HORARIO_SELECIONADO","servico_id":"%s","profissional_id":"%s",
                "data":"%s","horario":"%s" """
                .formatted(unhas, profissional, dia, horario))).path("screen").asText());
        assertEquals("CONFIRMACAO", trocar(dataExchange("CLIENTE", """
                "flow_action":"MONTAR_RESUMO","servico_id":"%s","profissional_id":"%s",
                "data":"%s","horario":"%s","nome":"Ana Souza" """
                .formatted(unhas, profissional, dia, horario))).path("screen").asText());

        // 5. Confirma: o agendamento é persistido de verdade, com o serviço DO CATÁLOGO.
        JsonNode sucesso = trocar(confirm(dia, horario));
        assertEquals("SUCCESS", sucesso.path("screen").asText());
        assertEquals(1, appointmentApplicationService.listarAtivos(TestTenants.PILOT).size());
        assertEquals(CatalogoDeTeste.item(consultarCatalogo, TestTenants.PILOT, CatalogoDeTeste.UNHAS).id(),
                appointmentApplicationService.listarAtivos(TestTenants.PILOT).get(0).getServiceId(),
                "O agendamento aponta para o serviço que a tela ofereceu");

        // 6. A disponibilidade compartilhada já reflete o agendamento: o horário some da
        //    lista que a MESMA fronteira devolve. Se conversa e Flow tivessem fontes
        //    diferentes, este horário continuaria aparecendo como livre.
        JsonNode horariosDepois = trocar(dataExchange("AGENDA", """
                "flow_action":"DATA_SELECIONADA","servico_id":"%s","profissional_id":"%s",
                "data":"%s" """.formatted(unhas, profissional, dia)));
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

    @Test
    @DisplayName("21. conversa textual recusa serviço que não existe no catálogo")
    void conversaTextualNaoInventaServicoForaDoCatalogo() {
        ConversationState estado = conversationStateService.buscarPorNumero(TELEFONE);
        menu.processarMenu(TELEFONE, "1", estado);

        ConversationState aguardandoServico = conversationStateService.buscarPorNumero(TELEFONE);
        String resposta = menu.processarMenu(TELEFONE, "sobrancelha", aguardandoServico);

        assertTrue(resposta.toLowerCase().contains("nao esta disponivel")
                        || resposta.toLowerCase().contains("não está disponível"),
                "Serviço fora do catálogo não pode avançar no fluxo: " + resposta);
        assertEquals(com.troquim_bot.conversation.state.ConversationStep.AGUARDANDO_SERVICO,
                conversationStateService.buscarPorNumero(TELEFONE).getStep());
        assertEquals(0, appointmentApplicationService.listarAtivos(TestTenants.PILOT).size());
    }

    @Test
    @DisplayName("22. horário digitado fora das opções é rejeitado e não persiste")
    void horarioForaDaOfertaNaoAvancaNemCriaAppointment() {
        ConversationState estado = conversationStateService.buscarPorNumero(TELEFONE);
        menu.processarMenu(TELEFONE, "1", estado);

        String respostaServico = menu.processarMenu(
                TELEFONE, "unha", conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(respostaServico.toLowerCase().contains("dia"), respostaServico);

        String dia = TestDias.futuroComAgenda();
        String respostaDia = menu.processarMenu(
                TELEFONE, dia, conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(respostaDia.toLowerCase().contains("horarios"), respostaDia);
        assertFalse(respostaDia.contains("18h"),
                "18h é o fechamento e não deve aparecer como início de slot: " + respostaDia);

        String respostaHorario = menu.processarMenu(
                TELEFONE, "18h", conversationStateService.buscarPorNumero(TELEFONE));

        assertTrue(respostaHorario.toLowerCase().contains("nao esta disponivel")
                        || respostaHorario.toLowerCase().contains("não está disponível"),
                "Horário não ofertado precisa ser recusado: " + respostaHorario);
        assertEquals(com.troquim_bot.conversation.state.ConversationStep.AGUARDANDO_HORARIO,
                conversationStateService.buscarPorNumero(TELEFONE).getStep());
        assertEquals(0, appointmentApplicationService.listarAtivos(TestTenants.PILOT).size());
    }

    @Test
    @DisplayName("23. conversa textual consulta e cancela o Appointment real")
    void conversaTextualUsaAppointmentComoFonteDaVerdade() {
        ConversationState estado = conversationStateService.buscarPorNumero(TELEFONE);
        menu.processarMenu(TELEFONE, "1", estado);

        String servico = menu.processarMenu(
                TELEFONE, CatalogoDeTeste.UNHAS,
                conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(servico.toLowerCase().contains("dia"), servico);

        String dia = TestDias.futuroComAgenda();
        String horarios = menu.processarMenu(
                TELEFONE, dia, conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(horarios.toLowerCase().contains("horarios"), horarios);

        menu.processarMenu(
                TELEFONE, "1", conversationStateService.buscarPorNumero(TELEFONE));
        menu.processarMenu(
                TELEFONE, "Gui Teste", conversationStateService.buscarPorNumero(TELEFONE));

        String confirmacao = menu.processarMenu(
                TELEFONE, "1", conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(confirmacao.toLowerCase().contains("confirmado com sucesso"), confirmacao);

        var ativos = appointmentApplicationService.listarAtivos(TestTenants.PILOT);
        assertEquals(1, ativos.size());
        assertEquals(AppointmentStatus.CONFIRMADO, ativos.get(0).getStatus(),
                "Booking confirmado pelo cliente nao pode ficar aguardando aprovacao manual");

        String consulta = menu.processarMenu(
                TELEFONE, "ver agendamento",
                conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(consulta.contains(CatalogoDeTeste.UNHAS), consulta);
        assertTrue(consulta.contains("CONFIRMADO"), consulta);
        assertFalse(consulta.toLowerCase().contains("aguardando confirmacao do salao"), consulta);

        // Reproduz o bug real: abre outro fluxo e pergunta pelo agendamento no meio da
        // escolha de servico. Isso deve ser intencao global, nao um "servico invalido".
        menu.processarMenu(
                TELEFONE, "agendar", conversationStateService.buscarPorNumero(TELEFONE));
        String consultaNoMeioDoFluxo = menu.processarMenu(
                TELEFONE, "ver agendamento",
                conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(consultaNoMeioDoFluxo.contains(CatalogoDeTeste.UNHAS), consultaNoMeioDoFluxo);
        assertFalse(consultaNoMeioDoFluxo.toLowerCase().contains("servico nao esta disponivel"),
                consultaNoMeioDoFluxo);

        String cancelamento = menu.processarMenu(
                TELEFONE, "cancelar agendamento",
                conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(cancelamento.toLowerCase().contains("cancelado com sucesso"), cancelamento);
        assertTrue(appointmentApplicationService.listarAtivos(TestTenants.PILOT).isEmpty(),
                "Cancelar pela conversa precisa cancelar o Appointment persistido, nao so o draft");

        String depois = menu.processarMenu(
                TELEFONE, "ver agendamento",
                conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(depois.toLowerCase().contains("nao tem agendamentos ativos"), depois);
    }

    @Test
    @DisplayName("24. erro de digitacao sugere servico do catalogo e exige confirmacao")
    void erroDeDigitacaoSugereServicoSemInventar() {
        menu.processarMenu(TELEFONE, "1", conversationStateService.buscarPorNumero(TELEFONE));

        String sugestao = menu.processarMenu(
                TELEFONE, "unhz", conversationStateService.buscarPorNumero(TELEFONE));

        assertTrue(sugestao.contains("Voce quis dizer " + CatalogoDeTeste.UNHAS), sugestao);
        assertEquals(com.troquim_bot.conversation.state.ConversationStep.AGUARDANDO_SERVICO,
                conversationStateService.buscarPorNumero(TELEFONE).getStep(),
                "Sugestao nao pode decidir pelo cliente");

        String confirmado = menu.processarMenu(
                TELEFONE, "isso mesmo", conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(confirmado.toLowerCase().contains("dia"), confirmado);
        assertEquals(CatalogoDeTeste.UNHAS,
                conversationStateService.buscarPorNumero(TELEFONE).getDraftAtual().getServico());

        // A correcao confirmada vira memoria do tenant. Em outro fluxo e outra frase,
        // o mesmo token errado deve resolver direto, sem perguntar de novo.
        menu.processarMenu(TELEFONE, "menu", conversationStateService.buscarPorNumero(TELEFONE));
        menu.processarMenu(TELEFONE, "1", conversationStateService.buscarPorNumero(TELEFONE));
        String reaprendido = menu.processarMenu(
                TELEFONE, "preciso de unhz",
                conversationStateService.buscarPorNumero(TELEFONE));

        assertTrue(reaprendido.toLowerCase().contains("dia"), reaprendido);
        assertFalse(reaprendido.toLowerCase().contains("quis dizer"), reaprendido);
        assertEquals(CatalogoDeTeste.UNHAS,
                conversationStateService.buscarPorNumero(TELEFONE).getDraftAtual().getServico());
    }

    @Test
    @DisplayName("25. frase natural com servico do catalogo avanca sem lista rigida")
    void fraseNaturalComServicoDoCatalogo() {
        menu.processarMenu(TELEFONE, "1", conversationStateService.buscarPorNumero(TELEFONE));

        String resposta = menu.processarMenu(
                TELEFONE, "quero fazer unhas",
                conversationStateService.buscarPorNumero(TELEFONE));

        assertTrue(resposta.toLowerCase().contains("dia"), resposta);
        assertEquals(CatalogoDeTeste.UNHAS,
                conversationStateService.buscarPorNumero(TELEFONE).getDraftAtual().getServico());
    }

    @Test
    @DisplayName("26. volta retorna uma etapa e menu principal funciona de qualquer etapa")
    void navegacaoGlobalNaoPrendeClienteNoFormulario() {
        menu.processarMenu(TELEFONE, "1", conversationStateService.buscarPorNumero(TELEFONE));
        menu.processarMenu(TELEFONE, CatalogoDeTeste.UNHAS,
                conversationStateService.buscarPorNumero(TELEFONE));

        String voltou = menu.processarMenu(
                TELEFONE, "volta", conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(voltou.toLowerCase().contains("servico"), voltou);
        assertEquals(com.troquim_bot.conversation.state.ConversationStep.AGUARDANDO_SERVICO,
                conversationStateService.buscarPorNumero(TELEFONE).getStep());

        menu.processarMenu(TELEFONE, CatalogoDeTeste.UNHAS,
                conversationStateService.buscarPorNumero(TELEFONE));
        String principal = menu.processarMenu(
                TELEFONE, "menu principal", conversationStateService.buscarPorNumero(TELEFONE));

        assertTrue(principal.contains("1) Agendar"), principal);
        assertEquals(com.troquim_bot.conversation.state.ConversationStep.INICIO,
                conversationStateService.buscarPorNumero(TELEFONE).getStep());
    }

    @Test
    @DisplayName("27. ao escolher cancelar, numero simples cancela o item selecionado")
    void selecaoNumericaDeCancelamentoTemEstadoProprio() {
        String dia = TestDias.futuroComAgenda();
        criarAgendamentoTextual(TELEFONE, dia, "Ana Um");
        criarAgendamentoTextual(TELEFONE, dia, "Ana Dois");

        assertEquals(2, appointmentApplicationService.listarAtivos(TestTenants.PILOT).size());

        String lista = menu.processarMenu(
                TELEFONE, "3", conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(lista.toLowerCase().contains("qual deseja cancelar"), lista);
        assertEquals(com.troquim_bot.conversation.state.ConversationStep.AGUARDANDO_CANCELAMENTO,
                conversationStateService.buscarPorNumero(TELEFONE).getStep());

        String cancelado = menu.processarMenu(
                TELEFONE, "1", conversationStateService.buscarPorNumero(TELEFONE));
        assertTrue(cancelado.toLowerCase().contains("cancelado com sucesso"), cancelado);
        assertEquals(1, appointmentApplicationService.listarAtivos(TestTenants.PILOT).size(),
                "Numero simples apos a lista deve cancelar, nunca iniciar outro agendamento");
    }

    private void criarAgendamentoTextual(String numero, String dia, String nome) {
        menu.processarMenu(numero, "1", conversationStateService.buscarPorNumero(numero));
        menu.processarMenu(numero, CatalogoDeTeste.UNHAS,
                conversationStateService.buscarPorNumero(numero));
        menu.processarMenu(numero, dia, conversationStateService.buscarPorNumero(numero));
        menu.processarMenu(numero, "1", conversationStateService.buscarPorNumero(numero));
        menu.processarMenu(numero, nome, conversationStateService.buscarPorNumero(numero));
        String confirmacao = menu.processarMenu(
                numero, "1", conversationStateService.buscarPorNumero(numero));
        assertTrue(confirmacao.toLowerCase().contains("confirmado com sucesso"), confirmacao);
    }

    @Test
    @DisplayName("28. cancelar no meio do formulario abandona draft sem cancelar agenda salva")
    void cancelarFormularioNaoCancelaAppointmentExistente() {
        String dia = TestDias.futuroComAgenda();
        criarAgendamentoTextual(TELEFONE, dia, "Cliente Existente");
        assertEquals(1, appointmentApplicationService.listarAtivos(TestTenants.PILOT).size());

        menu.processarMenu(TELEFONE, "agendar",
                conversationStateService.buscarPorNumero(TELEFONE));
        menu.processarMenu(TELEFONE, CatalogoDeTeste.UNHAS,
                conversationStateService.buscarPorNumero(TELEFONE));

        String resposta = menu.processarMenu(
                TELEFONE, "cancelar", conversationStateService.buscarPorNumero(TELEFONE));

        assertTrue(resposta.toLowerCase().contains("atual descartado"), resposta);
        assertTrue(resposta.contains("1) Agendar"), resposta);
        assertEquals(1, appointmentApplicationService.listarAtivos(TestTenants.PILOT).size(),
                "Cancelar o formulario nao pode cancelar Appointment ja persistido");
    }

    @Test
    @DisplayName("29. confirmacao natural ok conclui o booking")
    void confirmacaoNaturalOkConcluiBooking() {
        String dia = TestDias.futuroComAgenda();

        menu.processarMenu(TELEFONE, "1", conversationStateService.buscarPorNumero(TELEFONE));
        menu.processarMenu(TELEFONE, CatalogoDeTeste.UNHAS,
                conversationStateService.buscarPorNumero(TELEFONE));
        menu.processarMenu(TELEFONE, dia, conversationStateService.buscarPorNumero(TELEFONE));
        menu.processarMenu(TELEFONE, "1", conversationStateService.buscarPorNumero(TELEFONE));
        menu.processarMenu(TELEFONE, "Cliente Natural",
                conversationStateService.buscarPorNumero(TELEFONE));

        String resposta = menu.processarMenu(
                TELEFONE, "ok", conversationStateService.buscarPorNumero(TELEFONE));

        assertTrue(resposta.toLowerCase().contains("confirmado com sucesso"), resposta);
        assertEquals(AppointmentStatus.CONFIRMADO,
                appointmentApplicationService.listarAtivos(TestTenants.PILOT).get(0).getStatus());
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
                "flow_action":"CONFIRMAR_AGENDAMENTO","servico_id":"%s","profissional_id":"%s",
                "data":"%s","horario":"%s","nome":"Ana Souza" """
                .formatted(unhas, profissional, dia, horario));
    }

    private static LocalDate proximaQuinta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != java.time.DayOfWeek.THURSDAY) {
            data = data.plusDays(1);
        }
        return data;
    }
}
