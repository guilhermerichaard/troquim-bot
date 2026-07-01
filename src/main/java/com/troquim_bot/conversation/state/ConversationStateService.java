package com.troquim_bot.conversation.state;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConversationStateService {

    private static final Pattern HORARIO_PATTERN = Pattern.compile(
            "(?iu)(?:\\b(?:as|às)\\s*(\\d{1,2})(?::(\\d{2}))?\\b|\\b(\\d{1,2})(?:(?::(\\d{2}))|h(?:(\\d{2}))?)\\b)"
    );

    private final ConcurrentMap<String, ConversationState> states = new ConcurrentHashMap<>();

    public ConversationState buscarPorNumero(String numero) {
        return states.computeIfAbsent(chave(numero), ConversationState::new);
    }

    public ConversationState processarMensagem(String numero, String mensagem) {
        ConversationState state = buscarPorNumero(numero);

        if (mensagem == null || mensagem.isBlank()) {
            atualizarStep(state);
            return state;
        }

        ConversationStep stepAntes = state.getStep();
        String texto = normalizar(mensagem);

        if (!mensagemNeutra(texto)) {
            detectarServico(texto, state);
            detectarDia(texto, state);
            detectarHorario(mensagem, state);

            if (stepAntes == ConversationStep.AGUARDANDO_NOME) {
                detectarNome(mensagem, state);
            }
        }

        atualizarStep(state);

        return state;
    }

    public void atualizarServico(String numero, String servico) {
        ConversationState state = buscarPorNumero(numero);
        state.setServico(servico);
        atualizarStep(state);
    }

    public void atualizarDia(String numero, String dia) {
        ConversationState state = buscarPorNumero(numero);
        state.setDia(dia);
        atualizarStep(state);
    }

    public void atualizarHorario(String numero, String horario) {
        ConversationState state = buscarPorNumero(numero);
        state.setHorario(horario);
        atualizarStep(state);
    }

    public void atualizarNome(String numero, String nome) {
        ConversationState state = buscarPorNumero(numero);
        state.setNome(nome);
        atualizarStep(state);
    }

    public Optional<String> montarRespostaAutomatica(ConversationState state, String mensagem) {
        String texto = normalizar(mensagem);

        if (mensagemNeutra(texto)) {
            return Optional.of(montarRespostaParaContinuar(state));
        }

        return switch (state.getStep()) {
            case AGUARDANDO_SERVICO -> Optional.of("Claro. Qual serviço você gostaria de agendar?");
            case AGUARDANDO_DIA -> Optional.of("Perfeito. Para qual dia você gostaria?");
            case AGUARDANDO_HORARIO -> Optional.of(montarPerguntaHorario(state));
            case AGUARDANDO_NOME -> Optional.of("Perfeito. Como você prefere que eu te chame?");
            case AGUARDANDO_CONFIRMACAO -> Optional.of(montarConfirmacao(state));
            default -> Optional.empty();
        };
    }

    public String montarResumo(ConversationState state) {
        return String.join(System.lineSeparator(),
                "Estado atual do agendamento:",
                "Step: " + state.getStep(),
                "Conversa em andamento: " + (conversaEmAndamento(state) ? "sim" : "não"),
                "Próxima informação necessária: " + proximaInformacao(state),
                "Última pergunta feita: " + valorOuNaoInformado(state.getUltimaPergunta()),
                "Informações já coletadas:",
                "Serviço: " + valorOuNaoInformado(state.getServico()),
                "Dia: " + valorOuNaoInformado(state.getDia()),
                "Horário: " + valorOuNaoInformado(state.getHorario()),
                "Nome: " + valorOuNaoInformado(state.getNome())
        );
    }

    public boolean conversaEmAndamento(ConversationState state) {
        return !estaVazio(state.getServico())
                || !estaVazio(state.getDia())
                || !estaVazio(state.getHorario())
                || !estaVazio(state.getNome());
    }

    public void limparEstado(String numero) {
        states.remove(chave(numero));
    }

    private void detectarServico(String texto, ConversationState state) {
        if (contem(texto, "pe e mao", "pé e mão")) {
            state.setServico("pé e mão");
            return;
        }

        if (contem(texto, "manicure")) {
            state.setServico("manicure");
            return;
        }

        if (contem(texto, "pedicure")) {
            state.setServico("pedicure");
            return;
        }

        if (contem(texto, "unha", "mao", "mão")) {
            state.setServico("unha");
            return;
        }

        if (contemPalavra(texto, "pe")) {
            state.setServico("pé");
            return;
        }

        if (contem(texto, "cabelo", "corte", "escova", "progressiva")) {
            state.setServico("cabelo");
            return;
        }

        if (contem(texto, "sobrancelha")) {
            state.setServico("sobrancelha");
            return;
        }

        if (contem(texto, "cilios", "cílios")) {
            state.setServico("cílios");
            return;
        }

        if (contem(texto, "maquiagem")) {
            state.setServico("maquiagem");
        }
    }

    private void detectarDia(String texto, ConversationState state) {
        if (contem(texto, "segunda")) {
            state.setDia("segunda");
        } else if (contem(texto, "terca", "terça")) {
            state.setDia("terça");
        } else if (contem(texto, "quarta")) {
            state.setDia("quarta");
        } else if (contem(texto, "quinta")) {
            state.setDia("quinta");
        } else if (contem(texto, "sexta")) {
            state.setDia("sexta");
        } else if (contem(texto, "sabado", "sábado")) {
            state.setDia("sábado");
        } else if (contem(texto, "domingo")) {
            state.setDia("domingo");
        } else if (contem(texto, "hoje")) {
            state.setDia("hoje");
        } else if (contem(texto, "amanha", "amanhã")) {
            state.setDia("amanhã");
        }
    }

    private void detectarHorario(String mensagem, ConversationState state) {
        Matcher matcher = HORARIO_PATTERN.matcher(mensagem);

        if (matcher.find()) {
            String hora = primeiroValor(matcher.group(1), matcher.group(3));
            String minuto = primeiroValor(matcher.group(2), matcher.group(4), matcher.group(5));

            if (hora != null) {
                state.setHorario(formatarHorario(hora, minuto));
            }
        }
    }

    private void detectarNome(String mensagem, ConversationState state) {
        String nome = extrairNome(mensagem);

        if (nome != null) {
            state.setNome(nome);
        }
    }

    private String extrairNome(String mensagem) {
        String texto = mensagem.trim();
        String normalizado = normalizar(texto);

        if (normalizado.startsWith("meu nome e ")) {
            texto = texto.substring(11).trim();
        } else if (normalizado.startsWith("me chamo ")) {
            texto = texto.substring(9).trim();
        } else if (normalizado.startsWith("sou ")) {
            texto = texto.substring(4).trim();
        } else if (normalizado.startsWith("e ")) {
            texto = texto.substring(2).trim();
        }

        if (texto.length() < 2 || texto.length() > 60) {
            return null;
        }

        if (texto.matches(".*\\d.*")) {
            return null;
        }

        if (nomeBloqueado(normalizar(texto))) {
            return null;
        }

        return texto;
    }

    private void atualizarStep(ConversationState state) {
        if (estaVazio(state.getServico())) {
            state.setStep(ConversationStep.AGUARDANDO_SERVICO);
        } else if (estaVazio(state.getDia())) {
            state.setStep(ConversationStep.AGUARDANDO_DIA);
        } else if (estaVazio(state.getHorario())) {
            state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        } else if (estaVazio(state.getNome())) {
            state.setStep(ConversationStep.AGUARDANDO_NOME);
        } else {
            state.setStep(ConversationStep.AGUARDANDO_CONFIRMACAO);
        }

        state.setUltimaPergunta(montarUltimaPergunta(state));
    }

    private String montarUltimaPergunta(ConversationState state) {
        return switch (state.getStep()) {
            case AGUARDANDO_SERVICO -> "Qual serviço você gostaria de agendar?";
            case AGUARDANDO_DIA -> "Para qual dia você gostaria?";
            case AGUARDANDO_HORARIO -> montarPerguntaHorario(state);
            case AGUARDANDO_NOME -> "Como você prefere que eu te chame?";
            case AGUARDANDO_CONFIRMACAO -> "Confirmar que a solicitação foi recebida e será validada.";
            default -> "";
        };
    }

    private String montarRespostaParaContinuar(ConversationState state) {
        return switch (state.getStep()) {
            case AGUARDANDO_SERVICO -> "Me fala qual serviço você quer agendar.";
            case AGUARDANDO_DIA -> "Me fala para qual dia você gostaria.";
            case AGUARDANDO_HORARIO -> "Me fala o horário que você prefere.";
            case AGUARDANDO_NOME -> "Me fala como você prefere que eu te chame.";
            case AGUARDANDO_CONFIRMACAO -> montarConfirmacao(state);
            default -> "Me fala como posso ajudar.";
        };
    }

    private String montarPerguntaHorario(ConversationState state) {
        if (estaVazio(state.getDia())) {
            return "Certo. Qual horário você prefere?";
        }

        return "Certo. Qual horário você prefere na " + state.getDia() + "?";
    }

    private String montarConfirmacao(ConversationState state) {
        return "Perfeito, " + state.getNome()
                + ". Recebi sua solicitação para " + state.getServico()
                + " na " + state.getDia()
                + " às " + state.getHorario()
                + ". Vou verificar a disponibilidade e retorno com a confirmação.";
    }

    private String proximaInformacao(ConversationState state) {
        return switch (state.getStep()) {
            case AGUARDANDO_SERVICO -> "serviço desejado";
            case AGUARDANDO_DIA -> "dia desejado";
            case AGUARDANDO_HORARIO -> "horário desejado";
            case AGUARDANDO_NOME -> "nome da cliente";
            case AGUARDANDO_CONFIRMACAO -> "confirmar solicitação";
            case FINALIZADO -> "nenhuma";
            default -> "iniciar atendimento";
        };
    }

    private boolean mensagemNeutra(String texto) {
        return switch (texto) {
            case "sim", "isso", "pode ser", "ok", "ta", "ta bom", "tá", "tá bom", "certo", "beleza", "perfeito" -> true;
            default -> false;
        };
    }

    private boolean nomeBloqueado(String nomeNormalizado) {
        return switch (nomeNormalizado) {
            case "obrigado", "obrigada", "valeu", "tchau", "ok", "sim", "isso", "pode ser", "nao", "não", "beleza", "certo", "perfeito", "oi", "ola", "olá", "bom dia", "boa tarde", "boa noite" -> true;
            default -> false;
        };
    }

    private String formatarHorario(String hora, String minuto) {
        int horaInt = Integer.parseInt(hora);

        if (minuto == null || minuto.isBlank()) {
            return horaInt + "h";
        }

        return horaInt + ":" + minuto;
    }

    private String valorOuNaoInformado(String valor) {
        return estaVazio(valor) ? "não informado" : valor;
    }

    private String primeiroValor(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }

        return null;
    }

    private boolean contem(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(normalizar(termo))) {
                return true;
            }
        }

        return false;
    }

    private boolean contemPalavra(String texto, String termo) {
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(normalizar(termo)) + "\\b");
        return pattern.matcher(texto).find();
    }

    private boolean estaVazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String normalizar(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return semAcentos.toLowerCase(Locale.ROOT);
    }

    private String chave(String numero) {
        return numero == null ? "" : numero;
    }
}
