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

        if (contem(texto, "cancelar", "excluir agendamento", "apagar agendamento", "desmarcar")) {
            return IntentType.CANCELAR_AGENDAMENTO;
        }

        if (contem(texto, "agendei", "agendou", "o que agendei", "quais agendamentos", "meus agendamentos",
                "qual meu agendamento", "qual agendamento", "agendamentos pendentes", "agendou mesmo", "meu agendamento")) {
            return IntentType.CONSULTAR_AGENDAMENTO;
        }

        if (contem(texto, "qual dia marquei", "que dia marquei", "que dia eu", "qual dia eu",
                "que dia agendei", "qual dia agendei", "marquei para quando", "agendei para quando")) {
            return IntentType.CONSULTAR_DIA_AGENDADO;
        }

        if (contem(texto, "que horario marquei", "qual horario marquei", "que horario eu",
                "qual horario eu", "que horario agendei", "qual horario agendei",
                "qual horario", "que horario")) {
            return IntentType.CONSULTAR_HORARIO_AGENDADO;
        }

        if (contem(texto, "o que eu marquei", "que servico marquei", "qual servico marquei",
                "o que agendei", "que servico agendei", "qual servico agendei",
                "qual servico", "que servico")) {
            return IntentType.CONSULTAR_SERVICO_AGENDADO;
        }

        if (contem(texto, "quero agendar outro", "outro agendamento", "novo agendamento",
                "agendar outra coisa", "posso agendar", "outra marcacao")) {
            return IntentType.NOVO_AGENDAMENTO;
        }

        if (contem(texto, "qual meu nome", "meu nome", "meu nome e", "sabe meu nome", "quem sou eu")) {
            return IntentType.CONSULTAR_NOME;
        }

        if (contem(texto, "lembra de mim", "voce lembra de mim", "me conhece", "sabe quem eu sou")) {
            return IntentType.LEMBRAR_CLIENTE;
        }

        if (contem(texto, "quais servicos", "que servicos", "o que voces fazem",
                "procedimentos", "servicos disponiveis", "o que voce faz", "o que faz", "o que voce oferece", "quais os servicos")) {
            return IntentType.CONSULTAR_SERVICOS;
        }

        if (contem(texto, "horario de funcionamento", "horario comercial", "que horas abre", "que horas fecha",
                "horario de atendimento", "funciona que horas", "horarios", "horario")) {
            return IntentType.CONSULTAR_HORARIOS;
        }

        if (contem(texto, "onde fica", "endereco", "localizacao", "como chegar", "qual o endereco")) {
            return IntentType.CONSULTAR_ENDERECO;
        }

        if (contem(texto, "quem e voce", "quem e vc", "voce e quem", "o que e troquim", "o que e o troquim")) {
            return IntentType.CONSULTAR_QUEM_SOU;
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
