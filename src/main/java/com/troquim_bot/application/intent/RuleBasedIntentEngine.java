package com.troquim_bot.application.intent;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class RuleBasedIntentEngine implements IntentEngine {

    @Override
    public IntentResult classify(String message) {
        String text = normalize(message);
        if (text.isBlank()) {
            return result(IntentType.UNKNOWN);
        }

        if (hasAny(text, "cancelar", "desmarcar")) {
            return result(IntentType.CANCEL_APPOINTMENT);
        }
        if (hasAny(text, "remarcar", "trocar horario")) {
            return result(IntentType.RESCHEDULE_APPOINTMENT);
        }
        if (hasAny(text, "tem horario", "disponivel", "disponibilidade", "horarios disponiveis")) {
            return result(IntentType.CHECK_AVAILABILITY);
        }
        if (hasAny(text, "atendente", "humano", "pessoa")) {
            return result(IntentType.HUMAN_ATTENDANT);
        }
        if (hasAny(text, "servicos", "precos", "valor", "quais servicos", "que servicos", "o que voce faz", "o que faz", "o que voce oferece", "quais os servicos")) {
            return result(IntentType.ASK_SERVICES);
        }
        if (hasAny(text, "horario de funcionamento", "horario comercial", "que horas abre", "que horas fecha", "horario de atendimento", "funciona que horas", "horarios")) {
            return result(IntentType.ASK_HOURS);
        }
        if (hasAny(text, "onde fica", "endereco", "localizacao", "como chegar", "qual o endereco")) {
            return result(IntentType.ASK_LOCATION);
        }
        if (hasAny(text, "quem e voce", "quem e vc", "voce e quem", "o que e troquim", "o que e o troquim")) {
            return result(IntentType.ASK_WHO_ARE_YOU);
        }
        if (hasAny(text, "agendar", "marcar horario", "agendar horario", "marcar um horario", "quero agendar", "quero marcar horario", "preciso agendar")) {
            return result(IntentType.BOOK_APPOINTMENT);
        }
        if (hasAny(text, "obrigado", "obrigada", "agradeco", "valeu")) {
            return result(IntentType.AGRADECIMENTO);
        }
        if (hasAny(text, "tchau", "ate logo", "ate mais", "adeus", "falou")) {
            return result(IntentType.DESPEDIDA);
        }
        if (hasAny(text, "lembra de mim", "voce lembra de mim", "me conhece", "sabe quem eu sou")) {
            return result(IntentType.LEMBRAR_CLIENTE);
        }
        if (hasAny(text, "qual meu agendamento", "qual agendamento", "agendamentos pendentes", "agendou mesmo", "quero ver meu agendamento")) {
            return result(IntentType.CONSULTAR_AGENDAMENTO);
        }
        if (hasAny(text, "que dia agendei", "qual dia agendei", "marquei para quando", "agendei para quando")) {
            return result(IntentType.CONSULTAR_DIA_AGENDADO);
        }
        if (hasAny(text, "qual horario", "que horario", "horario e", "preciso saber o horario")) {
            return result(IntentType.CONSULTAR_HORARIO_AGENDADO);
        }
        if (hasAny(text, "qual servico", "que servico", "qual serviço", "que serviço")) {
            return result(IntentType.CONSULTAR_SERVICO_AGENDADO);
        }
        if (hasAny(text, "qual meu nome", "meu nome", "meu nome e", "sabe meu nome")) {
            return result(IntentType.CONSULTAR_NOME);
        }
        if (hasAny(text, "oi", "ola", "bom dia", "boa tarde", "boa noite")) {
            return result(IntentType.GREETING);
        }

        return result(IntentType.UNKNOWN);
    }

    private IntentResult result(IntentType type) {
        return new IntentResult(type);
    }

    private boolean hasAny(String text, String... terms) {
        for (String term : terms) {
            if (hasTerm(text, term)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTerm(String text, String term) {
        return (" " + text + " ").contains(" " + term + " ");
    }

    private String normalize(String message) {
        if (message == null) {
            return "";
        }

        String withoutAccents = Normalizer.normalize(message, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");

        return withoutAccents
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }
}
