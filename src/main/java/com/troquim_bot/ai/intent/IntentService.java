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

        if (contem(texto, "atendente", "humano", "pessoa", "falar com alguem", "falar com guilherme")) {
            return IntentType.HUMANO;
        }

        if (contem(texto, "o que agendei", "quais agendamentos", "meus agendamentos",
                "qual dia marquei", "que dia marquei", "que horario", "qual horario",
                "qual agendamento", "agendamentos pendentes", "agendou", "confirmou",
                "foi agendado", "foi marcado", "marcou mesmo", "agendou mesmo")) {
            return IntentType.CONSULTAR_AGENDAMENTO;
        }

        if (contem(texto, "quero agendar outro", "outro agendamento", "novo agendamento",
                "agendar outra coisa", "posso agendar", "outra marcacao")) {
            return IntentType.NOVO_AGENDAMENTO;
        }

        if (contem(texto, "qual meu nome", "meu nome", "meu nome e", "sabe meu nome")) {
            return IntentType.CONSULTAR_NOME;
        }

        if (contem(texto, "lembra de mim", "voce lembra de mim", "me conhece", "sabe quem eu sou")) {
            return IntentType.LEMBRAR_CLIENTE;
        }

        if (contem(texto, "quais servicos", "que servicos", "o que voces fazem",
                "procedimentos", "servicos disponiveis")) {
            return IntentType.CONSULTAR_SERVICOS;
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
            if (texto.contains(normalizar(termo))) {
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
