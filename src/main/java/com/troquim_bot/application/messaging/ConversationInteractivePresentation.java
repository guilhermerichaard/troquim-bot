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
    private static final Pattern EXPLICIT_OPTION =
            Pattern.compile("^\\[\\[choice:([^|]+)\\|(.+)]]$");

    private ConversationInteractivePresentation() {
    }

    public static Optional<Presentation> from(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return Optional.empty();
        }

        if (responseText.contains("1) Agendar")
                && responseText.contains("2) Meus agendamentos")
                && responseText.contains("3) Cancelar")) {
            List<OutboundInteractiveOption> options = List.of(
                    new OutboundInteractiveOption("menu_agendar", "Agendar"),
                    new OutboundInteractiveOption("menu_meus_agendamentos", "Meus agendamentos"),
                    new OutboundInteractiveOption("menu_cancelar", "Cancelar"));
            return Optional.of(Presentation.buttons(interactiveBody(responseText, options), options));
        }

        if (responseText.contains("1) Sim")
                && (responseText.contains("2) Nao") || responseText.contains("2) Não"))) {
            List<OutboundInteractiveOption> options = List.of(
                    new OutboundInteractiveOption("confirmar_sim", "Sim"),
                    new OutboundInteractiveOption("confirmar_nao", "Não"),
                    new OutboundInteractiveOption("nav_voltar", "Voltar"));
            return Optional.of(Presentation.buttons(interactiveBody(responseText, options), options));
        }

        if (responseText.contains("1) Confirmar") && responseText.contains("2) Cancelar")) {
            List<OutboundInteractiveOption> options = List.of(
                    new OutboundInteractiveOption("confirmar_sim", "Confirmar"),
                    new OutboundInteractiveOption("confirmar_nao", "Cancelar"),
                    new OutboundInteractiveOption("nav_voltar", "Voltar"));
            return Optional.of(Presentation.buttons(interactiveBody(responseText, options), options));
        }

        List<OutboundInteractiveOption> choices = selectableOptions(responseText);
        if (choices.size() == 1 && "nav_voltar".equals(choices.get(0).id())) {
            return Optional.of(Presentation.buttons(
                    interactiveBody(responseText, choices), choices));
        }
        if (choices.size() >= 2 && choices.size() <= 10) {
            return Optional.of(Presentation.list(interactiveBody(responseText, choices), choices));
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
            case "nav_voltar" -> "voltar";
            default -> interactiveId;
        };
    }

    private static String interactiveBody(String responseText,
                                          List<OutboundInteractiveOption> options) {
        java.util.Set<String> titles = options.stream()
                .map(option -> option.title().trim().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        java.util.ArrayList<String> kept = new java.util.ArrayList<>();
        for (String line : responseText.split("\\R")) {
            String trimmed = line.trim();
            Matcher matcher = NUMBERED_OPTION.matcher(trimmed);
            if (matcher.matches()) {
                String title = matcher.group(2).trim().toLowerCase(java.util.Locale.ROOT);
                if (titles.contains(title)) {
                    continue;
                }
            }
            if (EXPLICIT_OPTION.matcher(trimmed).matches()) {
                continue;
            }

            String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("digite o numero")
                    || lower.startsWith("digite apenas o numero")
                    || lower.startsWith("digite, por exemplo")) {
                continue;
            }
            kept.add(line);
        }

        String cleaned = String.join("\n", kept)
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return cleaned.isBlank() ? responseText : cleaned;
    }

    private static List<OutboundInteractiveOption> selectableOptions(String responseText) {
        ArrayList<OutboundInteractiveOption> options = new ArrayList<>();
        for (String line : responseText.split("\\R")) {
            String trimmed = line.trim();

            Matcher numbered = NUMBERED_OPTION.matcher(trimmed);
            if (numbered.matches()) {
                String id = numbered.group(1);
                String title = numbered.group(2).trim();
                if (!title.isBlank()) {
                    options.add(new OutboundInteractiveOption(id, title));
                }
                continue;
            }

            Matcher explicit = EXPLICIT_OPTION.matcher(trimmed);
            if (explicit.matches()) {
                String id = explicit.group(1).trim();
                String title = explicit.group(2).trim();
                if (!id.isBlank() && !title.isBlank()) {
                    options.add(new OutboundInteractiveOption(id, title));
                }
            }
        }
        return List.copyOf(options);
    }

    public enum Type {
        BUTTONS,
        LIST
    }

    public record Presentation(Type type, String text,
                               List<OutboundInteractiveOption> options) {
        public Presentation {
            options = List.copyOf(options);
        }

        public static Presentation buttons(String text,
                                           List<OutboundInteractiveOption> options) {
            return new Presentation(Type.BUTTONS, text, options);
        }

        public static Presentation list(String text,
                                        List<OutboundInteractiveOption> options) {
            return new Presentation(Type.LIST, text, options);
        }
    }
}
