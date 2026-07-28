package com.troquim_bot.whatsapp.flow;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import com.troquim_bot.whatsapp.flow.support.FlowTestCrypto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Isolamento cross-tenant do Data Endpoint, ponta a ponta.
 *
 * Uma sessão emitida sob OUTRO negócio não pode ser reproduzida contra o endpoint que
 * atende o piloto — nem para NAVEGAR (ler catálogo/agenda) nem para AGENDAR. A guarda
 * vive na Application ({@code FlowExchangeService}), não no controller, e responde o
 * MESMO 427 de um token desconhecido: quem sonda tokens não descobre que o token existe
 * noutro tenant.
 *
 * O teste usa a cadeia real de criptografia e a porta real de sessões — nada é simulado
 * além do tenant da sessão.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WhatsApp Flow - isolamento entre negócios")
class WhatsAppFlowTenantIsolationTest {

    private static final String ROTA = "/api/v1/whatsapp/flows";
    private static final String TELEFONE = "5511999990000";

    private static final FlowTestCrypto CRYPTO = new FlowTestCrypto();

    @DynamicPropertySource
    static void chaves(DynamicPropertyRegistry registry) {
        registry.add("troquim.integrations.whatsapp.flow.enabled", () -> "true");
        registry.add("troquim.integrations.whatsapp.flow.private-key", CRYPTO::privateKeyPem);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FlowSessionStore sessionStore;
    @Autowired
    private AppointmentApplicationService appointmentApplicationService;

    /** Sessão legítima, porém aberta para um negócio que NÃO é o tenant corrente. */
    private FlowSession sessaoDeOutroNegocio() {
        return sessionStore.abrir(TELEFONE, TestTenants.OUTRO.getValue(),
                LocalDateTime.now().plusHours(1));
    }

    @Test
    @DisplayName("token do tenant B não consulta a agenda do tenant A (427)")
    void tokenDeOutroTenantNaoNavega() throws Exception {
        FlowSession outroTenant = sessaoDeOutroNegocio();

        String corpo = """
                {"version":"3.0","action":"data_exchange","screen":"SERVICO","flow_token":"%s",
                 "data":{"flow_action":"SERVICO_SELECIONADO","servico_id":"cabelo"}}"""
                .formatted(outroTenant.flowToken());

        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(CRYPTO.envelope(corpo, CRYPTO.novaSessao())))
                .andExpect(result -> assertEquals(427, result.getResponse().getStatus(),
                        "Sessão de outro negócio deve falhar como token desconhecido"));
    }

    @Test
    @DisplayName("token do tenant B não cria agendamento no tenant A (427 e nada gravado)")
    void tokenDeOutroTenantNaoAgenda() throws Exception {
        int antes = appointmentApplicationService.listarAtivos().size();
        FlowSession outroTenant = sessaoDeOutroNegocio();
        LocalDate dia = proximaQuarta();

        String corpo = """
                {"version":"3.0","action":"data_exchange","screen":"CONFIRMACAO","flow_token":"%s",
                 "data":{"flow_action":"CONFIRMAR_AGENDAMENTO","servico_id":"unha","profissional_id":"qualquer",
                 "data":"%s","horario":"10:00","nome":"Ana Souza"}}"""
                .formatted(outroTenant.flowToken(), dia);

        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(CRYPTO.envelope(corpo, CRYPTO.novaSessao())))
                .andExpect(result -> assertEquals(427, result.getResponse().getStatus()));

        assertEquals(antes, appointmentApplicationService.listarAtivos().size(),
                "Nenhum agendamento pode ser criado a partir de sessão de outro negócio");
    }

    @Test
    @DisplayName("a sessão do outro negócio continua intacta — o 427 não a consome")
    void sessaoDeOutroTenantNaoEhAlterada() throws Exception {
        FlowSession outroTenant = sessaoDeOutroNegocio();

        String corpo = """
                {"version":"3.0","action":"data_exchange","screen":"SERVICO","flow_token":"%s",
                 "data":{"flow_action":"SERVICO_SELECIONADO","servico_id":"cabelo"}}"""
                .formatted(outroTenant.flowToken());

        mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(CRYPTO.envelope(corpo, CRYPTO.novaSessao())))
                .andExpect(result -> assertEquals(427, result.getResponse().getStatus()));

        FlowSession depois = sessionStore.buscar(outroTenant.flowToken()).orElseThrow();
        assertTrue(depois.pertenceAoTenant(TestTenants.OUTRO.getValue()),
                "A recusa não pode reatribuir a sessão a outro negócio");
        assertTrue(depois.utilizavel(LocalDateTime.now()),
                "A recusa não pode invalidar a sessão legítima do outro negócio");
    }

    private static LocalDate proximaQuarta() {
        LocalDate data = LocalDate.now().plusDays(1);
        while (data.getDayOfWeek() != java.time.DayOfWeek.WEDNESDAY) {
            data = data.plusDays(1);
        }
        return data;
    }
}
