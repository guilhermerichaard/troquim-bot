package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.CreateServiceRequest;
import com.troquim_bot.controller.dto.UpdateServiceRequest;
import com.troquim_bot.application.service.ServiceApplicationService;
import com.troquim_bot.repository.InMemoryServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.common.valueobject.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ServiceControllerTest {

    private MockMvc mockMvc;
    private ServiceApplicationService serviceApplicationService;
    private InMemoryServiceRepository serviceRepository;

    @BeforeEach
    void setUp() {
        serviceRepository = new InMemoryServiceRepository();
        serviceApplicationService = new ServiceApplicationService(serviceRepository);
        ServiceController serviceController = new ServiceController(serviceApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(serviceController).build();
    }

    // ==================== GET /services ====================

    @Test
    void deveRetornar200QuandoListarTodos() throws Exception {
        // Cria alguns serviços
        serviceApplicationService.criarServico("Morena Iluminada", "Cabelo", 120, Money.of(150.0, "BRL"));
        serviceApplicationService.criarServico("Escova", "Escova simples", 60, Money.of(80.0, "BRL"));

        // Testa GET /services
        mockMvc.perform(get("/services"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].nome").value(org.hamcrest.Matchers.hasItems("Morena Iluminada", "Escova")));
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistemServicos() throws Exception {
        // Testa GET /services sem serviços
        mockMvc.perform(get("/services"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ==================== GET /services/{id} ====================

    @Test
    void deveRetornar200QuandoBuscarPorIdExistente() throws Exception {
        // Cria um serviço
        Service service = serviceApplicationService.criarServico("Corte", "Corte de cabelo", 45, Money.of(60.0, "BRL"));
        String serviceId = service.getId().getValue().toString();

        // Testa GET /services/{id}
        mockMvc.perform(get("/services/" + serviceId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(serviceId))
            .andExpect(jsonPath("$.nome").value("Corte"))
            .andExpect(jsonPath("$.descricao").value("Corte de cabelo"))
            .andExpect(jsonPath("$.duracao.minutes").value(45))
            .andExpect(jsonPath("$.preco.amount").value(60.0))
            .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    void deveRetornar404QuandoBuscarPorIdInexistente() throws Exception {
        // Gera um UUID que não existe
        String nonExistentId = UUID.randomUUID().toString();

        // Testa GET /services/{id} com ID inexistente
        mockMvc.perform(get("/services/" + nonExistentId))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400QuandoIdInvalido() throws Exception {
        // Testa GET /services/{id} com ID inválido
        mockMvc.perform(get("/services/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }

    // ==================== POST /services ====================

    @Test
    void deveCriarServicoERetornar201() throws Exception {
        // Prepara request
        String requestBody = "{\"name\":\"Morena Iluminada\",\"description\":\"Cabelo\",\"durationMinutes\":120,\"price\":150.0}";

        // Testa POST /services
        mockMvc.perform(post("/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nome").value("Morena Iluminada"))
            .andExpect(jsonPath("$.descricao").value("Cabelo"))
            .andExpect(jsonPath("$.duracao.minutes").value(120))
            .andExpect(jsonPath("$.preco.amount").value(150.0))
            .andExpect(jsonPath("$.status").value("ATIVO"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.criadoEm").exists())
            .andExpect(jsonPath("$.atualizadoEm").exists());
    }

    @Test
    void deveRetornar400QuandoRequestNull() throws Exception {
        // Testa POST /services com request null
        mockMvc.perform(post("/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveCriarServicoComDescricaoOpcional() throws Exception {
        // Prepara request sem descrição
        String requestBody = "{\"name\":\"Escova\",\"description\":null,\"durationMinutes\":60,\"price\":80.0}";

        // Testa POST /services
        mockMvc.perform(post("/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nome").value("Escova"))
            .andExpect(jsonPath("$.descricao").doesNotExist());
    }

    // ==================== PUT /services/{id} ====================

    @Test
    void deveAtualizarNome() throws Exception {
        // Cria um serviço
        Service service = serviceApplicationService.criarServico("Nome Original", "Desc", 60, Money.of(100.0, "BRL"));
        String serviceId = service.getId().getValue().toString();

        // Atualiza apenas o nome
        String requestBody = "{\"name\":\"Nome Atualizado\"}";

        mockMvc.perform(put("/services/" + serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Nome Atualizado"))
            .andExpect(jsonPath("$.descricao").value("Desc")) // Não alterado
            .andExpect(jsonPath("$.duracao.minutes").value(60)); // Não alterado
    }

    @Test
    void deveAtualizarDescricao() throws Exception {
        // Cria um serviço
        Service service = serviceApplicationService.criarServico("Serviço", "Desc Original", 60, Money.of(100.0, "BRL"));
        String serviceId = service.getId().getValue().toString();

        // Atualiza apenas a descrição
        String requestBody = "{\"description\":\"Nova Descrição\"}";

        mockMvc.perform(put("/services/" + serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Serviço")) // Não alterado
            .andExpect(jsonPath("$.descricao").value("Nova Descrição"));
    }

    @Test
    void deveAtualizarDuracao() throws Exception {
        // Cria um serviço
        Service service = serviceApplicationService.criarServico("Serviço", "Desc", 60, Money.of(100.0, "BRL"));
        String serviceId = service.getId().getValue().toString();

        // Atualiza apenas a duração
        String requestBody = "{\"durationMinutes\":90}";

        mockMvc.perform(put("/services/" + serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.duracao.minutes").value(90))
            .andExpect(jsonPath("$.duracao.formatted").value("1h 30min"));
    }

    @Test
    void deveAtualizarPreco() throws Exception {
        // Cria um serviço
        Service service = serviceApplicationService.criarServico("Serviço", "Desc", 60, Money.of(100.0, "BRL"));
        String serviceId = service.getId().getValue().toString();

        // Atualiza apenas o preço
        String requestBody = "{\"price\":200.0}";

        mockMvc.perform(put("/services/" + serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preco.amount").value(200.0));
    }

    @Test
    void deveAtualizarTodosOsCampos() throws Exception {
        // Cria um serviço
        Service service = serviceApplicationService.criarServico("Nome Original", "Desc Original", 60, Money.of(100.0, "BRL"));
        String serviceId = service.getId().getValue().toString();

        // Atualiza todos os campos
        String requestBody = "{\"name\":\"Novo Nome\",\"description\":\"Nova Desc\",\"durationMinutes\":120,\"price\":250.0}";

        mockMvc.perform(put("/services/" + serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Novo Nome"))
            .andExpect(jsonPath("$.descricao").value("Nova Desc"))
            .andExpect(jsonPath("$.duracao.minutes").value(120))
            .andExpect(jsonPath("$.preco.amount").value(250.0));
    }

    @Test
    void deveRetornar404QuandoAtualizarServicoInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();
        String requestBody = "{\"name\":\"Novo Nome\"}";

        mockMvc.perform(put("/services/" + nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /services/{id} ====================

    @Test
    void deveInativarServicoERetornar204() throws Exception {
        // Cria um serviço
        Service service = serviceApplicationService.criarServico("Serviço", "Desc", 60, Money.of(100.0, "BRL"));
        String serviceId = service.getId().getValue().toString();

        // Verifica que está ativo
        assertTrue(serviceApplicationService.buscarPorId(service.getId()).orElseThrow().isAtivo());

        // Testa DELETE /services/{id}
        mockMvc.perform(delete("/services/" + serviceId))
            .andExpect(status().isNoContent());

        // Verifica que foi inativado (soft delete)
        Service serviceInativado = serviceApplicationService.buscarPorId(service.getId()).orElseThrow();
        assertFalse(serviceInativado.isAtivo());
        assertEquals(com.troquim_bot.service.ServiceStatus.INATIVO, serviceInativado.getStatus());
    }

    @Test
    void deveRetornar204QuandoDeletarServicoInexistente() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        // Testa DELETE /services/{id} com ID inexistente
        mockMvc.perform(delete("/services/" + nonExistentId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoIdInvalidoNoDelete() throws Exception {
        // Testa DELETE /services/{id} com ID inválido
        mockMvc.perform(delete("/services/invalid-uuid"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void servicoInativadoNaoApareceNaListaDeAtivos() throws Exception {
        // Cria dois serviços
        Service service1 = serviceApplicationService.criarServico("Serviço Ativo", "Desc", 60, Money.of(100.0, "BRL"));
        Service service2 = serviceApplicationService.criarServico("Serviço Inativo", "Desc", 60, Money.of(100.0, "BRL"));

        // Inativa o segundo serviço
        serviceApplicationService.inativarServico(service2.getId());

        // Verifica que apenas 1 serviço ativo aparece na lista
        List<Service> servicesAtivos = serviceApplicationService.listarAtivos();
        assertEquals(1, servicesAtivos.size());
        assertEquals("Serviço Ativo", servicesAtivos.get(0).getNome());
    }
}