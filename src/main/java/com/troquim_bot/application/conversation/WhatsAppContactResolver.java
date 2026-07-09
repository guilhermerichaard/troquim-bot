package com.troquim_bot.application.conversation;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Resolvedor de contato WhatsApp que protege contra instabilidades do remoteJid
 * com @lid na Evolution API 2.3.7.
 * <p>
 * Para identificação de contato de entrada, a prioridade é:
 * <ol>
 *   <li>remoteJidAlt se existir e terminar com @s.whatsapp.net</li>
 *   <li>remoteJid se terminar com @s.whatsapp.net</li>
 *   <li>remoteJid se já for número puro (sem @)</li>
 *   <li>sender apenas como fallback</li>
 * </ol>
 * Para envio, sempre retorna número puro (apenas dígitos).
 * <p>
 * Uso exclusivo na camada de integração (adapters). Não deve ser usado no domínio.
 */
public final class WhatsAppContactResolver {

    private static final String SUFFIX_WHATSAPP = "@s.whatsapp.net";
    private static final String SUFFIX_LID = "@lid";

    private WhatsAppContactResolver() {
        // Utility class
    }

    /**
     * Resolve o número do contato de entrada a partir do payload JSON da Evolution API.
     *
     * @param root nó raiz do payload JSON
     * @return número de telefone limpo (apenas dígitos), ou null se nenhum for encontrado
     */
    public static String resolveContactNumber(JsonNode root) {
        // 1. Tentar remoteJidAlt se existir e terminar com @s.whatsapp.net
        String remoteJidAlt = extractRemoteJidAlt(root);
        if (remoteJidAlt != null && remoteJidAlt.endsWith(SUFFIX_WHATSAPP)) {
            return stripSuffix(remoteJidAlt);
        }

        // 2. Tentar remoteJid
        String remoteJid = extractRemoteJid(root);
        if (remoteJid != null) {
            // 2a. Se terminar com @s.whatsapp.net, extrair número
            if (remoteJid.endsWith(SUFFIX_WHATSAPP)) {
                return stripSuffix(remoteJid);
            }
            // 2b. Se for @lid, tentar extrair apenas dígitos
            if (remoteJid.endsWith(SUFFIX_LID)) {
                String digitsOnly = extractDigits(remoteJid);
                if (digitsOnly != null && !digitsOnly.isEmpty()) {
                    return digitsOnly;
                }
                // Se não extraiu dígitos, não usa remoteJid - continua para fallback
            }
            // 2c. Se já for número puro (sem @), retornar
            if (!remoteJid.contains("@")) {
                return remoteJid;
            }
        }

        // 3. Fallback para sender
        String sender = extractSender(root);
        if (sender != null) {
            String digits = extractDigits(sender);
            if (digits != null && !digits.isEmpty()) {
                return digits;
            }
        }

        return null;
    }

    /**
     * Normaliza um número para envio, removendo qualquer sufixo de canal e
     * caracteres não numéricos.
     *
     * @param number número bruto (pode conter @s.whatsapp.net, @lid, etc.)
     * @return número contendo apenas dígitos, ou null se a entrada for null
     */
    public static String normalizeForOutgoing(String number) {
        if (number == null) {
            return null;
        }
        return extractDigits(number);
    }

    // ---- Métodos de extração do payload JSON ----

    static String extractRemoteJid(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isMissingNode()) {
            return null;
        }
        JsonNode key = data.path("key");
        if (key.isMissingNode()) {
            return null;
        }
        String remoteJid = key.path("remoteJid").asText(null);
        return (remoteJid == null || remoteJid.isBlank()) ? null : remoteJid.trim();
    }

    static String extractRemoteJidAlt(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isMissingNode()) {
            return null;
        }
        String remoteJidAlt = data.path("remoteJidAlt").asText(null);
        return (remoteJidAlt == null || remoteJidAlt.isBlank()) ? null : remoteJidAlt.trim();
    }

    static String extractSender(JsonNode root) {
        String sender = root.path("sender").asText(null);
        return (sender == null || sender.isBlank()) ? null : sender.trim();
    }

    // ---- Métodos utilitários ----

    /**
     * Remove qualquer sufixo após '@' de um JID.
     */
    static String stripSuffix(String jid) {
        if (jid == null) {
            return null;
        }
        int atIndex = jid.indexOf('@');
        return atIndex >= 0 ? jid.substring(0, atIndex) : jid;
    }

    /**
     * Extrai apenas caracteres numéricos de uma string.
     */
    static String extractDigits(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        return digits.toString();
    }
}