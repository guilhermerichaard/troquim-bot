package com.troquim_bot.ai.intent;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class IntentService {

    public IntentType classificar(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return IntentType.DESCONHECIDO;
        }

        String texto = normalizar(mensagem);

        if (contem(texto, "atendente", "humano", "pessoa", "guilherme", "falar com alguem")) {
            return IntentType.HUMANO;
        }

        if (contem(texto, "obrigado", "obrigada", "agradeco", "valeu")) {
            return IntentType.AGRADECIMENTO;
        }

        if (contem(texto, "tchau", "ate logo", "ate mais", "adeus", "falou")) {
            return IntentType.DESPEDIDA;
        }

        if (contem(texto, "orcamento", "valor", "preco", "quanto custa", "cotacao")) {
            return IntentType.ORCAMENTO;
        }

        if (contem(texto, "agendar", "agenda", "marcar", "horario", "visita")) {
            return IntentType.AGENDAMENTO;
        }

        if (contem(texto, "oi", "ola", "bom dia", "boa tarde", "boa noite")) {
            return IntentType.SAUDACAO;
        }

        return IntentType.DESCONHECIDO;
    }

    private boolean contem(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) {
                return true;
            }
        }

        return false;
    }

    private String normalizar(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return semAcentos.toLowerCase(Locale.ROOT);
    }
}
