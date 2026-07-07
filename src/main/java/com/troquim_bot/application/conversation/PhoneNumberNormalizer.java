package com.troquim_bot.application.conversation;

/**
 * Utilitário para normalizar números de telefone recebidos de canais de mensageria.
 * 
 * Responsabilidade exclusiva: remover identificadores de canal (como sufixos do WhatsApp)
 * ANTES que o número chegue ao domínio.
 * 
 * Uso exclusivo na camada de integração (adapters). Não deve ser usado no domínio.
 */
public final class PhoneNumberNormalizer {

    private PhoneNumberNormalizer() {
        // Utility class
    }

    /**
     * Remove identificadores de canal de um número de telefone.
     * 
     * Exemplos:
     * - "5511916698055@s.whatsapp.net" → "5511916698055"
     * - "5511916698055@c.us"           → "5511916698055"
     * - "5511916698055"                → "5511916698055"
     * - "5511916698055@whatsapp.net"   → "5511916698055"
     * 
     * @param raw Número bruto recebido do canal
     * @return Número limpo, ou null se raw for null
     */
    public static String normalizar(String raw) {
        if (raw == null) {
            return null;
        }
        int atIndex = raw.indexOf('@');
        return atIndex >= 0 ? raw.substring(0, atIndex) : raw;
    }
}