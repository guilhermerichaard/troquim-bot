package com.troquim_bot.controller;

import com.troquim_bot.application.business.ConfigurarPerfilPublico;
import com.troquim_bot.application.business.ConsultarDisponibilidadePublica;
import com.troquim_bot.application.business.ConsultarVitrinePublica;
import com.troquim_bot.application.business.PublicarPerfilPublico;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.support.CatalogoDeTeste;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * API pública SOMENTE LEITURA — vitrine e disponibilidade, resolvidas pelo slug.
 *
 * Roda contra o H2 real do profile de teste (Hibernate gera o schema das entidades JPA), com
 * a cadeia de segurança real ativa — o mesmo padrão de {@code SecurityConfigTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API pública - vitrine e disponibilidade por slug")
class PublicBusinessControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ProvisionarNegocio provisionarNegocio;
    @Autowired
    private ConfigurarPerfilPublico configurarPerfilPublico;
    @Autowired
    private PublicarPerfilPublico publicarPerfilPublico;
    @Autowired
    private ConsultarCatalogo consultarCatalogo;
    @Autowired
    private AppointmentRepository appointmentRepository;

    private static IntervaloDeHorario periodo(int hIni, int mIni, int hFim, int mFim) {
        return IntervaloDeHorario.de(LocalTime.of(hIni, mIni), LocalTime.of(hFim, mFim));
    }

    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    private BusinessId novoNegocioAtivoComCatalogo(String slug) {
        BusinessId id = BusinessId.from(UUID.randomUUID());
        businessRepository.save(new Business(id, "Negócio " + slug, null, null));
        CatalogoDeTeste.provisionar(provisionarNegocio, businessRepository, id);
        configurarPerfilPublico.configurar(id, slug, "Salão " + slug, "Descrição de " + slug,
                "+5511999990000", "Rua Pública, 1");
        publicarPerfilPublico.publicar(id);
        return id;
    }

    // ==================== VITRINE ====================

    @Test
    @DisplayName("1. perfil PUBLISHED e Business ativo retorna 200 com o catálogo")
    void perfilPublicadoRetorna200() throws Exception {
        novoNegocioAtivoComCatalogo("salao-1-" + UUID.randomUUID().toString().substring(0, 8));
        String slug = "salao-fixo-" + UUID.randomUUID().toString().substring(0, 8);
        novoNegocioAtivoComCatalogo(slug);

        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.catalogConfigured").value(true))
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.services", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    @DisplayName("5. resposta pública não contém BusinessId, status interno, PublicationStatus ou timestamps")
    void respostaNaoExpoeDadosInternos() throws Exception {
        String slug = "sem-dado-interno-" + UUID.randomUUID().toString().substring(0, 8);
        novoNegocioAtivoComCatalogo(slug);

        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.publicationStatus").doesNotExist())
                .andExpect(jsonPath("$.criadoEm").doesNotExist())
                .andExpect(jsonPath("$.atualizadoEm").doesNotExist());
    }

    @Test
    @DisplayName("7 e 8. serviço e profissional usam IDs reais do catálogo, preço ausente permanece null")
    void catalogoUsaIdsReaisEPrecoAusentePermaneceNull() throws Exception {
        String slug = "ids-reais-" + UUID.randomUUID().toString().substring(0, 8);
        BusinessId id = novoNegocioAtivoComCatalogo(slug);

        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);

        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[?(@.name=='" + CatalogoDeTeste.UNHAS + "')].id")
                        .value(org.hamcrest.Matchers.contains(item.id().getValue().toString())))
                .andExpect(jsonPath("$.services[?(@.name=='" + CatalogoDeTeste.UNHAS + "')].price")
                        .value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.services[0].professionals[0].id")
                        .value(item.profissionais().get(0).id().getValue().toString()))
                .andExpect(jsonPath("$.services[0].professionals[0].name").value(CatalogoDeTeste.PROFISSIONAL));
    }

    @Test
    @DisplayName("9. dois slugs devolvem somente seus próprios catálogos")
    void doisSlugsCatalogosIsolados() throws Exception {
        String slugA = "negocio-a-" + UUID.randomUUID().toString().substring(0, 8);
        String slugB = "negocio-b-" + UUID.randomUUID().toString().substring(0, 8);
        novoNegocioAtivoComCatalogo(slugA);
        novoNegocioAtivoComCatalogo(slugB);

        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slugA))
                .andExpect(jsonPath("$.name").value("Salão " + slugA));

        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slugB))
                .andExpect(jsonPath("$.name").value("Salão " + slugB));
    }

    @Test
    @DisplayName("2. perfil em DRAFT retorna 404")
    void perfilDraftRetorna404() throws Exception {
        BusinessId id = BusinessId.from(UUID.randomUUID());
        String slug = "ainda-rascunho-" + UUID.randomUUID().toString().substring(0, 8);
        businessRepository.save(new Business(id, "Negócio Rascunho", null, null));
        configurarPerfilPublico.configurar(id, slug, "Negócio Rascunho", null, null, null);
        // Nunca publicado.

        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = com.troquim_bot.business.BusinessStatus.class, names = {"INATIVO", "SUSPENSO", "DELETADO"})
    @DisplayName("3. negócio inativo, suspenso ou deletado retorna 404, mesmo com perfil PUBLISHED")
    void negocioNaoAtivoRetorna404(com.troquim_bot.business.BusinessStatus status) throws Exception {
        BusinessId id = BusinessId.from(UUID.randomUUID());
        String slug = "fechado-" + status.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 6);
        businessRepository.save(new Business(id, "Negócio Fechado", null, null));
        configurarPerfilPublico.configurar(id, slug, "Negócio Fechado", null, null, null);
        publicarPerfilPublico.publicar(id);

        Business negocio = businessRepository.findById(id);
        switch (status) {
            case INATIVO -> negocio.desativar();
            case SUSPENSO -> negocio.suspender();
            case DELETADO -> negocio.deletar();
            default -> throw new IllegalStateException();
        }
        businessRepository.save(negocio);

        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("4. slug inexistente ou inválido retorna 404")
    void slugInexistenteOuInvalidoRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/public/businesses/nunca-existiu-xyz"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/businesses/AB"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/businesses/admin"))
                .andExpect(status().isNotFound());
    }

    // ==================== DISPONIBILIDADE ====================

    @Test
    @DisplayName("11. o intervalo de almoço some da disponibilidade pública")
    void disponibilidadeRespeitaAlmoco() throws Exception {
        String slug = "com-almoco-" + UUID.randomUUID().toString().substring(0, 8);
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        LocalDate quarta = proximaQuarta();

        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slug)
                        .param("serviceId", item.id().getValue().toString())
                        .param("professionalId", item.profissionais().get(0).id().getValue().toString())
                        .param("date", quarta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condition").value("AVAILABLE"))
                .andExpect(jsonPath("$.times").value(org.hamcrest.Matchers.hasItem("11:00")))
                .andExpect(jsonPath("$.times").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("12:00"))))
                .andExpect(jsonPath("$.times").value(org.hamcrest.Matchers.hasItem("13:00")));
    }

    @Test
    @DisplayName("10, 12. disponibilidade vem de ConsultarDisponibilidade, respeitando a duração real do serviço")
    void disponibilidadeRespeitaDuracaoReal() throws Exception {
        BusinessId id = BusinessId.from(UUID.randomUUID());
        String slug = "duracao-real-" + UUID.randomUUID().toString().substring(0, 8);
        businessRepository.save(new Business(id, "Negócio Duração", null, null));

        provisionarNegocio.provisionar(id,
                List.of(new ProvisionarNegocio.ServicoDesejado("Curto", 30),
                        new ProvisionarNegocio.ServicoDesejado("Longo", 180)),
                new ProvisionarNegocio.ProfissionalDesejado("Profissional", "+5511900000001",
                        List.of("Curto", "Longo"),
                        Map.of(DiaSemana.QUARTA, List.of(periodo(9, 0, 12, 0)))),
                BusinessHours.deSemana(Map.of(DiaSemana.QUARTA, List.of(periodo(9, 0, 12, 0)))));
        configurarPerfilPublico.configurar(id, slug, "Negócio Duração", null, null, null);
        publicarPerfilPublico.publicar(id);

        var curto = CatalogoDeTeste.item(consultarCatalogo, id, "Curto");
        var longo = CatalogoDeTeste.item(consultarCatalogo, id, "Longo");
        LocalDate quarta = proximaQuarta();
        ProfessionalId profissionalId = curto.profissionais().get(0).id();

        // 30min no período 09:00-12:00 vai até 11:30; a lista tem várias opções.
        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slug)
                        .param("serviceId", curto.id().getValue().toString())
                        .param("professionalId", profissionalId.getValue().toString())
                        .param("date", quarta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.times").value(org.hamcrest.Matchers.hasItem("11:30")));

        // 3h só cabe começando às 09:00 — a duração REAL do serviço restringe a lista.
        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slug)
                        .param("serviceId", longo.id().getValue().toString())
                        .param("professionalId", profissionalId.getValue().toString())
                        .param("date", quarta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.times", org.hamcrest.Matchers.contains("09:00")));
    }

    @Test
    @DisplayName("13. appointment ocupado continua removendo o horário da disponibilidade pública")
    void appointmentOcupadoRemoveHorario() throws Exception {
        String slug = "com-agendamento-" + UUID.randomUUID().toString().substring(0, 8);
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        LocalDate quarta = proximaQuarta();
        ProfessionalId profissionalId = item.profissionais().get(0).id();

        appointmentRepository.save(new Appointment(AppointmentId.generate(), id,
                CustomerId.from(UUID.randomUUID()), profissionalId, item.id(),
                com.troquim_bot.availability.AvailabilityId.generate(), quarta,
                LocalTime.of(9, 0), LocalTime.of(10, 0)));

        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slug)
                        .param("serviceId", item.id().getValue().toString())
                        .param("professionalId", profissionalId.getValue().toString())
                        .param("date", quarta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.times").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("09:00"))));
    }

    @Test
    @DisplayName("14. ids de outro negócio não vazam informação: mesma forma de resposta, nunca 404")
    void idsDeOutroTenantNaoVazam() throws Exception {
        String slugA = "tenant-a-" + UUID.randomUUID().toString().substring(0, 8);
        String slugB = "tenant-b-" + UUID.randomUUID().toString().substring(0, 8);
        BusinessId a = novoNegocioAtivoComCatalogo(slugA);
        novoNegocioAtivoComCatalogo(slugB);

        var itemDeA = CatalogoDeTeste.item(consultarCatalogo, a, CatalogoDeTeste.UNHAS);
        LocalDate quarta = proximaQuarta();

        // Consulta o slug de B usando o serviceId/professionalId de A.
        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slugB)
                        .param("serviceId", itemDeA.id().getValue().toString())
                        .param("professionalId", itemDeA.profissionais().get(0).id().getValue().toString())
                        .param("date", quarta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condition").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.times").isArray())
                .andExpect(jsonPath("$.times").isEmpty());

        // Um UUID totalmente aleatório produz a MESMA forma de resposta — nada distingue
        // "existe em outro tenant" de "nunca existiu".
        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slugB)
                        .param("serviceId", UUID.randomUUID().toString())
                        .param("professionalId", UUID.randomUUID().toString())
                        .param("date", quarta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condition").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("4b. disponibilidade com slug inexistente retorna 404")
    void disponibilidadeSlugInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/public/businesses/nunca-existiu-xyz/availability")
                        .param("serviceId", UUID.randomUUID().toString())
                        .param("professionalId", UUID.randomUUID().toString())
                        .param("date", proximaQuarta().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("15. UUID ou data malformados retornam 400")
    void parametrosMalformadosRetornam400() throws Exception {
        String slug = "params-invalidos-" + UUID.randomUUID().toString().substring(0, 8);
        novoNegocioAtivoComCatalogo(slug);

        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slug)
                        .param("serviceId", "nao-e-um-uuid")
                        .param("professionalId", UUID.randomUUID().toString())
                        .param("date", proximaQuarta().toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slug)
                        .param("serviceId", UUID.randomUUID().toString())
                        .param("professionalId", UUID.randomUUID().toString())
                        .param("date", "31-12-2026"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/public/businesses/{slug}/availability", slug)
                        .param("serviceId", UUID.randomUUID().toString())
                        .param("professionalId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    // ==================== SEGURANÇA ====================

    @Test
    @DisplayName("16. GET público funciona sem qualquer credencial")
    void getPublicoFuncionaSemAutenticacao() throws Exception {
        String slug = "sem-credencial-" + UUID.randomUUID().toString().substring(0, 8);
        novoNegocioAtivoComCatalogo(slug);

        mockMvc.perform(get("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("17. POST, PUT, PATCH e DELETE na mesma rota pública continuam bloqueados")
    void metodosDeEscritaContinuamBloqueados() throws Exception {
        String slug = "bloqueio-escrita-" + UUID.randomUUID().toString().substring(0, 8);
        novoNegocioAtivoComCatalogo(slug);

        mockMvc.perform(post("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("18. rotas administrativas vizinhas continuam exigindo ADMIN")
    void rotasVizinhasContinuamBloqueadas() throws Exception {
        mockMvc.perform(get("/business/11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/services"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/whatsapp/connect"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("19. o Controller público não injeta nenhum Repository — só Application")
    void controllerNaoAcessaRepositoryDiretamente() {
        Constructor<?>[] construtores = PublicBusinessController.class.getDeclaredConstructors();
        assertEquals(1, construtores.length);

        for (Class<?> parametro : construtores[0].getParameterTypes()) {
            assertThat(parametro.getSimpleName())
                    .as("parâmetro '%s' do Controller público não pode ser um Repository", parametro.getSimpleName())
                    .doesNotContain("Repository");
        }
    }
}
