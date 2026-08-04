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

import java.util.UUID;

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

    private Business cadastrar(BusinessId id, String nome, String telefone, String endereco) {
        Business business = new Business(id, nome, telefone, endereco);
        return businessRepository.save(business);
    }

    private BusinessId novoId() {
        return BusinessId.from(UUID.randomUUID());
    }

    // ==================== GET /business/{businessId} ====================

    @Test
    void deveRetornar200QuandoBusinessExiste() throws Exception {
        BusinessId id = novoId();
        cadastrar(id, "Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        mockMvc.perform(get("/business/{id}", id.getValue()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nome").value("Meu Salão"))
            .andExpect(jsonPath("$.telefone").value("(11) 99999-9999"))
            .andExpect(jsonPath("$.endereco").value("São Paulo - SP"))
            .andExpect(jsonPath("$.status").value("TRIAL"))
            .andExpect(jsonPath("$.id").value(id.getValue().toString()))
            .andExpect(jsonPath("$.criadoEm").exists())
            .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    void deveRetornar404QuandoBusinessNaoExiste() throws Exception {
        BusinessId inexistente = novoId();

        mockMvc.perform(get("/business/{id}", inexistente.getValue()))
            .andExpect(status().isNotFound());
    }

    @Test
    void comDoisNegociosGetDevolveSomenteOSolicitado() throws Exception {
        BusinessId a = novoId();
        BusinessId b = novoId();
        cadastrar(a, "Negócio A", "(11) 11111-1111", "Endereço A");
        cadastrar(b, "Negócio B", "(11) 22222-2222", "Endereço B");

        mockMvc.perform(get("/business/{id}", a.getValue()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Negócio A"));

        mockMvc.perform(get("/business/{id}", b.getValue()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Negócio B"));
    }

    // ==================== PUT /business/{businessId} ====================

    @Test
    void deveAtualizarNomePhoneEAddress() throws Exception {
        BusinessId id = novoId();
        cadastrar(id, "Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        mockMvc.perform(put("/business/{id}", id.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Salão de Beleza Premium\",\"phone\":\"(11) 88888-8888\",\"address\":\"Rio de Janeiro - RJ\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nome").value("Salão de Beleza Premium"))
            .andExpect(jsonPath("$.telefone").value("(11) 88888-8888"))
            .andExpect(jsonPath("$.endereco").value("Rio de Janeiro - RJ"));
    }

    @Test
    void deveRetornar404AoAtualizarBusinessInexistente() throws Exception {
        mockMvc.perform(put("/business/{id}", novoId().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Não importa\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void putEmUmNegocioNaoModificaOOutro() throws Exception {
        BusinessId a = novoId();
        BusinessId b = novoId();
        cadastrar(a, "Negócio A", "(11) 11111-1111", "Endereço A");
        cadastrar(b, "Negócio B", "(11) 22222-2222", "Endereço B");

        mockMvc.perform(put("/business/{id}", a.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Negócio A Renomeado\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/business/{id}", a.getValue()))
            .andExpect(jsonPath("$.nome").value("Negócio A Renomeado"));

        // B continua intacto.
        mockMvc.perform(get("/business/{id}", b.getValue()))
            .andExpect(jsonPath("$.nome").value("Negócio B"))
            .andExpect(jsonPath("$.telefone").value("(11) 22222-2222"));
    }

    @Test
    void deveRetornar200AposAtualizacao() throws Exception {
        BusinessId id = novoId();
        cadastrar(id, "Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        mockMvc.perform(put("/business/{id}", id.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo Nome\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/business/{id}", id.getValue()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Novo Nome"))
            .andExpect(jsonPath("$.telefone").value("(11) 99999-9999")) // Não alterado
            .andExpect(jsonPath("$.endereco").value("São Paulo - SP")); // Não alterado
    }

    @Test
    void deveAtualizarApenasCamposFornecidos() throws Exception {
        BusinessId id = novoId();
        cadastrar(id, "Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        mockMvc.perform(put("/business/{id}", id.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"(11) 77777-7777\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/business/{id}", id.getValue()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Meu Salão")) // Não alterado
            .andExpect(jsonPath("$.telefone").value("(11) 77777-7777")) // Alterado
            .andExpect(jsonPath("$.endereco").value("São Paulo - SP")); // Não alterado
    }

    @Test
    void deveRetornarBadRequestQuandoRequestNull() throws Exception {
        mockMvc.perform(put("/business/{id}", novoId().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveAtualizarTodosOsCamposIndependentemente() throws Exception {
        BusinessId id = novoId();
        cadastrar(id, "Meu Salão", "(11) 99999-9999", "São Paulo - SP");

        mockMvc.perform(put("/business/{id}", id.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Salão Completo\",\"phone\":\"(11) 66666-6666\",\"address\":\"Belo Horizonte - MG\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/business/{id}", id.getValue()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Salão Completo"))
            .andExpect(jsonPath("$.telefone").value("(11) 66666-6666"))
            .andExpect(jsonPath("$.endereco").value("Belo Horizonte - MG"));
    }

    @Test
    void nomeTecnicoMigradoPodeSerAtualizadoExplicitamentePeloId() throws Exception {
        BusinessId id = novoId();
        cadastrar(id, "Negocio migrado " + id.getValue().toString().substring(0, 8), null, null);

        mockMvc.perform(put("/business/{id}", id.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Salão da Gizelle\",\"phone\":\"(11) 90000-0000\",\"address\":\"Rua Real, 1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Salão da Gizelle"));
    }
}
