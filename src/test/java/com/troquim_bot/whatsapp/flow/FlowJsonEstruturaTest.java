package com.troquim_bot.whatsapp.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cenário 23 — validação do Flow JSON até onde dá para automatizar AQUI.
 *
 * LIMITE EXPLÍCITO: isto NÃO substitui a validação oficial. A Meta não publica um
 * validador de schema executável (o repositório WhatsApp-Flows-Tools traz exemplos de
 * endpoint e coleções Postman, não um CLI; não há pacote npm oficial). A validação real
 * é o Flow Builder do WhatsApp Manager, que exige credencial da Meta.
 *
 * O que este teste cobre são as invariantes que, quando quebradas, causam falha
 * SILENCIOSA — a pior classe de erro aqui:
 * <ul>
 *   <li>campo devolvido pelo endpoint sem declaração no schema da tela: a Meta descarta
 *       sem avisar, e a tela aparece vazia;</li>
 *   <li>id de tela divergente do enum Java: o roteamento quebra em runtime;</li>
 *   <li>rota ausente no routing_model: a partir da versão 7.3 a navegação é validada.</li>
 * </ul>
 */
@DisplayName("Flow JSON - estrutura e contrato com o código")
class FlowJsonEstruturaTest {

    private static final String CAMINHO = "whatsapp/flows/agendamento-salao.flow.json";

