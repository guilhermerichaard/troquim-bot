package com.troquim_bot.whatsapp.flow.api;

/**
 * Envelope cifrado recebido da Meta. Único DTO de transporte do endpoint — os três
 * campos são base64 e nada aqui é interpretado como dado de negócio.
 *
 * Sem anotações de binding: o corpo é lido BRUTO e desserializado explicitamente pelo
 * controller, como no webhook da Cloud API. Isso evita depender de qual Jackson o
 * conversor de mensagens do Spring está usando e mantém o mapeamento de erro sob nosso
 * controle (JSON inválido vira 400, não exceção de framework).
 */
public record EncryptedFlowEnvelope(String encryptedFlowData,
                                    String encryptedAesKey,
                                    String initialVector) {
}
