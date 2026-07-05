package com.troquim_bot.conversation.language;

import java.text.Normalizer;
import java.util.Locale;

public class ConversationTextUtils {

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return semAcentos.toLowerCase(Locale.ROOT);
    }

    public static boolean contem(String texto, String... termos) {
        if (texto == null) {
            return false;
        }
        String textoNormalizado = normalizar(texto);
        for (String termo : termos) {
            if (textoNormalizado.contains(normalizar(termo))) {
                return true;
            }
        }
        return false;
    }

    public static boolean contemPalavra(String texto, String termo) {
        if (texto == null || termo == null) {
            return false;
        }

        String textoNormalizado = normalizar(texto).trim();
        String termoNormalizado = normalizar(termo).trim();

        if (textoNormalizado.isEmpty() || termoNormalizado.isEmpty()) {
            return false;
        }

        if (termoNormalizado.contains(" ")) {
            return false;
        }

        if (textoNormalizado.contains(" e ")) {
            return false;
        }

        return (" " + textoNormalizado + " ").contains(" " + termoNormalizado + " ");
    }

    public static boolean estaVazio(String valor) {
        return valor == null || valor.isBlank();
    }
}