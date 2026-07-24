package com.troquim_bot.whatsapp.flow.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resposta do endpoint, ainda em texto claro (a cifragem é da Infrastructure).
 *
 * Formato conforme a documentação oficial e o exemplo de referência da Meta
 * (WhatsApp-Flows-Tools/examples/endpoint):
 * <ul>
 *   <li>troca de tela: {@code {"screen": "...", "data": {...}}} — SEM campo version;</li>
 *   <li>ping: {@code {"data": {"status": "active"}}};</li>
 *   <li>notificação de erro: {@code {"data": {"acknowledged": true}}};</li>
 *   <li>encerramento: tela RESERVADA {@code "SUCCESS"} com
 *       {@code extension_message_response.params} contendo o flow_token.</li>
 * </ul>
 *
 * A Meta descarta silenciosamente qualquer campo de {@code data} que não esteja
 * declarado no schema da tela no Flow JSON — os nomes usados aqui e no arquivo
 * {@code agendamento-salao.flow.json} precisam andar juntos.
 *
 * @param screen id da tela a exibir; nulo em respostas de protocolo (ping/ack)
 * @param data   dados da tela; nunca nulo
 */
public record FlowResponse(String screen, Map<String, Object> data) {

    /**
     * Nome RESERVADO pela Meta para encerrar um Flow conduzido por endpoint. Não é uma
     * tela declarada no JSON: respondê-la fecha o Flow e entrega os params na conversa
     * (webhook {@code nfm_reply}).
     */
    public static final String SCREEN_SUCCESS_RESERVADA = "SUCCESS";

    public FlowResponse {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    /** Navega para (ou re-renderiza) uma tela declarada do agendamento. */
    public static FlowResponse tela(FlowScreen screen, Map<String, Object> data) {
        return new FlowResponse(screen.id(), data);
    }

    /** Health check do protocolo: {@code ping} → {@code {"data":{"status":"active"}}}. */
    public static FlowResponse ativo() {
        return new FlowResponse(null, Map.of("status", "active"));
    }

    /** Confirmação de recebimento de uma notificação de erro do cliente. */
    public static FlowResponse reconhecido() {
        return new FlowResponse(null, Map.of("acknowledged", true));
    }

    /**
     * Encerramento do Flow pela tela reservada SUCCESS. O protocolo exige o
     * {@code flow_token} dentro de {@code extension_message_response.params}; os demais
     * params voltam ao negócio no webhook de conclusão e alimentam a mensagem final.
     */
    public static FlowResponse terminal(String flowToken, Map<String, Object> params) {
        Map<String, Object> todosOsParams = new LinkedHashMap<>(params);
        todosOsParams.put("flow_token", flowToken);
        return new FlowResponse(SCREEN_SUCCESS_RESERVADA, Map.of(
                "extension_message_response", Map.of("params", todosOsParams)));
    }

    /**
     * Serialização no formato do protocolo. Sem campo {@code version}: a resposta de
     * data_exchange é {@code {screen, data}} e as de protocolo são {@code {data}},
     * conforme o exemplo oficial da Meta.
     */
    public Map<String, Object> comoMapa() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        if (screen != null) {
            corpo.put("screen", screen);
        }
        corpo.put("data", data);
        return corpo;
    }
}
