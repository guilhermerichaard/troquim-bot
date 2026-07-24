package com.troquim_bot.whatsapp.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.support.TestTenants;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import com.troquim_bot.whatsapp.flow.support.FlowTestCrypto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Cenário 14: falha de PERSISTÊNCIA na confirmação.
 *
 * A garantia sob teste é a mais importante do endpoint: se a escrita falha, o cliente NÃO
 * pode ver a tela de sucesso. Um sucesso falso aqui significa alguém aparecendo no salão
 * num horário que não existe.
 *
 * O repositório de Appointment é substituído por um duplo que estoura no {@code save} —
 * classe separada porque a substituição de bean vale para o contexto inteiro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Precisa vir daqui (e nao de @DynamicPropertySource): a flag e lida na
        // construcao do SpringApplication, antes das propriedades dinamicas.
        properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WhatsApp Flow - falha de persistência")
class WhatsAppFlowPersistenceFailureTest {

    private static final String ROTA = "/api/v1/whatsapp/flows";
    private static final FlowTestCrypto CRYPTO = new FlowTestCrypto();

    @DynamicPropertySource
    static void chaves(DynamicPropertyRegistry registry) {
        registry.add("troquim.integrations.whatsapp.flow.enabled", () -> "true");
        registry.add("troquim.integrations.whatsapp.flow.private-key", CRYPTO::privateKeyPem);
    }

    /** Repositório que aceita leitura mas recusa toda escrita. */
    @TestConfiguration
    static class RepositorioQueFalha {

        // Mesmo NOME do bean de producao: sobrescreve a definicao em vez de concorrer
        // com ela por @Primary.
        @Bean("jpaAppointmentRepository")
        @Primary
        AppointmentRepository appointmentRepositoryQueFalha() {
            return new AppointmentRepository() {
                @Override
                public Appointment save(Appointment appointment) {
                    throw new IllegalStateException("Falha simulada de persistência");
                }

                @Override
                public Appointment findById(AppointmentId id) {
                    return null;
                }

                @Override
                public List<Appointment> findAll() {
                    return List.of();
                }

                @Override
                public List<Appointment> findByCustomerId(CustomerId customerId) {
                    return List.of();
                }

                @Override
                public List<Appointment> findByProfessionalIdAndDate(ProfessionalId professionalId,
                                                                     LocalDate date) {
                    return List.of();
                }

                @Override
                public boolean exists(AppointmentId id) {
                    return false;
                }

                @Override
                public void delete(AppointmentId id) {
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FlowSessionStore sessionStore;

    // Mapper proprio: o Spring Boot 4 nao expoe um bean do Jackson 2 usado aqui.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("falha de persistência não devolve SUCESSO")
    void naoConfirmaQuandoPersistenciaFalha() throws Exception {
        FlowSession sessao = sessionStore.abrir("5511977776666", TestTenants.PILOT.getValue(),
                LocalDateTime.now().plusHours(1));

        LocalDate dia = LocalDate.now().plusDays(1);
        while (dia.getDayOfWeek() != java.time.DayOfWeek.WEDNESDAY) {
            dia = dia.plusDays(1);
        }

        String corpo = """
                {"version":"3.0","action":"data_exchange","screen":"CONFIRMACAO","flow_token":"%s",
                 "data":{"flow_action":"CONFIRMAR_AGENDAMENTO","servico_id":"unha","profissional_id":"qualquer",
                 "data":"%s","horario":"10:00","nome":"Ana Souza"}}"""
                .formatted(sessao.flowToken(), dia);

        FlowTestCrypto.Sessao cripto = CRYPTO.novaSessao();
        MvcResult resultado = mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON)
                        .content(CRYPTO.envelope(corpo, cripto))).andReturn();

        assertEquals(200, resultado.getResponse().getStatus());
        JsonNode resposta = objectMapper.readTree(
                CRYPTO.decifrarResposta(resultado.getResponse().getContentAsString(), cripto));

        assertNotEquals("SUCCESS", resposta.path("screen").asText(),
                "Persistência falhou: o cliente não pode ver a tela de sucesso");
        assertFalse(resposta.path("data").path("error_message").asText().isEmpty(),
                "O cliente precisa ver uma mensagem, não uma tela muda");

        // Falha tecnica devolve o cliente a CONFIRMACAO com a MESMA escolha, para que
        // repetir seja um retry do mesmo agendamento — e nao a HORARIO, que afirmaria um
        // diagnostico de agenda que esta falha justamente nos impede de ter.
        assertEquals("CONFIRMACAO", resposta.path("screen").asText());

        String mensagem = resposta.path("data").path("error_message").asText();
        assertEquals(BookingResult.MENSAGEM_FALHA_TECNICA, mensagem,
                "O Flow precisa usar o texto canonico e neutro de falha tecnica");

        String texto = mensagem.toLowerCase();
        for (String proibido : List.of("ocupado", "indisponível", "escolha outro",
                "continua livre", "continua disponível", "não foi criado", "nada foi criado",
                "nada foi agendado", "não foi agendado")) {
            assertFalse(texto.contains(proibido),
                    "Falha tecnica nao pode afirmar \"" + proibido + "\": " + mensagem);
        }

        // E a sessão continua sem recibo: uma nova tentativa é permitida.
        assertFalse(sessionStore.buscar(sessao.flowToken()).orElseThrow().jaConfirmada());
    }
}
