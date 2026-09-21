package com.troquim_bot.conversation;

import com.troquim_bot.conversation.state.ConversationState;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

public final class ConversationNavigationPolicy {

    public sealed interface NavigationAction permits ResetToMenu, Back, NoOp {}

    public record ResetToMenu() implements NavigationAction {}

    public record Back() implements NavigationAction {}

    public record NoOp() implements NavigationAction {}

    public Optional<NavigationAction> interpretar(String mensagem, ConversationState state) {
        if (mensagem == null || mensagem.isBlank()) {
            return Optional.of(new ResetToMenu());
        }

        String texto = normalizar(mensagem);

        if (isComandoVoltar(texto)) {
            return Optional.of(new Back());
        }
        if (isComandoReinicio(texto)) {
            return Optional.of(new ResetToMenu());
        }

        return Optional.empty();
    }

    private boolean isComandoVoltar(String texto) {
        return texto.equals("volta")
                || texto.equals("voltar")
                || texto.equals("anterior")
                || texto.equals("passo anterior");
    }

    private boolean isComandoReinicio(String texto) {
        return texto.equals("oi")
                || texto.equals("ola")
                || texto.equals("menu")
                || texto.equals("menu principal")
                || texto.equals("inicio")
                || texto.equals("comecar novamente")
                || texto.equals("recomecar")
                || texto.equals("reiniciar")
                || texto.equals("voltar ao inicio")
                || texto.equals("ola bot")
                || texto.equals("oi bot")
                || texto.equals("bom dia")
                || texto.equals("boa tarde")
                || texto.equals("boa noite")
                || texto.equals("ola tudo bem")
                || texto.equals("oi tudo bem");
    }

    private String normalizar(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).trim();
    }
}
