package com.troquim_bot.whatsapp.flow.application.handler;

import com.troquim_bot.whatsapp.flow.application.FlowAction;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Índice dos handlers por evento canônico.
 *
 * Construído dos beans descobertos pelo Spring, com validação na inicialização:
 * evento duplicado OU evento sem handler viram falha de boot — nunca comportamento
 * imprevisível em produção.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowHandlerRegistry {

    private final Map<FlowAction, FlowActionHandler> porAcao = new EnumMap<>(FlowAction.class);

    public FlowHandlerRegistry(List<FlowActionHandler> handlers) {
        for (FlowActionHandler handler : handlers) {
            FlowActionHandler anterior = porAcao.put(handler.acao(), handler);
            if (anterior != null) {
                throw new IllegalStateException(
                        "Mais de um handler registrado para o evento " + handler.acao());
            }
        }
        EnumSet<FlowAction> faltando = EnumSet.complementOf(
                porAcao.isEmpty() ? EnumSet.noneOf(FlowAction.class) : EnumSet.copyOf(porAcao.keySet()));
        if (!faltando.isEmpty()) {
            throw new IllegalStateException("Eventos canônicos sem handler: " + faltando);
        }
    }

    /** Vazio quando o evento não tem handler — a borda responde erro controlado. */
    public Optional<FlowActionHandler> paraAcao(FlowAction acao) {
        return Optional.ofNullable(porAcao.get(acao));
    }
}
