package com.troquim_bot.conversation.state;

import com.troquim_bot.ai.intent.IntentType;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConversationStateService {

    private static final Pattern HORARIO_PATTERN = Pattern.compile(
            "(?iu)(?:\\b(?:as|às)\\s*(\\d{1,2})(?::(\\d{2}))?\\b|\\b(\\d{1,2})(?::(\\d{2}))?\\b|\\b(\\d{1,2})\\s*(?:h(?:oras?)?\\b)?)"
    );

    private final ConcurrentMap<String, ConversationState> states = new ConcurrentHashMap<>();

    public ConversationState buscarPorNumero(String numero) {
        return states.computeIfAbsent(chave(numero), ConversationState::new);
    }

    public ConversationState processarMensagem(String numero, String mensagem) {
        ConversationState state = buscarPorNumero(numero);

        // Se estado está FINALIZADO, verifica se deve reiniciar
        if (state.getStep() == ConversationStep.FINALIZADO) {
            if (deveReiniciarConversa(mensagem)) {
                limparEstado(numero);
                state = buscarPorNumero(numero);
            } else {
                atualizarStep(state);
                return state;
            }
        }

        if (mensagem == null || mensagem.isBlank()) {
            atualizarStep(state);
            return state;
        }

        ConversationStep stepAntes = state.getStep();
        String texto = normalizar(mensagem);

        // Se for mensagem de lembrete ("já falei", "esqueceu?", etc.), não processar novamente
        if (isMensagemLembranca(texto)) {
            atualizarStep(state);
            return state;
        }

        // Se for pergunta sobre agendamentos, não processar como novo agendamento
        if (isPerguntaSobreAgendamentos(texto)) {
            atualizarStep(state);
            return state;
        }

        if (!mensagemNeutra(texto)) {
            // Se está em FINALIZADO e recebeu mensagem de novo agendamento, cria novo draft
            if (state.getStep() == ConversationStep.FINALIZADO && deveCriarNovoAgendamento(texto)) {
                state.criarNovoDraft();
            }
            
            AppointmentDraft draftAtual = state.getDraftAtual();
            if (draftAtual == null) {
                draftAtual = state.criarNovoDraft();
            }

            detectarServico(texto, draftAtual);
            detectarDia(texto, draftAtual);
            detectarHorario(mensagem, draftAtual);

            // Só detecta nome se não tiver nome ainda E se estiver no step correto
            if (estaVazio(draftAtual.getNome()) && stepAntes == ConversationStep.AGUARDANDO_NOME) {
                detectarNome(mensagem, draftAtual);
            }
        }

        atualizarStep(state);

        return state;
    }

    public void atualizarServico(String numero, String servico) {
        ConversationState state = buscarPorNumero(numero);
        AppointmentDraft draft = state.getDraftAtual();
        if (draft == null) {
            draft = state.criarNovoDraft();
        }
        draft.setServico(servico);
        atualizarStep(state);
    }

    public void atualizarDia(String numero, String dia) {
        ConversationState state = buscarPorNumero(numero);
        AppointmentDraft draft = state.getDraftAtual();
        if (draft == null) {
            draft = state.criarNovoDraft();
        }
        draft.setDia(dia);
        atualizarStep(state);
    }

    public void atualizarHorario(String numero, String horario) {
        ConversationState state = buscarPorNumero(numero);
        AppointmentDraft draft = state.getDraftAtual();
        if (draft == null) {
            draft = state.criarNovoDraft();
        }
        draft.setHorario(horario);
        atualizarStep(state);
    }

    public void atualizarNome(String numero, String nome) {
        ConversationState state = buscarPorNumero(numero);
        AppointmentDraft draft = state.getDraftAtual();
        if (draft == null) {
            draft = state.criarNovoDraft();
        }
        draft.setNome(nome);
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
            case AGUARDANDO_CONFIRMACAO, FINALIZADO -> Optional.of(montarRespostaPosAgendamento(state, mensagem));
            default -> Optional.empty();
        };
    }
    
    public Optional<String> montarRespostaPorIntencao(ConversationState state, String mensagem, IntentType intentType) {
        if (intentType == IntentType.CONSULTAR_AGENDAMENTO) {
            AppointmentDraft draft = state.getDraftAtual();
            if (draft != null && draft.isCompleto()) {
                return Optional.of("Você solicitou um agendamento de " + draft.getServico()
                        + " para " + draft.getDia()
                        + " às " + draft.getHorario()
                        + ". Ainda estou aguardando a confirmação do salão.");
            }
            return Optional.of("Você ainda não tem um agendamento ativo. Qual serviço você gostaria de agendar?");
        }
        
        if (intentType == IntentType.NOVO_AGENDAMENTO) {
            return Optional.of("Sem problemas! Vamos registrar outro agendamento.\n\nQual serviço você gostaria de agendar?");
        }
        
        if (intentType == IntentType.CONSULTAR_NOME) {
            AppointmentDraft draft = state.getDraftAtual();
            if (draft != null && draft.getNome() != null) {
                return Optional.of("Seu nome está salvo como " + draft.getNome() + ".");
            }
            return Optional.of("Você ainda não informou seu nome. Como prefere que eu te chame?");
        }
        
        if (intentType == IntentType.CONSULTAR_SERVICOS) {
            return Optional.of("Por enquanto consigo registrar solicitações de serviços de beleza, como unha, sobrancelha, cabelo e cílios. Qual deles você gostaria de agendar?");
        }
        
        return Optional.empty();
    }

    public String montarResumo(ConversationState state) {
        AppointmentDraft draft = state.getDraftAtual();
        
        return String.join(System.lineSeparator(),
                "Serviço: " + valorOuNaoInformado(draft != null ? draft.getServico() : null),
                "Dia: " + valorOuNaoInformado(draft != null ? draft.getDia() : null),
                "Horário: " + valorOuNaoInformado(draft != null ? draft.getHorario() : null),
                "Nome: " + valorOuNaoInformado(draft != null ? draft.getNome() : null),
                "Próxima etapa: " + proximaEtapa(state)
        );
    }

    public boolean conversaEmAndamento(ConversationState state) {
        AppointmentDraft draft = state.getDraftAtual();
        if (draft == null) {
            return false;
        }
        
        return !estaVazio(draft.getServico())
                || !estaVazio(draft.getDia())
                || !estaVazio(draft.getHorario())
                || !estaVazio(draft.getNome());
    }

    public void limparEstado(String numero) {
        states.remove(chave(numero));
    }

    public String listarAgendamentosPendentes(ConversationState state) {
        List<AppointmentDraft> pendentes = state.getDraftsPendentes();
        
        if (pendentes.isEmpty()) {
            return "Você não tem agendamentos pendentes no momento.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Você tem ").append(pendentes.size()).append(" agendamento(s) pendente(s):\n\n");
        
        for (int i = 0; i < pendentes.size(); i++) {
            AppointmentDraft draft = pendentes.get(i);
            sb.append(i + 1).append(". ").append(draft.getResumo()).append("\n");
        }
        
        sb.append("\nTodos estão aguardando confirmação de disponibilidade.");
        return sb.toString();
    }

    private void detectarServico(String texto, AppointmentDraft draft) {
        if (contem(texto, "pe e mao", "pé e mão")) {
            draft.setServico("pé e mão");
            return;
        }

        if (contem(texto, "manicure")) {
            draft.setServico("manicure");
            return;
        }

        if (contem(texto, "pedicure")) {
            draft.setServico("pedicure");
            return;
        }

        if (contem(texto, "unha", "mao", "mão")) {
            draft.setServico("unha");
            return;
        }

        if (contemPalavra(texto, "pe")) {
            draft.setServico("pé");
            return;
        }

        if (contem(texto, "cabelo", "corte", "escova", "progressiva")) {
            draft.setServico("cabelo");
            return;
        }

        if (contem(texto, "sobrancelha")) {
            draft.setServico("sobrancelha");
            return;
        }

        if (contem(texto, "cilios", "cílios")) {
            draft.setServico("cílios");
            return;
        }

        if (contem(texto, "maquiagem")) {
            draft.setServico("maquiagem");
        }
    }

    private void detectarDia(String texto, AppointmentDraft draft) {
        if (contem(texto, "segunda")) {
            draft.setDia("segunda");
        } else if (contem(texto, "terca", "terça")) {
            draft.setDia("terça");
        } else if (contem(texto, "quarta")) {
            draft.setDia("quarta");
        } else if (contem(texto, "quinta")) {
            draft.setDia("quinta");
        } else if (contem(texto, "sexta")) {
            draft.setDia("sexta");
        } else if (contem(texto, "sabado", "sábado")) {
            draft.setDia("sábado");
        } else if (contem(texto, "domingo")) {
            draft.setDia("domingo");
        } else if (contem(texto, "hoje")) {
            draft.setDia("hoje");
        } else if (contem(texto, "amanha", "amanhã")) {
            draft.setDia("amanhã");
        }
    }

    private void detectarHorario(String mensagem, AppointmentDraft draft) {
        Matcher matcher = HORARIO_PATTERN.matcher(mensagem);

        if (matcher.find()) {
            String hora = primeiroValor(matcher.group(1), matcher.group(3));
            String minuto = primeiroValor(matcher.group(2), matcher.group(4), matcher.group(5));

            if (hora != null) {
                draft.setHorario(formatarHorario(hora, minuto));
            }
        }
    }

    private void detectarNome(String mensagem, AppointmentDraft draft) {
        String nome = extrairNome(mensagem);

        if (nome != null) {
            draft.setNome(nome);
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
        AppointmentDraft draftAtual = state.getDraftAtual();
        
        // Se não tem draft, cria um
        if (draftAtual == null) {
            draftAtual = state.criarNovoDraft();
        }
        
        // Se o draft atual está completo, vai para AGUARDANDO_CONFIRMACAO
        if (draftAtual.isCompleto()) {
            state.setStep(ConversationStep.AGUARDANDO_CONFIRMACAO);
        } else if (estaVazio(draftAtual.getServico())) {
            state.setStep(ConversationStep.AGUARDANDO_SERVICO);
        } else if (estaVazio(draftAtual.getDia())) {
            state.setStep(ConversationStep.AGUARDANDO_DIA);
        } else if (estaVazio(draftAtual.getHorario())) {
            state.setStep(ConversationStep.AGUARDANDO_HORARIO);
        } else if (estaVazio(draftAtual.getNome())) {
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
            case AGUARDANDO_CONFIRMACAO, FINALIZADO -> "Confirmar que a solicitação foi recebida e será validada.";
            default -> "";
        };
    }

    private String montarRespostaParaContinuar(ConversationState state) {
        return switch (state.getStep()) {
            case AGUARDANDO_SERVICO -> "Me fala qual serviço você quer agendar.";
            case AGUARDANDO_DIA -> "Me fala para qual dia você gostaria.";
            case AGUARDANDO_HORARIO -> "Me fala o horário que você prefere.";
            case AGUARDANDO_NOME -> "Me fala como você prefere que eu te chame.";
            case AGUARDANDO_CONFIRMACAO, FINALIZADO -> montarConfirmacao(state);
            default -> "Me fala como posso ajudar.";
        };
    }

    private String montarPerguntaHorario(ConversationState state) {
        AppointmentDraft draft = state.getDraftAtual();
        if (draft == null || estaVazio(draft.getDia())) {
            return "Certo. Qual horário você prefere?";
        }

        return "Certo. Qual horário você prefere na " + draft.getDia() + "?";
    }

    private String montarConfirmacao(ConversationState state) {
        AppointmentDraft draft = state.getDraftAtual();
        if (draft == null) {
            return "Perfeito. Recebi sua solicitação e vou verificar a disponibilidade.";
        }
        
        return "Perfeito, " + valorOuNaoInformado(draft.getNome())
                + ". Recebi sua solicitação para " + valorOuNaoInformado(draft.getServico())
                + " na " + valorOuNaoInformado(draft.getDia())
                + " às " + valorOuNaoInformado(draft.getHorario())
                + ". Vou verificar a disponibilidade e retorno com a confirmação.";
    }

    private String montarRespostaPosAgendamento(ConversationState state, String mensagem) {
        String texto = normalizar(mensagem);
        AppointmentDraft draft = state.getDraftAtual();

        // Se perguntou sobre o dia/horário do agendamento
        if (contem(texto, "qual dia", "que dia", "quando agendei", "qual data", "que data", 
                   "que horario", "qual horario", "que horas")) {
            if (draft != null) {
                return "Você agendou " + valorOuNaoInformado(draft.getServico())
                        + " na " + valorOuNaoInformado(draft.getDia())
                        + " às " + valorOuNaoInformado(draft.getHorario())
                        + ". Vou verificar a disponibilidade e retorno com a confirmação.";
            }
            return "Você ainda não tem um agendamento ativo. Qual serviço você gostaria de agendar?";
        }

        // Se perguntou sobre serviços disponíveis
        if (contem(texto, "servicos", "servico", "procedimentos", "o que voces fazem", "o que fazem")) {
            return "Por enquanto consigo registrar solicitações de serviços de beleza, como unha, sobrancelha, cabelo e cílios. Qual deles você gostaria de agendar?";
        }

        // Se perguntou sobre disponibilidade/agenda (sem ser sobre o próprio agendamento)
        if (contem(texto, "disponivel", "disponibilidade", "tem vaga", "tem horario", "quais dias")) {
            return "Ainda não consigo consultar a agenda automaticamente. Posso registrar o dia e horário que você prefere e confirmar com o salão.";
        }

        // Se quer novo agendamento
        if (contem(texto, "outro", "novo", "remarcar", "mudar")) {
            return "Claro! Qual serviço você gostaria de agendar agora?";
        }

        // Se agradeceu (verifica se é uma mensagem curta, apenas agradecimento)
        if (contem(texto, "obrigado", "obrigada", "valeu", "agradeco")) {
            // Verifica se a mensagem é curta (até 20 caracteres) ou se não tem outras perguntas
            if (mensagem.length() <= 20 || !temOutraPergunta(texto)) {
                return "Disponha!";
            }
        }

        // Se perguntou se agendou/confirmou
        if (contem(texto, "agendou", "confirmou", "foi agendado", "confirmado", "enviou", "marcou")) {
            if (draft != null) {
                return "Ainda não foi confirmado. Recebi sua solicitação para " + valorOuNaoInformado(draft.getServico())
                        + " na " + valorOuNaoInformado(draft.getDia())
                        + " às " + valorOuNaoInformado(draft.getHorario())
                        + " e vou verificar a disponibilidade.";
            }
            return "Você ainda não tem um agendamento ativo. Qual serviço você gostaria de agendar?";
        }

        // Se perguntou o próprio nome
        if (contem(texto, "meu nome", "qual meu nome", "meu nome e", "voce sabe meu nome")) {
            if (draft != null && draft.getNome() != null) {
                return "Seu nome está salvo como " + draft.getNome() + ".";
            }
            return "Você ainda não informou seu nome. Como prefere que eu te chame?";
        }

        // Default: resposta genérica pós-agendamento
        if (draft != null) {
            return "Perfeito, " + valorOuNaoInformado(draft.getNome())
                    + ". Recebi sua solicitação para " + valorOuNaoInformado(draft.getServico())
                    + " na " + valorOuNaoInformado(draft.getDia())
                    + " às " + valorOuNaoInformado(draft.getHorario())
                    + ". Vou verificar a disponibilidade e retorno com a confirmação.";
        }
        
        return "Perfeito. Recebi sua solicitação e vou verificar a disponibilidade.";
    }

    private boolean temOutraPergunta(String texto) {
        return contem(texto, "?", "como", "qual", "quando", "onde", "quem", "quanto", "por que");
    }

    private String proximaEtapa(ConversationState state) {
        return switch (state.getStep()) {
            case INICIO -> "iniciar atendimento";
            case AGUARDANDO_SERVICO -> "serviço desejado";
            case AGUARDANDO_DIA -> "dia desejado";
            case AGUARDANDO_HORARIO -> "horário desejado";
            case AGUARDANDO_NOME -> "nome da cliente";
            case AGUARDANDO_CONFIRMACAO, FINALIZADO -> "confirmar solicitação";
        };
    }

    private boolean mensagemNeutra(String texto) {
        return switch (texto) {
            case "sim", "isso", "pode ser", "ok", "ta", "ta bom", "tá", "tá bom", "certo", "beleza", "perfeito" -> true;
            default -> false;
        };
    }

    private boolean deveReiniciarConversa(String mensagem) {
        String texto = normalizar(mensagem);
        
        // Verifica se é uma saudacao
        boolean ehSaudacao = contem(texto, "oi", "ola", "olá", "bom dia", "boa tarde", "boa noite", "hey");
        
        // Verifica se é uma intencao de novo agendamento
        boolean ehNovoAgendamento = contem(texto, "quero agendar", "quero marcar", "gostaria de agendar", 
                                           "gostaria de marcar", "novo agendamento", "nova marcação",
                                           "outro horario", "outra marcação", "remarcar", "mudar");
        
        return ehSaudacao || ehNovoAgendamento;
    }

    private boolean isMensagemLembranca(String texto) {
        return contem(texto, "esqueceu", "já falei", "ja falei", "acabei de falar", "acabei de dizer", 
                      "eu já disse", "eu ja disse", "repete", "repita", "lembra", "lembre");
    }

    public boolean isPerguntaSobreAgendamentos(String texto) {
        return contem(texto, "o que agendei", "quais agendamentos", "meus agendamentos", 
                      "agendamentos pendentes", "lista de agendamentos");
    }

    private boolean deveCriarNovoAgendamento(String texto) {
        return contem(texto, "quero agendar", "quero marcar", "gostaria de agendar", 
                      "gostaria de marcar", "novo agendamento", "nova marcação",
                      "outro horario", "outra marcação", "remarcar", "mudar");
    }

    public boolean isApenasAgradecimentoCurto(String mensagem) {
        String texto = normalizar(mensagem);
        
        // Verifica se contém palavras de pergunta ou outras intenções
        if (contem(texto, "qual", "quais", "que", "quando", "onde", "como", "servico", "servicos", 
                   "dia", "horario", "nome", "agendou", "confirmou", "disponivel")) {
            return false;
        }
        
        // Verifica se é uma mensagem curta (até 20 caracteres)
        if (mensagem.length() > 20) {
            return false;
        }
        
        // Verifica se é apenas agradecimento
        return contem(texto, "obrigado", "obrigada", "valeu", "vlw", "agradeco");
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
