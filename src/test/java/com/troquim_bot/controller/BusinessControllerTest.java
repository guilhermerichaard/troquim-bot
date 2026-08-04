package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.UpdateBusinessRequest;
import com.troquim_bot.repository.InMemoryBusinessRepository;
import com.troquim_bot.application.business.BusinessApplicationService;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private Business cadastrar(String nome, String telefone, String endereco) {
        Business business = new Business(BusinessId.generate(), nome, telefone, endereco);
        return businessRepository.save(business);
    }

    // ==================== GET /business ====================

    @Test
    void deveRetornar200QuandoBusinessExiste() throws Exception {
        cadastrar("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nome").value("Meu Salão"))
            .andExpect(jsonPath("$.telefone").value("(11) 99999-9999"))
            .andExpect(jsonPath("$.endereco").value("São Paulo - SP"))
            .andExpect(jsonPath("$.status").value("TRIAL"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.criadoEm").exists())
            .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    void deveRetornar404QuandoBusinessNaoExiste() throws Exception {
        assertFalse(businessApplicationService.existeBusiness());

        mockMvc.perform(get("/business"))
            .andExpect(status().isNotFound());

        // GET não cria negócio automaticamente: cadastro é responsabilidade explícita.
        assertFalse(businessApplicationService.existeBusiness());
    }

    // ==================== PUT /business ====================

    @Test
    void deveAtualizarNomePhoneEAddress() throws Exception {
        cadastrar("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        UpdateBusinessRequest request = new UpdateBusinessRequest();
        request.setName("Salão de Beleza Premium");
        request.setPhone("(11) 88888-8888");
        request.setAddress("Rio de Janeiro - RJ");

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
        cadastrar("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        mockMvc.perform(put("/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo Nome\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Novo Nome"))
            .andExpect(jsonPath("$.telefone").value("(11) 99999-9999")) // Não alterado
            .andExpect(jsonPath("$.endereco").value("São Paulo - SP")); // Não alterado
    }

    @Test
    void deveAtualizarApenasCamposFornecidos() throws Exception {
        cadastrar("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        mockMvc.perform(put("/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"(11) 77777-7777\"}"))
            .andExpect(status().isOk());

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
        cadastrar("Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        mockMvc.perform(put("/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Salão Completo\",\"phone\":\"(11) 66666-6666\",\"address\":\"Belo Horizonte - MG\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Salão Completo"))
            .andExpect(jsonPath("$.telefone").value("(11) 66666-6666"))
            .andExpect(jsonPath("$.endereco").value("Belo Horizonte - MG"));
    }

    @Test
    void deveManterBusinessExistenteAposGet() throws Exception {
        cadastrar("Salão Original", "(11) 11111-1111", "Curitiba - PR");

        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Salão Original"));

        assertEquals(1, businessRepository.findAll().size());

        mockMvc.perform(get("/business"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Salão Original"));

        assertEquals(1, businessRepository.findAll().size());
    }
}