    private static JsonNode flow;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = FlowJsonEstruturaTest.class.getClassLoader()
                .getResourceAsStream(CAMINHO)) {
            assertNotNull(in, "Flow JSON não encontrado em " + CAMINHO);
            flow = new ObjectMapper().readTree(in);
        }
    }

    @Test
    @DisplayName("declara a versão do Flow JSON e a do protocolo de dados")
    void versoes() {
        // 7.3 é a versão recomendada atual do Flow JSON. data_api_version PERMANECE
        // dentro do Flow JSON (Flow JSON Reference oficial) — o que saiu do JSON foi o
        // data_channel_uri (URL do endpoint), agora configurado pela Flows API. Existe
        // data_api_version 4.0 (changelog oficial: two-signature auth no endpoint), mas
        // exige implementar a verificação de assinatura da plataforma — fora deste escopo.
        // Ficamos em 3.0 até implementar 4.0.
        assertEquals("7.3", flow.path("version").asText());
        assertEquals("3.0", flow.path("data_api_version").asText());
    }

    @Test
    @DisplayName("os ids das telas batem exatamente com o enum FlowScreen")
    void idsDeTelaBatemComOCodigo() {
        Set<String> noJson = new HashSet<>();
        flow.path("screens").forEach(s -> noJson.add(s.path("id").asText()));

        Set<String> noCodigo = new HashSet<>();
        for (com.troquim_bot.whatsapp.flow.application.FlowScreen tela
                : com.troquim_bot.whatsapp.flow.application.FlowScreen.values()) {
            noCodigo.add(tela.id());
        }

        assertEquals(noCodigo, noJson,
                "Renomear uma tela em só um dos lados quebra Flows já publicados");
    }

    @Test
    @DisplayName("o routing_model cobre todas as telas e só aponta para telas existentes")
    void routingModelConsistente() {
        JsonNode routing = flow.path("routing_model");
        Set<String> telas = new HashSet<>();
        flow.path("screens").forEach(s -> telas.add(s.path("id").asText()));

        assertEquals(telas, new HashSet<>(iteravel(routing.fieldNames())),
                "Toda tela precisa estar no routing_model (validado pela Meta desde a 7.3)");

        routing.fields().forEachRemaining(entrada ->
                entrada.getValue().forEach(destino -> assertTrue(
                        telas.contains(destino.asText()),
                        "Rota para tela inexistente: " + entrada.getKey() + " -> " + destino.asText())));
    }

    @Test
    @DisplayName("CONFIRMACAO é a única tela terminal e está marcada como sucesso")
    void telaTerminal() {
        // O contrato canônico encerra pela tela reservada SUCCESS da Meta (não declarada
        // no JSON): a última tela DECLARADA (CONFIRMACAO) é a terminal, cujo footer
        // dispara o data_exchange de confirmação.
        List<String> terminais = new ArrayList<>();
        flow.path("screens").forEach(s -> {
            if (s.path("terminal").asBoolean()) {
                terminais.add(s.path("id").asText());
            }
        });

        assertEquals(List.of("CONFIRMACAO"), terminais);
        flow.path("screens").forEach(s -> {
            if ("CONFIRMACAO".equals(s.path("id").asText())) {
                assertTrue(s.path("success").asBoolean());
            }
        });
    }

    @Test
    @DisplayName("todo campo referenciado como ${data.x} está declarado no schema da tela")
    void referenciasDeDadosDeclaradas() {
        flow.path("screens").forEach(tela -> {
            String id = tela.path("id").asText();
            Set<String> declarados = new HashSet<>(iteravel(tela.path("data").fieldNames()));
            Set<String> referenciados = new HashSet<>();
            coletarReferencias(tela.path("layout"), referenciados);

            for (String campo : referenciados) {
                assertTrue(declarados.contains(campo),
                        "Tela " + id + " usa ${data." + campo + "} sem declarar no schema — "
                                + "a Meta descartaria o campo sem erro");
            }
        });
    }

    @Test
    @DisplayName("referências ${data.x} são binding de valor inteiro, sem interpolação com literais")
    void bindingDinamicoEhValorInteiro() {
        // O Flow JSON só aceita binding de valor INTEIRO: "${data.campo}" ocupando toda a
        // string. Misturar referência com texto literal ("${a} às ${b}") NÃO é suportado —
        // o Builder o rejeita (rotulando como "campo não declarado"). Este guard percorre
        // todos os valores string do layout e falha se uma referência ${data.x} não for o
        // valor inteiro.
        List<String> valores = new ArrayList<>();
        coletarStrings(flow.path("screens"), valores);

        for (String valor : valores) {
            if (valor.contains("${data.")) {
                assertTrue(valor.matches("^\\$\\{data\\.[a-zA-Z0-9_]+}$"),
                        "Binding dinâmico deve ser valor inteiro (sem interpolação com literal): \""
                                + valor + "\"");
            }
        }
    }

    @Test
    @DisplayName("labels de TextArea respeitam o limite de 20 caracteres da Meta")
    void textAreaLabelDentroDoLimite() {
        // O Flow Builder rejeita label de TextArea acima de 20 caracteres.
        List<JsonNode> textAreas = new ArrayList<>();
        coletarPorTipo(flow.path("screens"), "TextArea", textAreas);

        assertFalse(textAreas.isEmpty(), "Deve existir ao menos um TextArea (observacoes)");
        for (JsonNode ta : textAreas) {
            String label = ta.path("label").asText();
            assertTrue(label.length() <= 20,
                    "Label de TextArea '" + ta.path("name").asText() + "' excede 20 caracteres ("
                            + label.length() + "): \"" + label + "\"");
        }
    }

    @Test
    @DisplayName("toda ação data_exchange declara o flow_action que o registry conhece")
    void acoesConhecidasPeloCodigo() {
        Set<String> acoesValidas = new HashSet<>();
        for (com.troquim_bot.whatsapp.flow.application.FlowAction acao
                : com.troquim_bot.whatsapp.flow.application.FlowAction.values()) {
            acoesValidas.add(acao.name());
        }

        List<String> encontradas = new ArrayList<>();
        coletarAcoes(flow.path("screens"), encontradas);

        assertFalse(encontradas.isEmpty(), "Nenhuma ação data_exchange encontrada");
        for (String acao : encontradas) {
            assertTrue(acoesValidas.contains(acao),
                    "Flow JSON envia flow_action desconhecida pelo backend: " + acao);
        }
    }

    @Test
    @DisplayName("data e horário usam Dropdown data-driven, não componente com estado próprio")
    void dataEHorarioSaoDropdown() {
        // O contrato canônico usa Dropdown alimentado pelo backend (habilitação
        // progressiva via on-select), como o exemplo oficial book-appointment. Não há
        // CalendarPicker — a elegibilidade de datas é decidida pelo domínio, não pelo
        // componente.
        List<JsonNode> dropdowns = new ArrayList<>();
        coletarPorTipo(flow.path("screens"), "Dropdown", dropdowns);
        List<JsonNode> calendars = new ArrayList<>();
        coletarPorTipo(flow.path("screens"), "CalendarPicker", calendars);

        assertTrue(calendars.isEmpty(), "O contrato canônico não usa CalendarPicker");
        assertTrue(dropdowns.stream().anyMatch(d -> "data".equals(d.path("name").asText())),
                "Deve existir um Dropdown 'data'");
        assertTrue(dropdowns.stream().anyMatch(d -> "horario".equals(d.path("name").asText())),
                "Deve existir um Dropdown 'horario'");
    }

    @Test
    @DisplayName("nenhum TextInput usa init-value (rejeitado pelo Flow Builder)")
    void textInputSemInitValue() {
        // O Flow Builder da Meta rejeita `init-value` em TextInput. O pré-preenchimento
        // do nome (nome_prefill) continua no schema e é enviado pelo presenter, mas a Meta
        // simplesmente o ignora — não pode ser ligado via init-value.
        List<JsonNode> inputs = new ArrayList<>();
        coletarPorTipo(flow.path("screens"), "TextInput", inputs);

        assertFalse(inputs.isEmpty(), "Deve existir ao menos um TextInput (nome)");
        for (JsonNode input : inputs) {
            assertTrue(input.path("init-value").isMissingNode(),
                    "TextInput '" + input.path("name").asText() + "' não pode ter init-value");
        }
    }

    @Test
    @DisplayName("visible está ligado a booleano, não a texto")
    void visibleUsaBooleano() {
        List<String> visiveis = new ArrayList<>();
        coletarVisible(flow.path("screens"), visiveis);

        assertFalse(visiveis.isEmpty());
        for (String expressao : visiveis) {
            assertEquals("${data.tem_erro}", expressao,
                    "A checagem de tipo da 7.3 reprova `visible` apontando para string");
        }
    }

    @Test
    @DisplayName("os textos são em português e sem instruções de menu numérico")
    void textoEmPortuguesSemDigiteNumero() {
        String bruto = flow.toString().toLowerCase();
        assertFalse(bruto.contains("digite 1"), "O Flow não deve pedir 'digite 1'");
        assertFalse(bruto.contains("type 1"));
        assertTrue(bruto.contains("horário") || bruto.contains("horários"),
                "Os textos devem estar em pt-BR");
    }

    // ==================== helpers de varredura ====================

    /** Coleta todo valor string folha sob um nó (para inspecionar bindings). */
    private static void coletarStrings(JsonNode no, List<String> destino) {
        if (no.isTextual()) {
            destino.add(no.asText());
            return;
        }
        no.forEach(filho -> coletarStrings(filho, destino));
    }

    private static void coletarReferencias(JsonNode no, Set<String> destino) {
        if (no.isTextual()) {
            String texto = no.asText();
            int i = 0;
            while ((i = texto.indexOf("${data.", i)) >= 0) {
                int fim = texto.indexOf('}', i);
                if (fim < 0) {
                    break;
                }
                destino.add(texto.substring(i + "${data.".length(), fim));
                i = fim;
            }
            return;
        }
        no.forEach(filho -> coletarReferencias(filho, destino));
    }

    private static void coletarAcoes(JsonNode no, List<String> destino) {
        if (no.isObject() && no.has("flow_action") && no.path("flow_action").isTextual()) {
            destino.add(no.path("flow_action").asText());
        }
        no.forEach(filho -> coletarAcoes(filho, destino));
    }

    private static void coletarPorTipo(JsonNode no, String tipo, List<JsonNode> destino) {
        if (no.isObject() && tipo.equals(no.path("type").asText())) {
            destino.add(no);
        }
        no.forEach(filho -> coletarPorTipo(filho, tipo, destino));
    }

    private static void coletarVisible(JsonNode no, List<String> destino) {
        if (no.isObject() && no.has("visible") && no.path("visible").isTextual()) {
            destino.add(no.path("visible").asText());
        }
        no.forEach(filho -> coletarVisible(filho, destino));
    }

    private static List<String> iteravel(java.util.Iterator<String> it) {
        List<String> lista = new ArrayList<>();
        it.forEachRemaining(lista::add);
        return lista;
    }
}
