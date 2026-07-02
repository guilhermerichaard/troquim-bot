package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.UpdateBusinessRequest;
import com.troquim_bot.repository.InMemoryBusinessRepository;
import com.troquim_bot.application.business.BusinessApplicationService;
import com.troquim_bot.business.Business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import static org.junit.jupiter.api.Assertions.*;

class BusinessControllerTest {

    private MockMvc mockMvc;
    private BusinessApplicationService businessApplicationService;
    private InMemoryBusinessRepository businessRepository;

    @BeforeEach
    void setUp() {
        businessRepository = new InMemoryBusinessRepository();
        businessApplicationService = new BusinessApplicationService(businessRepository);
        BusinessController businessController = new BusinessController(businessApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(businessController).build();
    }

    // ==================== GET /business ====================

    @Test
    void deveRetornar200QuandoBusinessExiste() throws Exception {
        // Cria um Business primeiro
        businessApplicationService.criarBusinessPadrao("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        // Testa GET /business
        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nome").value("Meu Salão"))
            .andExpect(jsonPath("$.telefone").value("(11) 99999-9999"))
            .andExpect(jsonPath("$.endereco").value("São Paulo - SP"))
            .andExpect(jsonPath("$.status").value("TRIAL"))
            .andExpect(jsonPath("$.horarioFuncionamento").exists())
            .andExpect(jsonPath("$.horarioFuncionamento.abertura").value("09:00:00"))
            .andExpect(jsonPath("$.horarioFuncionamento.fechamento").value("19:00:00"))
            .andExpect(jsonPath("$.horarioFuncionamento.diasFuncionamento").isArray())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.criadoEm").exists())
            .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    void deveRetornar200ECriarBusinessPadraoQuandoNaoExiste() throws Exception {
        // Garante que não existe Business
        assertFalse(businessApplicationService.existeBusiness());

        // Testa GET /business - deve criar Business padrão automaticamente
        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nome").value("Meu Salão"))
            .andExpect(jsonPath("$.telefone").value("(11) 99999-9999"))
            .andExpect(jsonPath("$.endereco").value("São Paulo - SP"))
            .andExpect(jsonPath("$.status").value("TRIAL"));

        // Verifica que o Business foi criado
        assertTrue(businessApplicationService.existeBusiness());
    }

    // ==================== PUT /business ====================

    @Test
    void deveAtualizarNomePhoneEAddress() throws Exception {
        // Cria Business inicial
        businessApplicationService.criarBusinessPadrao("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        // Prepara request de atualização
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("Salão de Beleza Premium");
        request.setPhone("(11) 88888-8888");
        request.setAddress("Rio de Janeiro - RJ");

        // Testa PUT /business
        mockMvc.perform(put("/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Salão de Beleza Premium\",\"phone\":\"(11) 88888-8888\",\"address\":\"Rio de Janeiro - RJ\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nome").value("Salão de Beleza Premium"))
            .andExpect(jsonPath("$.telefone").value("(11) 88888-8888"))
            .andExpect(jsonPath("$.endereco").value("Rio de Janeiro - RJ"));
    }

    @Test
    void deveRetornar200AposAtualizacao() throws Exception {
        // Cria Business inicial
        businessApplicationService.criarBusinessPadrao("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        // Atualiza apenas o nome
        mockMvc.perform(put("/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo Nome\"}"))
            .andExpect(status().isOk());

        // Verifica que GET retorna os dados atualizados
        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Novo Nome"))
            .andExpect(jsonPath("$.telefone").value("(11) 99999-9999")) // Não alterado
            .andExpect(jsonPath("$.endereco").value("São Paulo - SP")); // Não alterado
    }

    @Test
    void deveAtualizarApenasCamposFornecidos() throws Exception {
        // Cria Business inicial
        businessApplicationService.criarBusinessPadrao("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        // Atualiza apenas o telefone
        mockMvc.perform(put("/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"(11) 77777-7777\"}"))
            .andExpect(status().isOk());

        // Verifica que apenas o telefone foi alterado
        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Meu Salão")) // Não alterado
            .andExpect(jsonPath("$.telefone").value("(11) 77777-7777")) // Alterado
            .andExpect(jsonPath("$.endereco").value("São Paulo - SP")); // Não alterado
    }

    @Test
    void deveRetornarBadRequestQuandoRequestNull() throws Exception {
        mockMvc.perform(put("/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveAtualizarTodosOsCamposIndependentemente() throws Exception {
        // Cria Business inicial
        businessApplicationService.criarBusinessPadrao("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        // Atualiza todos os campos
        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("Salão Completo");
        request.setPhone("(11) 66666-6666");
        request.setAddress("Belo Horizonte - MG");

        mockMvc.perform(put("/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Salão Completo\",\"phone\":\"(11) 66666-6666\",\"address\":\"Belo Horizonte - MG\"}"))
            .andExpect(status().isOk());

        // Verifica que todos os campos foram atualizados
        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Salão Completo"))
            .andExpect(jsonPath("$.telefone").value("(11) 66666-6666"))
            .andExpect(jsonPath("$.endereco").value("Belo Horizonte - MG"));
    }

    @Test
    void deveManterBusinessExistenteAposGet() throws Exception {
        // Cria Business com nome específico
        businessApplicationService.criarBusinessPadrao("Salão Original", "(11) 11111-1111", "Curitiba - PR");

        // Faz GET /business (não deve criar novo Business)
        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Salão Original"));

        // Verifica que ainda existe apenas 1 Business
        assertEquals(1, businessRepository.findAll().size());
        
        // Faz outro GET
        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Salão Original"));

        // Verifica que ainda existe apenas 1 Business (não criou duplicado)
        assertEquals(1, businessRepository.findAll().size());
    }
}