package com.troquim_bot.controller;

import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingIdempotencyStore;
import com.troquim_bot.application.business.ConfigurarPerfilPublico;
import com.troquim_bot.application.business.PublicarPerfilPublico;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.repository.CustomerRepository;
import com.troquim_bot.repository.ReservationRepository;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.support.CatalogoDeTeste;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API pública de AGENDAMENTO — único POST público, idempotente pelo cabeçalho
 * {@code Idempotency-Key}, reusando o caminho canônico de confirmação ponta a ponta.
 *
 * Roda contra o H2 real do profile de teste (o mesmo padrão de
 * {@code PublicBusinessControllerTest}). O cenário de falha técnica simulada vive em
 * classe separada ({@code PublicBookingControllerFailureTest}), que decora um Repository
 * real — não pode compartilhar contexto Spring com esta classe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API pública - agendamento idempotente por slug")
class PublicBookingControllerTest {

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
    private CustomerRepository customerRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ServiceRepository serviceRepository;
    @Autowired
    private BookingIdempotencyStore idempotencyStore;

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

    private String novoSlug(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String corpo(String serviceId, String professionalId, String date, String time,
                                String nome, String telefone) {
        return """
                {"serviceId":"%s","professionalId":"%s","date":"%s","time":"%s",\
                "customerName":"%s","customerPhone":"%s"}""".formatted(
                serviceId, professionalId, date, time, nome, telefone);
    }

    private String idempotencyKey() {
        return "test-key-" + UUID.randomUUID();
    }

    // ==================== caminho feliz ====================

    @Test
    @DisplayName("1. perfil publicado + negócio ativo + corpo válido -> 201")
    void perfilPublicadoRetorna201() throws Exception {
        String slug = novoSlug("feliz");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(),
                                item.profissionais().get(0).id().getValue().toString(),
                                data.toString(), "10:00", "Maria Silva", "+5511999990001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.serviceId").value(item.id().getValue().toString()))
                .andExpect(jsonPath("$.professionalId")
                        .value(item.profissionais().get(0).id().getValue().toString()))
                .andExpect(jsonPath("$.date").value(data.toString()))
                .andExpect(jsonPath("$.time").value("10:00"));
    }

    @Test
    @DisplayName("2 e 3. cria exatamente 1 Customer/Reservation/Appointment, no BusinessId do slug")
    void criaExatamenteUmAgendamentoNoTenantDoSlug() throws Exception {
        String slug = novoSlug("contagem");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(),
                                item.profissionais().get(0).id().getValue().toString(),
                                data.toString(), "10:00", "Bia Lima", "+5511999990002")))
                .andExpect(status().isCreated());

        assertThat(customerRepository.findByBusinessId(id)).hasSize(1);
        assertThat(appointmentRepository.findByBusinessId(id)).hasSize(1);
        assertThat(reservationRepository.findByBusinessId(id)).hasSize(1);

        Appointment criado = appointmentRepository.findByBusinessId(id).get(0);
        assertThat(criado.getBusinessId()).isEqualTo(id);
        Customer cliente = customerRepository.findByBusinessId(id).get(0);
        assertThat(cliente.getBusinessId()).isEqualTo(id);
    }

    @Test
    @DisplayName("4. duração persistida vem do catálogo, não do cliente (que nem a envia)")
    void duracaoVemDoCatalogo() throws Exception {
        String slug = novoSlug("duracao");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(),
                                item.profissionais().get(0).id().getValue().toString(),
                                data.toString(), "10:00", "Ana Souza", "+5511999990003")))
                .andExpect(status().isCreated());

        Appointment criado = appointmentRepository.findByBusinessId(id).get(0);
        long minutos = java.time.Duration.between(criado.getStartTime(), criado.getEndTime()).toMinutes();
        assertThat(minutos).isEqualTo(60); // CatalogoDeTeste provisiona 60 minutos
    }

    // ==================== idempotência ====================

    @Test
    @DisplayName("5. mesma Idempotency-Key + payload idêntico -> 201 de novo, sem duplicar")
    void mesmaChaveMesmoPayloadNaoDuplica() throws Exception {
        String slug = novoSlug("retry");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();
        String chave = idempotencyKey();
        String corpo = corpo(item.id().getValue().toString(),
                item.profissionais().get(0).id().getValue().toString(),
                data.toString(), "10:00", "Cliente Retry", "+5511999990004");

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(appointmentRepository.findByBusinessId(id)).hasSize(1);
    }

    @Test
    @DisplayName("6. mesma Idempotency-Key + payload diferente -> 409 IDEMPOTENCY_KEY_REUSED")
    void mesmaChavePayloadDiferenteReusada() throws Exception {
        String slug = novoSlug("reuso");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();
        String chave = idempotencyKey();

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(),
                                item.profissionais().get(0).id().getValue().toString(),
                                data.toString(), "10:00", "Cliente A", "+5511999990005")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(),
                                item.profissionais().get(0).id().getValue().toString(),
                                data.toString(), "11:00", "Cliente A", "+5511999990005")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(appointmentRepository.findByBusinessId(id)).hasSize(1);
    }

    @Test
    @DisplayName("7. mesma Idempotency-Key reusada após 1a tentativa terminar em SLOT_UNAVAILABLE -> 409")
    void chaveReusadaAposConflitoDeHorario() throws Exception {
        String slug = novoSlug("reuso-conflito");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        ProfessionalId profissional = item.profissionais().get(0).id();
        LocalDate data = proximaQuarta();

        // Ocupa o horário 10:00 com OUTRA chave, então a 1a tentativa da chave sob teste
        // nasce em conflito (SLOT_UNAVAILABLE), sem nunca criar Appointment.
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(), profissional.getValue().toString(),
                                data.toString(), "10:00", "Ocupante", "+5511999990006")))
                .andExpect(status().isCreated());

        String chave = idempotencyKey();
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(), profissional.getValue().toString(),
                                data.toString(), "10:00", "Cliente Conflito", "+5511999990007")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

        // 2a tentativa, MESMA chave, payload diferente (outro horário) -> reuso, não retry.
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(), profissional.getValue().toString(),
                                data.toString(), "11:00", "Cliente Conflito", "+5511999990007")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("8. a mesma Idempotency-Key em dois negócios diferentes é independente")
    void mesmaChaveEmDoisNegociosEIndependente() throws Exception {
        String slugA = novoSlug("tenant-a");
        String slugB = novoSlug("tenant-b");
        BusinessId a = novoNegocioAtivoComCatalogo(slugA);
        BusinessId b = novoNegocioAtivoComCatalogo(slugB);
        var itemA = CatalogoDeTeste.item(consultarCatalogo, a, CatalogoDeTeste.UNHAS);
        var itemB = CatalogoDeTeste.item(consultarCatalogo, b, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();
        String chave = idempotencyKey();

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slugA)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(itemA.id().getValue().toString(),
                                itemA.profissionais().get(0).id().getValue().toString(),
                                data.toString(), "10:00", "Cliente A", "+5511999990008")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slugB)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(itemB.id().getValue().toString(),
                                itemB.profissionais().get(0).id().getValue().toString(),
                                data.toString(), "10:00", "Cliente B", "+5511999990009")))
                .andExpect(status().isCreated());

        assertThat(appointmentRepository.findByBusinessId(a)).hasSize(1);
        assertThat(appointmentRepository.findByBusinessId(b)).hasSize(1);
    }

    // ==================== catálogo ====================

    @Test
    @DisplayName("10. serviço/profissional de outro tenant -> 422, resposta idêntica à de inexistente")
    void selecaoDeOutroTenantEInexistenteSaoIndistinguiveis() throws Exception {
        String slugA = novoSlug("intruso-a");
        String slugB = novoSlug("intruso-b");
        BusinessId a = novoNegocioAtivoComCatalogo(slugA);
        novoNegocioAtivoComCatalogo(slugB);
        var itemA = CatalogoDeTeste.item(consultarCatalogo, a, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();

        var respostaOutroTenant = mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slugB)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(itemA.id().getValue().toString(),
                                itemA.profissionais().get(0).id().getValue().toString(),
                                data.toString(), "10:00", "Cliente", "+5511999990010")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELECTION_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        var respostaInexistente = mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slugB)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                data.toString(), "10:00", "Cliente", "+5511999990010")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELECTION_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(respostaOutroTenant, respostaInexistente,
                "Serviço de outro tenant e serviço inexistente precisam parecer idênticos de fora");
    }

    // ==================== idempotência após recusa de catálogo ====================

    private static String recibo(BusinessId businessId, String idempotencyKey, String servicoId,
                                 String profissionalId, LocalDate data, String horario, String telefone) {
        return BookingCommandKey.deChaveExclusiva(businessId, idempotencyKey,
                new PhoneNumber(telefone).getE164(),
                com.troquim_bot.service.ServiceId.from(UUID.fromString(servicoId)),
                ProfessionalId.from(UUID.fromString(profissionalId)),
                data, java.time.LocalTime.parse(horario)).valor();
    }

    @Test
    @DisplayName("mesma chave + mesma seleção inválida -> dois 422, sem escrita, um único recibo")
    void mesmaChaveMesmaSelecaoInvalidaRegistraUmUnicoRecibo() throws Exception {
        String slugA = novoSlug("selecao-invalida-a");
        String slugB = novoSlug("selecao-invalida-b");
        BusinessId a = novoNegocioAtivoComCatalogo(slugA);
        BusinessId b = novoNegocioAtivoComCatalogo(slugB);
        var itemA = CatalogoDeTeste.item(consultarCatalogo, a, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();
        String chave = idempotencyKey();
        String telefone = "+5511999990020";
        String servicoDeOutroTenant = itemA.id().getValue().toString();
        String profissionalDeOutroTenant = itemA.profissionais().get(0).id().getValue().toString();

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slugB)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(servicoDeOutroTenant, profissionalDeOutroTenant,
                                data.toString(), "10:00", "Cliente", telefone)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELECTION_UNAVAILABLE"));

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slugB)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(servicoDeOutroTenant, profissionalDeOutroTenant,
                                data.toString(), "10:00", "Cliente", telefone)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELECTION_UNAVAILABLE"));

        assertThat(customerRepository.findByBusinessId(b)).isEmpty();
        assertThat(appointmentRepository.findByBusinessId(b)).isEmpty();
        assertThat(reservationRepository.findByBusinessId(b)).isEmpty();

        String chaveRecibo = recibo(b, chave, servicoDeOutroTenant, profissionalDeOutroTenant,
                data, "10:00", telefone);
        assertThat(idempotencyStore.buscar(chaveRecibo)).isPresent();
    }

    @Test
    @DisplayName("mesma chave + payload diferente após 1o 422 -> 409, sem escrita de negócio")
    void mesmaChavePayloadDiferenteAposSelecaoInvalidaEReusada() throws Exception {
        String slug = novoSlug("selecao-invalida-reuso");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        LocalDate data = proximaQuarta();
        String chave = idempotencyKey();

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                data.toString(), "10:00", "Cliente", "+5511999990021")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELECTION_UNAVAILABLE"));

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                data.toString(), "11:00", "Cliente", "+5511999990021")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(customerRepository.findByBusinessId(id)).isEmpty();
        assertThat(appointmentRepository.findByBusinessId(id)).isEmpty();
        assertThat(reservationRepository.findByBusinessId(id)).isEmpty();
    }

    @Test
    @DisplayName("1a resposta 422, catálogo corrigido depois: mesma chave/payload continua 422; "
            + "nova chave segue o catálogo atualizado")
    void chaveImutavelAposSelecaoInvalidaMasNovaChaveSegueCatalogoAtualizado() throws Exception {
        String slug = novoSlug("catalogo-corrigido");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();
        String servicoId = item.id().getValue().toString();
        String profissionalId = item.profissionais().get(0).id().getValue().toString();

        // Serviço temporariamente INATIVO: a 1a tentativa nasce em SELECTION_UNAVAILABLE.
        com.troquim_bot.service.Service servico = serviceRepository.buscarPorId(id, item.id()).orElseThrow();
        servico.desativar();
        serviceRepository.salvar(servico);

        String chave = idempotencyKey();
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(servicoId, profissionalId, data.toString(), "10:00",
                                "Cliente Catalogo", "+5511999990022")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELECTION_UNAVAILABLE"));

        // Catálogo corrigido: o serviço volta a ficar ativo.
        com.troquim_bot.service.Service servicoReativado =
                serviceRepository.buscarPorId(id, item.id()).orElseThrow();
        servicoReativado.ativar();
        serviceRepository.salvar(servicoReativado);

        // MESMA chave, MESMO payload: o recibo é imutável — continua 422, mesmo com o
        // catálogo já corrigido.
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(servicoId, profissionalId, data.toString(), "10:00",
                                "Cliente Catalogo", "+5511999990022")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELECTION_UNAVAILABLE"));

        // NOVA chave, mesmo payload: segue o catálogo ATUAL e confirma.
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(servicoId, profissionalId, data.toString(), "10:00",
                                "Cliente Catalogo", "+5511999990022")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(appointmentRepository.findByBusinessId(id)).hasSize(1);
    }

    @Test
    @DisplayName("a mesma Idempotency-Key com seleção inválida em dois negócios é independente")
    void mesmaChaveComSelecaoInvalidaEmDoisNegociosEIndependente() throws Exception {
        String slugA = novoSlug("selecao-invalida-tenant-a");
        String slugB = novoSlug("selecao-invalida-tenant-b");
        novoNegocioAtivoComCatalogo(slugA);
        novoNegocioAtivoComCatalogo(slugB);
        LocalDate data = proximaQuarta();
        String chave = idempotencyKey();

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slugA)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                data.toString(), "10:00", "Cliente A", "+5511999990023")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELECTION_UNAVAILABLE"));

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slugB)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                data.toString(), "10:00", "Cliente B", "+5511999990023")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELECTION_UNAVAILABLE"));
    }

    @Test
    @DisplayName("retry idêntico após SLOT_UNAVAILABLE continua funcionando (mesma resposta, sem duplicar)")
    void retryIdenticoAposSlotIndisponivelContinuaFuncionando() throws Exception {
        String slug = novoSlug("retry-conflito");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        ProfessionalId profissional = item.profissionais().get(0).id();
        LocalDate data = proximaQuarta();

        // Ocupa o horário com OUTRA chave.
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(), profissional.getValue().toString(),
                                data.toString(), "10:00", "Ocupante", "+5511999990024")))
                .andExpect(status().isCreated());

        String chave = idempotencyKey();
        String corpoConflito = corpo(item.id().getValue().toString(), profissional.getValue().toString(),
                data.toString(), "10:00", "Cliente Retry Conflito", "+5511999990025");

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoConflito))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

        // Retry IDÊNTICO (mesma chave, mesmo payload): mesma resposta, sem duplicar nem falhar.
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoConflito))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

        assertThat(appointmentRepository.findByBusinessId(id)).hasSize(1);
    }

    // ==================== slug ====================

    @Test
    @DisplayName("11. slug DRAFT e negócio inativo -> 404, sem escrever nada")
    void slugDraftOuInativoRetorna404SemEscrever() throws Exception {
        BusinessId id = BusinessId.from(UUID.randomUUID());
        String slug = novoSlug("rascunho");
        businessRepository.save(new Business(id, "Negócio Rascunho", null, null));
        // Nunca publicado (DRAFT).

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                proximaQuarta().toString(), "10:00", "Cliente", "+5511999990011")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"));

        assertThat(customerRepository.findByBusinessId(id)).isEmpty();
        assertThat(appointmentRepository.findByBusinessId(id)).isEmpty();
        assertThat(reservationRepository.findByBusinessId(id)).isEmpty();

        // Negócio publicado, porém inativo.
        BusinessId id2 = BusinessId.from(UUID.randomUUID());
        String slug2 = novoSlug("inativo");
        businessRepository.save(new Business(id2, "Negócio Inativo", null, null));
        configurarPerfilPublico.configurar(id2, slug2, "Negócio Inativo", null, null, null);
        publicarPerfilPublico.publicar(id2);
        Business negocio = businessRepository.findById(id2);
        negocio.desativar();
        businessRepository.save(negocio);

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug2)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                proximaQuarta().toString(), "10:00", "Cliente", "+5511999990012")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"));

        assertThat(appointmentRepository.findByBusinessId(id2)).isEmpty();
    }

    // ==================== validação HTTP ====================

    @Test
    @DisplayName("12. Idempotency-Key ausente ou inválida -> 400 INVALID_REQUEST")
    void idempotencyKeyAusenteOuInvalidaRetorna400() throws Exception {
        String slug = novoSlug("sem-chave");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);
        String corpoValido = corpo(item.id().getValue().toString(),
                item.profissionais().get(0).id().getValue().toString(),
                proximaQuarta().toString(), "10:00", "Cliente", "+5511999990013");

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoValido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", "chave com espaço e acento çãé")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoValido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", "x".repeat(81))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoValido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(appointmentRepository.findByBusinessId(id)).isEmpty();
    }

    @Test
    @DisplayName("13. UUID/data/horário/nome/telefone inválidos -> 400 INVALID_REQUEST")
    void camposInvalidosRetornam400() throws Exception {
        String slug = novoSlug("invalido");
        novoNegocioAtivoComCatalogo(slug);

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("nao-e-um-uuid", UUID.randomUUID().toString(),
                                proximaQuarta().toString(), "10:00", "Cliente", "+5511999990014")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                "31-12-2026", "10:00", "Cliente", "+5511999990014")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                proximaQuarta().toString(), "25:99", "Cliente", "+5511999990014")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                proximaQuarta().toString(), "10:00", "", "+5511999990014")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                proximaQuarta().toString(), "10:00", "Cliente", "telefone-invalido")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("corpo JSON malformado -> 400 INVALID_REQUEST, nunca 500")
    void corpoMalformadoRetorna400() throws Exception {
        String slug = novoSlug("json-quebrado");
        novoNegocioAtivoComCatalogo(slug);

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ isso nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("14. businessId forjado no corpo é ignorado — tenant e duração seguem do slug/catálogo")
    void businessIdForjadoNoCorpoEIgnorado() throws Exception {
        String slugReal = novoSlug("real");
        String slugForjado = novoSlug("forjado");
        BusinessId real = novoNegocioAtivoComCatalogo(slugReal);
        BusinessId forjado = novoNegocioAtivoComCatalogo(slugForjado);
        var item = CatalogoDeTeste.item(consultarCatalogo, real, CatalogoDeTeste.UNHAS);
        LocalDate data = proximaQuarta();

        String corpoComBusinessIdForjado = """
                {"businessId":"%s","serviceId":"%s","professionalId":"%s","date":"%s","time":"%s",\
                "customerName":"Cliente Forjado","customerPhone":"+5511999990015"}""".formatted(
                forjado.getValue(), item.id().getValue(),
                item.profissionais().get(0).id().getValue(), data, "10:00");

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slugReal)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComBusinessIdForjado))
                .andExpect(status().isCreated());

        // O agendamento foi criado no tenant do SLUG (real), nunca no forjado.
        assertThat(appointmentRepository.findByBusinessId(real)).hasSize(1);
        assertThat(appointmentRepository.findByBusinessId(forjado)).isEmpty();
        long minutos = java.time.Duration.between(
                appointmentRepository.findByBusinessId(real).get(0).getStartTime(),
                appointmentRepository.findByBusinessId(real).get(0).getEndTime()).toMinutes();
        assertThat(minutos).isEqualTo(60);
    }

    // ==================== segurança ====================

    @Test
    @DisplayName("17. a rota funciona sem qualquer credencial")
    void rotaFuncionaSemCredencial() throws Exception {
        String slug = novoSlug("sem-credencial");
        BusinessId id = novoNegocioAtivoComCatalogo(slug);
        var item = CatalogoDeTeste.item(consultarCatalogo, id, CatalogoDeTeste.UNHAS);

        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments", slug)
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(item.id().getValue().toString(),
                                item.profissionais().get(0).id().getValue().toString(),
                                proximaQuarta().toString(), "10:00", "Cliente", "+5511999990016")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("18. outros métodos na mesma rota, e outros POST públicos, continuam bloqueados")
    void outrosMetodosEOutrasRotasContinuamBloqueadas() throws Exception {
        String slug = novoSlug("bloqueio");
        novoNegocioAtivoComCatalogo(slug);

        mockMvc.perform(get("/api/v1/public/businesses/{slug}/appointments", slug))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/public/businesses/{slug}/appointments", slug))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/public/businesses/{slug}/appointments", slug))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/public/businesses/{slug}", slug))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/availability", slug))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/public/businesses/{slug}/appointments/extra", slug))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("19. o Controller público de agendamento não injeta nenhum Repository — só Application")
    void controllerNaoAcessaRepositoryDiretamente() {
        Constructor<?>[] construtores = PublicBookingController.class.getDeclaredConstructors();
        assertEquals(1, construtores.length);

        for (Class<?> parametro : construtores[0].getParameterTypes()) {
            assertThat(parametro.getSimpleName())
                    .as("parâmetro '%s' do Controller público não pode ser um Repository", parametro.getSimpleName())
                    .doesNotContain("Repository");
        }
    }
}
