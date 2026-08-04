package com.troquim_bot.owner;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.owner.api.OwnerSessionCookie;
import com.troquim_bot.owner.application.OwnerUserRepository;
import com.troquim_bot.owner.domain.OwnerUser;
import com.troquim_bot.owner.infrastructure.BCryptPasswordHasher;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.repository.CustomerRepository;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * /app: acesso negado sem sessao, e isolamento ponta a ponta entre dois donos de
 * negocios diferentes -- nome, agenda e status do canal de um nunca aparecem para o
 * outro. Usa o fluxo HTTP real (login -> cookie -> GET /app), nao atalho de unidade.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = {
        "troquim.integrations.whatsapp.embedded-signup.enabled=true",
        "troquim.integrations.whatsapp.embedded-signup.app-id=app-id-de-teste",
        "troquim.integrations.whatsapp.embedded-signup.config-id=973012265764230",
        "troquim.integrations.whatsapp.embedded-signup.app-secret=segredo-que-nunca-pode-vazar",
        "troquim.integrations.whatsapp.embedded-signup.graph-api-version=vtest",
        "troquim.integrations.whatsapp.embedded-signup.base-url=http://localhost:59999"
})
@Transactional
@DisplayName("/app - acesso negado e isolamento entre donos")
class OwnerAppAccessTest {

    private static final String APP_SECRET = "segredo-que-nunca-pode-vazar";

    @Autowired private MockMvc mockMvc;
    @Autowired private OwnerUserRepository ownerUserRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AppointmentApplicationService appointmentApplicationService;
    @Autowired private BCryptPasswordHasher hasher;

    private static final ProfessionalId PROFISSIONAL =
            ProfessionalId.from(UUID.fromString("11111111-2222-3333-4444-555555555555"));

    @BeforeEach
    void seed() {
        salvarBusiness(TestTenants.PILOT, "Salao da Ana");
        salvarBusiness(TestTenants.OUTRO, "Barbearia do Bruno");

        ownerUserRepository.salvar(OwnerUser.novo(TestTenants.PILOT, "ana@teste.com", hasher.hash("senha-ana-123")));
        ownerUserRepository.salvar(OwnerUser.novo(TestTenants.OUTRO, "bruno@teste.com", hasher.hash("senha-bruno-123")));

        agendarPara(TestTenants.PILOT, "Cliente da Ana");
        agendarPara(TestTenants.OUTRO, "Cliente do Bruno");
    }

    private void salvarBusiness(BusinessId id, String nome) {
        businessRepository.save(new Business(id, nome, "(11) 90000-0000", "SP"));
    }

    private void agendarPara(BusinessId tenant, String nomeCliente) {
        CustomerId customerId = CustomerId.generate();
        customerRepository.save(new Customer(customerId, tenant,
                CustomerName.of(nomeCliente + " Sobrenome"), new PhoneNumber("11988887777"), null));
        appointmentApplicationService.criarAgendamento(tenant, customerId, PROFISSIONAL,
                ServiceId.generate(), AvailabilityId.generate(),
                LocalDate.now().plusDays(2), LocalTime.of(10, 0), LocalTime.of(11, 0));
    }

