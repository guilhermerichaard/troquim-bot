package com.troquim_bot.application.messaging;

import java.util.List;

/**
 * Resultado neutro do parsing de um payload de webhook.
 *
 * {@code textMessages} traz apenas mensagens de TEXTO já convertidas ao contrato
 * interno. Eventos de status ou tipos não suportados NÃO viram itens aqui — eles
 * são reconhecidos ({@code recognizedEvent = true}) porém não geram ação de negócio.
 *
 * @param recognizedEvent true se o payload pertence ao objeto esperado do provedor
 *                        (ex: whatsapp_business_account), mesmo sem mensagens de texto
 * @param textMessages    mensagens de texto neutras (pode ser vazio)
 */
public record ParsedInboundPayload(boolean recognizedEvent, List<InboundTextMessage> textMessages) {

    public ParsedInboundPayload {
        textMessages = textMessages == null ? List.of() : List.copyOf(textMessages);
    }

    public static ParsedInboundPayload ignored() {
        return new ParsedInboundPayload(false, List.of());
    }
}
