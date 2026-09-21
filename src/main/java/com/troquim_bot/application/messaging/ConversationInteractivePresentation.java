package com.troquim_bot.application.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single Source of Truth da apresentacao interativa da conversa.
 *
 * Nao cria regra de negocio: apenas reconhece decisoes que a resposta textual
 * canonica ja tornou explicitas. Se nenhum provider suportar interativo, o texto
 * continua completo e utilizavel.
 */
public final class ConversationInteractivePresentation {

    private static final Pattern NUMBERED_OPTION = Pattern.compile("^(\\d+)\\)\\s+(.+)$");

    private ConversationInteractivePresentation() {
    }

    public static Optional<Presentation> from(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return Optional.empty();
        }

        if (responseText.contains("1) Agendar")
                && responseText.contains("2) Meus agendamentos")
                && responseText.contains("3) Cancelar")) {
            return Optional.of(Presentation.buttons(List.of(
                    new OutboundInteractiveOption("menu_agendar", "Agendar"),
                    new OutboundInteractiveOption("menu_meus_agendamentos", "Meus agendamentos"),
                    new OutboundInteractiveOption("menu_cancelar", "Cancelar"))));
        }

        if ((responseText.contains("1) Sim") && responseText.contains("2) Nao"))
                || (responseText.contains("1) Confirmar") && responseText.contains("2) Cancelar"))) {
            return Optional.of(Presentation.buttons(List.of(
                    new OutboundInteractiveOption("confirmar_sim", "Confirmar"),
                    new OutboundInteractiveOption("confirmar_nao", "Cancelar"))));
        }

        List<OutboundInteractiveOption> numbered = numberedOptions(responseText);
        if (numbered.size() >= 2 && numbered.size() <= 10) {
            return Optional.of(Presentation.list(numbered));
        }

        return Optional.empty();
    }

    /**
     * IDs de botoes fixos sao traduzidos para os mesmos comandos que a conversa ja
     * entende. IDs numericos de listas passam intactos.
     */
    public static String canonicalInput(String interactiveId) {
        if (interactiveId == null || interactiveId.isBlank()) {
            return interactiveId;
        }
        return switch (interactiveId) {
            case "menu_agendar" -> "1";
            case "menu_meus_agendamentos" -> "2";
            case "menu_cancelar" -> "3";
            case "confirmar_sim" -> "1";
            case "confirmar_nao" -> "2";
            default -> interactiveId;
        };
    }

    private static List<OutboundInteractiveOption> numberedOptions(String responseText) {
        ArrayList<OutboundInteractiveOption> options = new ArrayList<>();
        for (String line : responseText.split("\\R")) {
            Matcher matcher = NUMBERED_OPTION.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            String id = matcher.group(1);
            String title = matcher.group(2).trim();
            if (!title.isBlank()) {
                options.add(new OutboundInteractiveOption(id, title));
            }
        }
        return List.copyOf(options);
    }

    public enum Type {
        BUTTONS,
        LIST
    }

    public record Presentation(Type type, List<OutboundInteractiveOption> options) {
        public Presentation {
            options = List.copyOf(options);
        }

        public static Presentation buttons(List<OutboundInteractiveOption> options) {
            return new Presentation(Type.BUTTONS, options);
        }

        public static Presentation list(List<OutboundInteractiveOption> options) {
            return new Presentation(Type.LIST, options);
        }
    }
}
