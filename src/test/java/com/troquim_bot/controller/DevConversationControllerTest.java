package com.troquim_bot.controller;

import com.troquim_bot.application.conversation.ConversationApplicationService;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.conversation.state.ConversationStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevConversationControllerTest {

    private MockMvc mockMvc;
    private ConversationApplicationService conversationApplicationService;
    private ConversationStateService conversationStateService;

    @BeforeEach
    void setUp() {
        conversationApplicationService = mock(ConversationApplicationService.class);
        conversationStateService = mock(ConversationStateService.class);

        DevConversationController controller = new DevConversationController(
            conversationApplicationService,
            conversationStateService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void deveProcessarMensagemComSucesso() throws Exception {
        String numero = "5511999999999";
        String mensagem = "Oi";

        when(conversationApplicationService.processarMensagem(numero, mensagem))
            .thenReturn("Olá! Como posso ajudar?");

        ConversationState state = new ConversationState(numero);
        state.setStep(ConversationStep.INICIO);
        when(conversationStateService.buscarPorNumero(numero)).thenReturn(state);

        String requestBody = "{\"number\":\"" + numero + "\",\"message\":\"" + mensagem + "\"}";

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Olá! Como posso ajudar?"))
            .andExpect(jsonPath("$.conversationState").value("INICIO"))
            .andExpect(jsonPath("$.debug.processingTimeMs").isNumber());
    }

    @Test
    void deveRetornar400QuandoRequestNulo() throws Exception {
        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoNumberAusente() throws Exception {
        String requestBody = "{\"message\":\"Oi\"}";

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoMessageAusente() throws Exception {
        String requestBody = "{\"number\":\"5511999999999\"}";

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornarRespostaComCustomerQuandoNomeInformado() throws Exception {
        String numero = "5511999999999";
        String mensagem = "Meu nome é Maria";

        when(conversationApplicationService.processarMensagem(numero, mensagem))
            .thenReturn("Perfeito, Maria. Vou lembrar.");

        ConversationState state = new ConversationState(numero);
        state.setStep(ConversationStep.AGUARDANDO_NOME);
        state.setNome("Maria");
        when(conversationStateService.buscarPorNumero(numero)).thenReturn(state);

        String requestBody = "{\"number\":\"" + numero + "\",\"message\":\"" + mensagem + "\"}";

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Perfeito, Maria. Vou lembrar."))
            .andExpect(jsonPath("$.customer").value("Maria"))
            .andExpect(jsonPath("$.conversationState").value("AGUARDANDO_NOME"))
            .andExpect(jsonPath("$.debug.processingTimeMs").isNumber());
    }

    @Test
    void deveRetornarRespostaComEstadoAguardandoServico() throws Exception {
        String numero = "5511999999999";
        String mensagem = "Quero agendar unha";

        when(conversationApplicationService.processarMensagem(numero, mensagem))
            .thenReturn("Perfeito. Para qual dia você gostaria?");

        ConversationState state = new ConversationState(numero);
        state.setStep(ConversationStep.AGUARDANDO_DIA);
        when(conversationStateService.buscarPorNumero(numero)).thenReturn(state);

        String requestBody = "{\"number\":\"" + numero + "\",\"message\":\"" + mensagem + "\"}";

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Perfeito. Para qual dia você gostaria?"))
            .andExpect(jsonPath("$.conversationState").value("AGUARDANDO_DIA"))
            .andExpect(jsonPath("$.debug.processingTimeMs").isNumber());
    }

    @Test
    void deveRetornarRespostaComEstadoAguardandoHorario() throws Exception {
        String numero = "5511999999999";
        String mensagem = "Segunda";

        when(conversationApplicationService.processarMensagem(numero, mensagem))
            .thenReturn("Certo. Qual horário você prefere na segunda?");

        ConversationState state = new ConversationState(numero);
        state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        when(conversationStateService.buscarPorNumero(numero)).thenReturn(state);

        String requestBody = "{\"number\":\"" + numero + "\",\"message\":\"" + mensagem + "\"}";

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Certo. Qual horário você prefere na segunda?"))
            .andExpect(jsonPath("$.conversationState").value("AGUARDANDO_HORARIO"))
            .andExpect(jsonPath("$.debug.processingTimeMs").isNumber());
    }

    @Test
    void deveRetornarRespostaComEstadoAguardandoConfirmacao() throws Exception {
        String numero = "5511999999999";
        String mensagem = "14h";

        when(conversationApplicationService.processarMensagem(numero, mensagem))
            .thenReturn("Perfeito, Maria. Recebi sua solicitação para unha na segunda às 14h. Estou registrando sua solicitação.");

        ConversationState state = new ConversationState(numero);
        state.setStep(ConversationStep.AGUARDANDO_CONFIRMACAO);
        state.setNome("Maria");
        when(conversationStateService.buscarPorNumero(numero)).thenReturn(state);

        String requestBody = "{\"number\":\"" + numero + "\",\"message\":\"" + mensagem + "\"}";

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Perfeito, Maria. Recebi sua solicitação para unha na segunda às 14h. Estou registrando sua solicitação."))
            .andExpect(jsonPath("$.conversationState").value("AGUARDANDO_CONFIRMACAO"))
            .andExpect(jsonPath("$.customer").value("Maria"))
            .andExpect(jsonPath("$.debug.processingTimeMs").isNumber());
    }

    @Test
    void deveRetornarRespostaComEstadoInicioParaNumeroNovo() throws Exception {
        String numero = "5511999999999";
        String mensagem = "Bom dia";

        when(conversationApplicationService.processarMensagem(numero, mensagem))
            .thenReturn("Olá! Como posso ajudar?");

        ConversationState state = new ConversationState(numero);
        state.setStep(ConversationStep.INICIO);
        when(conversationStateService.buscarPorNumero(numero)).thenReturn(state);

        String requestBody = "{\"number\":\"" + numero + "\",\"message\":\"" + mensagem + "\"}";

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Olá! Como posso ajudar?"))
            .andExpect(jsonPath("$.conversationState").value("INICIO"))
            .andExpect(jsonPath("$.debug.processingTimeMs").isNumber());
    }

    @Test
    void deveRetornarMenuPrincipalParaQuemSouEu() throws Exception {
        String numero = "5511999999999";
        String mensagem = "Quem sou eu";

        when(conversationApplicationService.processarMensagem(numero, mensagem))
            .thenReturn("Olá! No momento eu consigo te ajudar com agendamentos. Escolha uma opção:\n\n" +
                "1) Agendar\n" +
                "2) Meus agendamentos\n" +
                "3) Cancelar");

        ConversationState state = new ConversationState(numero);
        state.setStep(ConversationStep.INICIO);
        when(conversationStateService.buscarPorNumero(numero)).thenReturn(state);

        String requestBody = "{\"number\":\"" + numero + "\",\"message\":\"" + mensagem + "\"}";

        mockMvc.perform(post("/dev/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("Olá! No momento eu consigo te ajudar com agendamentos. Escolha uma opção:\n\n" +
                "1) Agendar\n" +
                "2) Meus agendamentos\n" +
                "3) Cancelar"))
            .andExpect(jsonPath("$.conversationState").value("INICIO"))
            .andExpect(jsonPath("$.debug.processingTimeMs").isNumber());
    }
}
