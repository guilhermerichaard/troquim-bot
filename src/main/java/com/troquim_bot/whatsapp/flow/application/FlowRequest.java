package com.troquim_bot.whatsapp.flow.application;

import java.util.Map;
import java.util.Optional;

/**
 * Requisição do Flow já decifrada e desserializada.
 *
 * Contrato de TRANSPORTE da Application: nenhuma entidade JPA atravessa esta fronteira.
 * Todo campo de {@code data} vem do cliente e é NÃO CONFIÁVEL — os acessadores
 * tipados abaixo devolvem {@link Optional} em vez de lançar, para que a validação seja
 * decisão explícita de cada handler.
 *
 * @param version   versão do protocolo de dados (ex.: "3.0")
 * @param action    ação do protocolo da Meta: {@code ping}, {@code INIT},
 *                  {@code data_exchange} ou {@code BACK}
 * @param screen    id da tela que originou a requisição (pode ser nulo em {@code ping})
 * @param data      dados enviados pela tela; nunca nulo (vazio quando ausente)
 * @param flowToken token opaco que identifica a sessão — a ÚNICA amarração confiável
 *                  entre a requisição e o cliente do WhatsApp
 */
public record FlowRequest(String version,
                          String action,
                          String screen,
                          Map<String, Object> data,
                          String flowToken) {

    public static final String PROTOCOL_PING = "ping";
    public static final String PROTOCOL_INIT = "INIT";
    public static final String PROTOCOL_DATA_EXCHANGE = "data_exchange";
    public static final String PROTOCOL_BACK = "BACK";

    /** Campo estável que carrega o passo de negócio dentro de {@code data}. */
    public static final String CAMPO_ACAO = "flow_action";

    public FlowRequest {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public boolean isPing() {
        return PROTOCOL_PING.equalsIgnoreCase(action);
    }

    public boolean isInit() {
        return PROTOCOL_INIT.equalsIgnoreCase(action);
    }

    public boolean isDataExchange() {
        return PROTOCOL_DATA_EXCHANGE.equalsIgnoreCase(action);
    }

    public boolean isBack() {
        return PROTOCOL_BACK.equalsIgnoreCase(action);
    }

    /**
     * Notificação de erro do cliente: a Meta envia {@code data.error} quando a tela
     * falhou. O protocolo pede apenas confirmação de recebimento.
     */
    public boolean isNotificacaoDeErro() {
        return data.containsKey("error") || data.containsKey("error_message");
    }

    /** Passo de negócio declarado pela tela. Vazio se ausente ou desconhecido. */
    public Optional<FlowAction> acaoDeNegocio() {
        return texto(CAMPO_ACAO).flatMap(FlowAction::parse);
    }

    /** Lê um campo de texto de {@code data}, normalizando vazio para ausente. */
    public Optional<String> texto(String campo) {
        Object valor = data.get(campo);
        if (valor == null) {
            return Optional.empty();
        }
        String texto = String.valueOf(valor).trim();
        return texto.isEmpty() ? Optional.empty() : Optional.of(texto);
    }
}
