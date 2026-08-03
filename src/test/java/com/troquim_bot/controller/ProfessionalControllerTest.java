package com.troquim_bot.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.troquim_bot.controller.dto.CreateProfessionalRequest;
import com.troquim_bot.controller.dto.UpdateProfessionalRequest;
import com.troquim_bot.repository.InMemoryProfessionalRepository;
import com.troquim_bot.application.professional.ProfessionalApplicationService;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.professional.ProfessionalId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.hasItems;

class ProfessionalControllerTest {

    private MockMvc mockMvc;
    private ProfessionalApplicationService professionalApplicationService;
    private InMemoryProfessionalRepository professionalRepository;

    @BeforeEach
    void setUp() {
        professionalRepository = new InMemoryProfessionalRepository();
        professionalApplicationService = new ProfessionalApplicationService(professionalRepository, TestTenants.pilot());
        ProfessionalController professionalController = new ProfessionalController(professionalApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(professionalController).build();
    }

    // ==================== GET /professionals ====================

    @Test
    void deveRetornar200QuandoBuscarTodosProfessionals() throws Exception {
        // Cria dois professionals
        Set<String> especialidades1 = new HashSet<>();
        especialidades1.add("Corte");
        especialidades1.add("Barba");
        professionalApplicationService.criarProfissional("João Silva", java.util.Set.of(), especialidades1, "(11) 11111-1111");

        Set<String> especialidades2 = new HashSet<>();
        especialidades2.add("Coloração");
        professionalApplicationService.criarProfissional("Maria Santos", java.util.Set.of(), especialidades2, "(11) 22222-2222");

        // Testa GET /professionals
        mockMvc.perform(get("/professionals"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].name", hasItems("João Silva", "Maria Santos")));
    }

    @Test
    void deveRetornar200EListaVaziaQuandoNaoExistemProfessionals() throws Exception {
        // Testa GET /professionals sem professionals
        mockMvc.perform(get("/professionals"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ==================== GET /professionals/{id} ====================

    @Test
    void deveRetornar200QuandoBuscarProfessionalPorId() throws Exception {
        // Cria um professional
        Set<String> especialidades = new HashSet<>();
        especialidades.add("Corte");
        especialidades.add("Barba");
        Professional professional = professionalApplicationService.criarProfissional("João Silva", java.util.Set.of(), especialidades, "(11) 11111-1111");

        // Testa GET /professionals/{id}
        mockMvc.perform(get("/professionals/" + professional.getId().getValue()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(professional.getId().getValue().toString()))
            .andExpect(jsonPath("$.name").value("João Silva"))
            .andExpect(jsonPath("$.phone").value("(11) 11111-1111"))
            .andExpect(jsonPath("$.status").value("ATIVO"))
            .andExpect(jsonPath("$.specialties").isArray())
            .andExpect(jsonPath("$.specialties.length()").value(2))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void deveRetornar404QuandoBuscarProfessionalInexistente() throws Exception {
        // Testa GET /professionals/{id} com ID inexistente
        mockMvc.perform(get("/professionals/12345678-1234-1234-1234-123456789012"))
            .andExpect(status().isNotFound());
    }

    // ==================== POST /professionals ====================

    @Test
    void deveCriarProfessionalComSucesso() throws Exception {
        Set<String> especialidades = new HashSet<>();
        especialidades.add("Corte");
        especialidades.add("Barba");

        // Testa POST /professionals
        mockMvc.perform(post("/professionals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"João Silva\",\"specialties\":[\"Corte\",\"Barba\"],\"phone\":\"(11) 11111-1111\"}"))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value("João Silva"))
            .andExpect(jsonPath("$.phone").value("(11) 11111-1111"))
            .andExpect(jsonPath("$.status").value("ATIVO"))
            .andExpect(jsonPath("$.specialties").isArray())
            .andExpect(jsonPath("$.specialties.length()").value(2))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void deveRetornar400QuandoRequestNull() throws Exception {
        mockMvc.perform(post("/professionals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoNomeVazio() throws Exception {
        mockMvc.perform(post("/professionals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"specialties\":[\"Corte\"],\"phone\":\"(11) 11111-1111\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoNomeNulo() throws Exception {
        mockMvc.perform(post("/professionals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialties\":[\"Corte\"],\"phone\":\"(11) 11111-1111\"}"))
            .andExpect(status().isBadRequest());
    }

    // MUDANÇA DELIBERADA DE CONTRATO: `especialidades` deixou de ser obrigatória.
    // Ela era exigida por ser o vínculo de fato entre profissional e serviço — papel que
    // agora pertence a `servicosHabilitados`, por ServiceId. Como `especialidades` virou
    // texto livre DESCRITIVO, exigi-la obrigaria a inventar conteúdo no provisionamento.
    // O que protege o agendamento não é este campo: é a habilitação explícita por ID,
    // coberta em CatalogoTenantScopeTest.

    @Test
    void deveAceitarEspecialidadesVaziasPoisNaoSaoVinculoDeServico() throws Exception {
        mockMvc.perform(post("/professionals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"João\",\"specialties\":[],\"phone\":\"(11) 11111-1111\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void deveAceitarEspecialidadesNulasPoisNaoSaoVinculoDeServico() throws Exception {
        mockMvc.perform(post("/professionals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"João\",\"phone\":\"(11) 11111-1111\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void profissionalCriadoNaoAtendeNenhumServicoAteSerHabilitado() throws Exception {
        mockMvc.perform(post("/professionals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"João\",\"specialties\":[\"Corte\"],\"phone\":\"(11) 11111-1111\"}"))
            .andExpect(status().isCreated());

        // Mesmo com "Corte" em especialidades, nenhum serviço fica habilitado: o texto
        // livre não cria vínculo. Sem habilitação por ServiceId, o profissional não é
        // ofertado para serviço nenhum.
        assertThat(professionalRepository.listarAtivos(TestTenants.PILOT))
            .isNotEmpty()
            .allSatisfy(p -> assertThat(p.getServicosHabilitados()).isEmpty());
    }

    @Test
    void deveRetornar400QuandoTelefoneVazio() throws Exception {
        mockMvc.perform(post("/professionals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"João\",\"specialties\":[\"Corte\"],\"phone\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoTelefoneNulo() throws Exception {
        mockMvc.perform(post("/professionals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"João\",\"specialties\":[\"Corte\"]}"))
            .andExpect(status().isBadRequest());
    }

    // ==================== PUT /professionals/{id} ====================

    @Test
    void deveAtualizarNomeEEspecialidadesETelefone() throws Exception {
        // Cria professional inicial
        Set<String> especialidades = new HashSet<>();
        especialidades.add("Corte");
        Professional professional = professionalApplicationService.criarProfissional("João Silva", java.util.Set.of(), especialidades, "(11) 11111-1111");

        // Atualiza todos os campos
        Set<String> novasEspecialidades = new HashSet<>();
        novasEspecialidades.add("Barba");
        novasEspecialidades.add("Coloração");

        mockMvc.perform(put("/professionals/" + professional.getId().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"João Santos\",\"specialties\":[\"Barba\",\"Coloração\"],\"phone\":\"(11) 99999-9999\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value("João Santos"))
            .andExpect(jsonPath("$.phone").value("(11) 99999-9999"))
            .andExpect(jsonPath("$.specialties").isArray())
            .andExpect(jsonPath("$.specialties.length()").value(2));
    }

    @Test
    void deveAtualizarApenasCamposFornecidos() throws Exception {
        // Cria professional inicial
        Set<String> especialidades = new HashSet<>();
        especialidades.add("Corte");
        Professional professional = professionalApplicationService.criarProfissional("João Silva", java.util.Set.of(), especialidades, "(11) 11111-1111");

        // Atualiza apenas o nome
        mockMvc.perform(put("/professionals/" + professional.getId().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"João Santos\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("João Santos"))
            .andExpect(jsonPath("$.phone").value("(11) 11111-1111")) // Não alterado
            .andExpect(jsonPath("$.specialties").isArray())
            .andExpect(jsonPath("$.specialties.length()").value(1)); // Não alterado
    }

    @Test
    void deveRetornar404AoAtualizarProfessionalInexistente() throws Exception {
        mockMvc.perform(put("/professionals/12345678-1234-1234-1234-123456789012")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"João\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400QuandoRequestNullNoUpdate() throws Exception {
        Set<String> especialidades = new HashSet<>();
        especialidades.add("Corte");
        Professional professional = professionalApplicationService.criarProfissional("João Silva", java.util.Set.of(), especialidades, "(11) 11111-1111");

        mockMvc.perform(put("/professionals/" + professional.getId().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest());
    }

    // ==================== DELETE /professionals/{id} ====================

    @Test
    void deveInativarProfessionalComSucesso() throws Exception {
        // Cria professional ativo
        Set<String> especialidades = new HashSet<>();
        especialidades.add("Corte");
        Professional professional = professionalApplicationService.criarProfissional("João Silva", java.util.Set.of(), especialidades, "(11) 11111-1111");

        // Testa DELETE /professionals/{id}
        mockMvc.perform(delete("/professionals/" + professional.getId().getValue()))
            .andExpect(status().isNoContent());

        // Verifica que foi inativado (status INATIVO)
        Professional inactivated = professionalApplicationService.buscarPorId(professional.getId()).orElseThrow();
        assertEquals(com.troquim_bot.professional.ProfessionalStatus.INATIVO, inactivated.getStatus());
    }

    @Test
    void deveRetornar404AoDeletarProfessionalInexistente() throws Exception {
        mockMvc.perform(delete("/professionals/12345678-1234-1234-1234-123456789012"))
            .andExpect(status().isNotFound());
    }

    // ==================== TESTES ADICIONAIS ====================

    @Test
    void deveCriarMultipleProfessionalsERetornarTodos() throws Exception {
        // Cria 3 professionals
        Set<String> esp1 = new HashSet<>();
        esp1.add("Corte");
        professionalApplicationService.criarProfissional("João", java.util.Set.of(), esp1, "(11) 11111-1111");

        Set<String> esp2 = new HashSet<>();
        esp2.add("Barba");
        professionalApplicationService.criarProfissional("Maria", java.util.Set.of(), esp2, "(11) 22222-2222");

        Set<String> esp3 = new HashSet<>();
        esp3.add("Coloração");
        professionalApplicationService.criarProfissional("Pedro", java.util.Set.of(), esp3, "(11) 33333-3333");

        // Verifica GET /professionals retorna todos
        mockMvc.perform(get("/professionals"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[*].name", hasItems("João", "Maria", "Pedro")));
    }

    @Test
    void deveManterProfessionalsAposCriacaoENaoDuplicar() throws Exception {
        // Cria professional
        Set<String> especialidades = new HashSet<>();
        especialidades.add("Corte");
        Professional professional = professionalApplicationService.criarProfissional("João Silva", java.util.Set.of(), especialidades, "(11) 11111-1111");

        // Faz GET /professionals
        mockMvc.perform(get("/professionals"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        // Faz outro GET
        mockMvc.perform(get("/professionals"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)); // Ainda 1, não duplicou
    }
}