    /**
     * MockHttpServletRequest não faz parsing de um header "Cookie" bruto em
     * getCookies() -- só um jakarta.servlet.http.Cookie estruturado, via
     * MockHttpServletRequestBuilder.cookie(...), chega ao filtro. Por isso o
     * login devolve o Cookie já pronto, não a string do header.
     */
    private jakarta.servlet.http.Cookie cookieDoLogin(String email, String senha) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/owner/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andReturn();
        assertEquals(200, resultado.getResponse().getStatus());
        String setCookie = resultado.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie, "Login precisa devolver o cookie de sessao");
        String par = setCookie.split(";")[0];
        String valor = par.substring(par.indexOf('=') + 1);
        return new jakarta.servlet.http.Cookie(OwnerSessionCookie.NOME, valor);
    }

    @Test
    @DisplayName("GET /app sem sessao responde 401/403")
    void semSessaoNegaAcesso() throws Exception {
        mockMvc.perform(get("/app"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403,
                        "Esperava 401/403, veio " + result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("POST start sem sessao responde 401/403, mesmo com Embedded Signup ligado")
    void semSessaoNegaInicioDeConexao() throws Exception {
        mockMvc.perform(post("/api/v1/app/whatsapp/connection/start"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
    }

    @Test
    @DisplayName("cookie invalido nao concede acesso")
    void cookieInvalidoNegaAcesso() throws Exception {
        mockMvc.perform(get("/app").cookie(new jakarta.servlet.http.Cookie(
                        "troquim_owner_session", "token-que-nunca-existiu")))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
    }

    @Test
    @DisplayName("dono A ve o proprio negocio e cliente; nunca os do dono B")
    void isolamentoEntreDonos() throws Exception {
        var cookieAna = cookieDoLogin("ana@teste.com", "senha-ana-123");
        var cookieBruno = cookieDoLogin("bruno@teste.com", "senha-bruno-123");

        String paginaAna = mockMvc.perform(get("/app").cookie(cookieAna))
                .andReturn().getResponse().getContentAsString();
        String paginaBruno = mockMvc.perform(get("/app").cookie(cookieBruno))
                .andReturn().getResponse().getContentAsString();

        // CustomerName normaliza para Title Case ("Cliente Da Ana", nao "Cliente da Ana").
        assertTrue(paginaAna.contains("Salao da Ana"));
        assertTrue(paginaAna.contains("Cliente Da Ana"));
        assertFalse(paginaAna.contains("Barbearia do Bruno"), "Pagina da Ana nao pode citar o negocio do Bruno");
        assertFalse(paginaAna.contains("Cliente Do Bruno"), "Pagina da Ana nao pode citar cliente do Bruno");

        assertTrue(paginaBruno.contains("Barbearia do Bruno"));
        assertTrue(paginaBruno.contains("Cliente Do Bruno"));
        assertFalse(paginaBruno.contains("Salao da Ana"));
        assertFalse(paginaBruno.contains("Cliente Da Ana"));
    }

    @Test
    @DisplayName("status do canal de um dono nunca aparece para o outro, mesmo sem conexao")
    void statusDeCanalNaoAtravessaDono() throws Exception {
        var cookieBruno = cookieDoLogin("bruno@teste.com", "senha-bruno-123");

        String paginaBruno = mockMvc.perform(get("/app").cookie(cookieBruno))
                .andReturn().getResponse().getContentAsString();

        assertTrue(paginaBruno.contains("nao conectado")
                || paginaBruno.contains("não conectado"),
                "Bruno nunca conectou: precisa mostrar status vazio, nunca herdar de outro tenant");
    }

    @Test
    @DisplayName("HTML de /app expoe o config_id (publico) mas jamais o app secret")
    void appSecretNuncaVazaNoHtml() throws Exception {
        var cookieAna = cookieDoLogin("ana@teste.com", "senha-ana-123");

        String pagina = mockMvc.perform(get("/app").cookie(cookieAna))
                .andReturn().getResponse().getContentAsString();

        assertTrue(pagina.contains("973012265764230"), "config_id e' publico e deve aparecer no HTML");
        assertFalse(pagina.contains(APP_SECRET), "O App Secret NUNCA pode chegar ao HTML");
        assertFalse(pagina.toLowerCase().contains("secret"),
                "Nem o nome do campo deve aparecer no HTML renderizado");
    }

    @Test
    @DisplayName("resposta JSON de start/finish tambem nunca inclui o app secret")
    void appSecretNuncaVazaNoJson() throws Exception {
        var cookieAna = cookieDoLogin("ana@teste.com", "senha-ana-123");

        MvcResult inicio = mockMvc.perform(post("/api/v1/app/whatsapp/connection/start").cookie(cookieAna))
                .andReturn();
        assertEquals(200, inicio.getResponse().getStatus());
        String corpo = inicio.getResponse().getContentAsString();

        assertFalse(corpo.contains(APP_SECRET));
        assertFalse(corpo.toLowerCase().contains("secret"));
        assertTrue(corpo.contains("973012265764230"));
    }

    @Test
    @DisplayName("code invalido falha sem persistir conexao parcial")
    void codeInvalidoNaoDeixaConexaoParcial() throws Exception {
        var cookieAna = cookieDoLogin("ana@teste.com", "senha-ana-123");

        MvcResult inicio = mockMvc.perform(post("/api/v1/app/whatsapp/connection/start").cookie(cookieAna))
                .andReturn();
        String state = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(inicio.getResponse().getContentAsString()).get("state").asText();

        // A Graph API real nao esta configurada neste teste (base-url aponta para uma
        // porta inerte): a troca de code falha exatamente como um code invalido falharia.
        mockMvc.perform(post("/api/v1/app/whatsapp/connection/finish").cookie(cookieAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"" + state + "\",\"code\":\"codigo-qualquer\"}"))
                .andExpect(result -> assertEquals(400, result.getResponse().getStatus()));

        String pagina = mockMvc.perform(get("/app").cookie(cookieAna))
                .andReturn().getResponse().getContentAsString();
        // A folha de estilo sempre declara a regra ".s-conectado{...}"; o que importa e'
        // a classe de fato APLICADA ao status renderizado, nao a substring solta.
        assertFalse(pagina.contains("class=\"status s-conectado\""),
                "Falha na troca nao pode deixar o canal como conectado");
        assertTrue(pagina.contains("class=\"status s-falhou\""),
                "O status renderizado precisa ser FALHOU");
    }
}
