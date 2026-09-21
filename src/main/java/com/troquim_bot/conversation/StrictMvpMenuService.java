package com.troquim_bot.conversation;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.AberturaDeAgenda;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.conversation.state.ConversationStep;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StrictMvpMenuService {

    private static final int INTERACTIVE_PAGE_SIZE = 7;
    private static final String CHOICE_BACK = "[[choice:nav_voltar|← Voltar]]";

    /**
     * Falha tecnica: o texto canonico de {@link BookingResult}, o MESMO que o WhatsApp
     * Flow usa — os dois canais nao podem divergir sobre o que aconteceu.
     *
     * Deliberadamente neutro: nao afirma que o horario continua livre nem que nada foi
     * criado. Numa falha de persistencia nao ha evidencia de nenhuma das duas coisas. A
     * instrucao de repetir e' segura por causa da idempotencia por MENSAGEM
     * (InboundReceiptProcessor, UNIQUE por provider + external_message_id), nao por a
     * agenda estar inalterada.
     */
    private static final String MENSAGEM_FALHA_TECNICA =
            BookingResult.MENSAGEM_FALHA_TECNICA + "\n\nDigite 1 para tentar de novo.";

    /** Texto natural: o cliente toca no botao, sem "digite 1". */
    private static final String MENSAGEM_AGENDA_ABERTA =
            "Te mandei a agenda aqui em cima. E so tocar em \"Abrir agenda\" e escolher.\n\n"
                    + "Se preferir, pode continuar por aqui digitando o servico.";

    private static final String MENSAGEM_FALHA_CONSULTA_HORARIOS =
            "Nao consegui consultar os horarios agora. Tente novamente em instantes "
                    + "ou digite \"voltar\" para escolher outro dia.";

    private final ConversationStateService conversationStateService;
    private final AvailabilityApplicationService availabilityApplicationService;
    private final BookingApplicationService bookingApplicationService;
    private final ObjectProvider<AberturaDeAgenda> aberturaDeAgenda;
    private final ConversationNavigationPolicy navigationPolicy;
    private final TimeInputParser timeInputParser;
    private final boolean strictMvpEnabled;
    private final ConversationBookingGateway conversationBookingGateway;

    public StrictMvpMenuService(ConversationStateService conversationStateService,
                                AvailabilityApplicationService availabilityApplicationService,
                                BookingApplicationService bookingApplicationService,
                                ObjectProvider<AberturaDeAgenda> aberturaDeAgenda,
                                @Value("${conversation.mode:STRICT_MVP}") String conversationMode) {
        this(conversationStateService, availabilityApplicationService, bookingApplicationService,
                aberturaDeAgenda, conversationMode, null);
    }

    @Autowired
    public StrictMvpMenuService(ConversationStateService conversationStateService,
                                AvailabilityApplicationService availabilityApplicationService,
                                BookingApplicationService bookingApplicationService,
                                ObjectProvider<AberturaDeAgenda> aberturaDeAgenda,
                                @Value("${conversation.mode:STRICT_MVP}") String conversationMode,
                                ConversationBookingGateway conversationBookingGateway) {
        this.conversationStateService = conversationStateService;
        this.availabilityApplicationService = availabilityApplicationService;
        this.bookingApplicationService = bookingApplicationService;
        this.aberturaDeAgenda = aberturaDeAgenda;
        this.navigationPolicy = new ConversationNavigationPolicy();
        this.timeInputParser = new TimeInputParser();
        this.strictMvpEnabled = "STRICT_MVP".equalsIgnoreCase(conversationMode);
        this.conversationBookingGateway = conversationBookingGateway;
    }

    public boolean isStrictMvpEnabled() {
        return strictMvpEnabled;
    }

    public String processarMenu(String numero, String mensagem, ConversationState state) {
        if (!strictMvpEnabled) {
            return null;
        }

        var navigationAction = navigationPolicy.interpretar(mensagem, state);
        if (navigationAction.isPresent()) {
            var action = navigationAction.get();
            if (action instanceof ConversationNavigationPolicy.ResetToMenu) {
                conversationStateService.limparEstado(numero);
                ConversationState resetState = conversationStateService.buscarPorNumero(numero);
                resetState.setStep(ConversationStep.INICIO);
                conversationStateService.persistir(resetState);
                return menuPrincipal();
            }
            if (action instanceof ConversationNavigationPolicy.Back) {
                return voltarUmaEtapa(numero, state);
            }
        }

        String texto = normalizar(mensagem);
        ConversationStep step = state.getStep();

        Integer paginaHorarios = paginaDe(texto, "horarios_pagina_");
        if (paginaHorarios != null && step == ConversationStep.AGUARDANDO_HORARIO) {
            return menuHorarios(numero, paginaHorarios);
        }

        Integer paginaServicos = paginaDe(texto, "servicos_pagina_");
        if (paginaServicos != null && step == ConversationStep.AGUARDANDO_SERVICO) {
            return menuServicos(paginaServicos);
        }

        Integer paginaCancelamentos = paginaDe(texto, "cancelamentos_pagina_");
        if (paginaCancelamentos != null && step == ConversationStep.AGUARDANDO_CANCELAMENTO) {
            return menuCancelamentos(numero, paginaCancelamentos);
        }

        // Intencoes globais nao podem ficar presas na etapa atual do formulario textual.
        // "ver agendamento" durante AGUARDANDO_SERVICO continua sendo consulta, nao nome
        // de servico. O mesmo vale para iniciar um novo agendamento.
        if (ehComandoAgendar(texto)) {
            return iniciarNovoAgendamento(numero);
        }
        if (ehConsultaAgendamentos(texto)) {
            return consultarAgendamentos(numero);
        }
        if (ehCancelarFluxoAtual(texto, step)) {
            conversationStateService.limparEstado(numero);
            return "Agendamento atual descartado.\n\n" + menuAcoes();
        }
        Integer indiceCancelamento = indiceDeCancelamento(texto);
        if (indiceCancelamento != null && step != ConversationStep.AGUARDANDO_CONFIRMACAO) {
            return cancelarAgendamento(numero, indiceCancelamento);
        }
        if (ehComandoCancelar(texto) && step != ConversationStep.AGUARDANDO_CONFIRMACAO) {
            return cancelarAgendamento(numero);
        }

        if (step == ConversationStep.FINALIZADO || step == ConversationStep.INICIO) {
            if (texto.matches("^[123]$")) {
                return processarEscolhaMenuPrincipal(numero, texto);
            }
            return menuPrincipal();
        }

        if (step == ConversationStep.AGUARDANDO_SERVICO) {
            return processarEscolhaServico(numero, texto, mensagem);
        }
        if (step == ConversationStep.AGUARDANDO_DIA) {
            return processarEscolhaDia(numero, texto, mensagem);
        }
        if (step == ConversationStep.AGUARDANDO_HORARIO) {
            return processarEscolhaHorario(numero, texto, mensagem);
        }
        if (step == ConversationStep.AGUARDANDO_NOME) {
            return processarEscolhaNome(numero, mensagem);
        }
        if (step == ConversationStep.AGUARDANDO_CONFIRMACAO) {
            return processarConfirmacao(numero, texto);
        }
        if (step == ConversationStep.AGUARDANDO_CANCELAMENTO) {
            if (texto.matches("^\\d+$")) {
                try {
                    return cancelarAgendamento(numero, Integer.parseInt(texto) - 1);
                } catch (NumberFormatException invalido) {
                    return cancelarAgendamento(numero);
                }
            }
            return cancelarAgendamento(numero);
        }

        return null;
    }

    private String voltarUmaEtapa(String numero, ConversationState state) {
        if (state == null) {
            return menuPrincipal();
        }

        var draft = state.getDraftAtual();
        switch (state.getStep()) {
            case AGUARDANDO_SERVICO, INICIO, FINALIZADO, AGUARDANDO_CANCELAMENTO -> {
                conversationStateService.limparEstado(numero);
                return menuPrincipal();
            }
            case AGUARDANDO_DIA -> {
                if (draft != null) {
                    draft.setServico(null);
                    draft.setServicoSugerido(null);
                    draft.setEntradaServicoSugerida(null);
                }
                state.setStep(ConversationStep.AGUARDANDO_SERVICO);
                conversationStateService.persistir(state);
                return menuServicos();
            }
            case AGUARDANDO_HORARIO -> {
                if (draft != null) {
                    draft.setDia(null);
                }
                state.setStep(ConversationStep.AGUARDANDO_DIA);
                conversationStateService.persistir(state);
                return menuDias();
            }
            case AGUARDANDO_NOME, AGUARDANDO_CONFIRMACAO -> {
                if (draft != null) {
                    draft.setHorario(null);
                }
                state.setStep(ConversationStep.AGUARDANDO_HORARIO);
                conversationStateService.persistir(state);
                return menuHorarios(numero);
            }
        }
        return menuPrincipal();
    }

    private String processarEscolhaMenuPrincipal(String numero, String texto) {
        if (texto.contains("1") || texto.contains("agendar") || texto.contains("marcar") || texto.contains("novo")) {
            return iniciarNovoAgendamento(numero);
        }
        if (texto.contains("2") || texto.contains("meus") || texto.contains("consultar") || texto.contains("agendamentos") || texto.contains("ver")) {
            return consultarAgendamentos(numero);
        }
        if (texto.contains("3") || texto.contains("cancelar") || texto.contains("apagar") || texto.contains("remover") || texto.contains("desmarcar")) {
            return cancelarAgendamento(numero);
        }
        return menuPrincipal();
    }

    private String menuPrincipal() {
        return "Ola! No momento eu consigo te ajudar com agendamentos. Escolha uma opcao:\n\n" +
               "1) Agendar\n" +
               "2) Meus agendamentos\n" +
               "3) Cancelar";
    }

    private String iniciarNovoAgendamento(String numero) {
        conversationStateService.limparEstado(numero);
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        state.criarNovoDraft();
        state.setStep(ConversationStep.AGUARDANDO_SERVICO);
        conversationStateService.atualizarStep(state);
        conversationStateService.persistir(state);

        // ÚNICO ponto de integração com a agenda rica. A conversa nao conhece WhatsApp
        // Flow, Meta nem criptografia: pergunta a uma capacidade opcional se ela existe e
        // manda abrir. Ausente, desligada ou com falha de envio, o menu textual segue
        // exatamente como antes — o estado da conversa ja foi preparado acima, entao o
        // cliente que ignorar o botao continua atendido pelo texto.
        AberturaDeAgenda agenda = aberturaDeAgenda.getIfAvailable();
        if (agenda != null && agenda.disponivel() && agenda.abrirPara(numero).abriu()) {
            return MENSAGEM_AGENDA_ABERTA;
        }

        return menuServicos();
    }

    private String menuServicos() {
        return menuServicos(0);
    }

    private String menuServicos(int pagina) {
        List<String> nomes;
        if (conversationBookingGateway == null) {
            nomes = List.of("Unha", "Cabelo", "Sobrancelha", "Cílios", "Pé e mão");
        } else {
            List<ConversationBookingGateway.Servico> servicos =
                    conversationBookingGateway.listarServicos();
            if (servicos.isEmpty()) {
                return "Nenhum serviço disponível no momento.\n\n" + CHOICE_BACK;
            }
            nomes = servicos.stream().map(ConversationBookingGateway.Servico::nome).toList();
        }

        StringBuilder sb = new StringBuilder("Qual serviço você gostaria de agendar?\n\n");
        appendPaginatedOptions(
                sb, nomes, pagina, "servicos_pagina_",
                "← Serviços anteriores", "Mais serviços →");
        return sb.toString();
    }

    private String processarEscolhaServico(String numero, String texto, String mensagemOriginal) {
        if (conversationBookingGateway != null) {
            ConversationState state = conversationStateService.buscarPorNumero(numero);
            var draft = state.getDraftAtual();
            if (draft != null && draft.getServicoSugerido() != null) {
                if (ehConfirmacaoPositiva(texto)) {
                    String confirmado = draft.getServicoSugerido();
                    String entradaOriginal = draft.getEntradaServicoSugerida();
                    conversationBookingGateway.aprenderCorrecaoDeServico(entradaOriginal, confirmado);
                    draft.setServicoSugerido(null);
                    draft.setEntradaServicoSugerida(null);
                    conversationStateService.atualizarServico(numero, confirmado);
                    return menuDias();
                }
                if (ehConfirmacaoNegativa(texto)) {
                    draft.setServicoSugerido(null);
                    draft.setEntradaServicoSugerida(null);
                    conversationStateService.persistir(state);
                    return menuServicos();
                }
                // O cliente escreveu outra coisa: descarta a sugestao antiga e interpreta
                // a nova mensagem normalmente.
                draft.setServicoSugerido(null);
                draft.setEntradaServicoSugerida(null);
                conversationStateService.persistir(state);
            }

            List<ConversationBookingGateway.Servico> servicos = conversationBookingGateway.listarServicos();
            if (servicos.isEmpty()) {
                return "Nenhum servico disponivel no momento.";
            }

            String servico = null;
            if (texto.matches("^\\d+$")) {
                try {
                    int indice = Integer.parseInt(texto) - 1;
                    if (indice >= 0 && indice < servicos.size()) {
                        servico = servicos.get(indice).nome();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (servico == null) {
                servico = conversationBookingGateway.nomeCanonicoDoServico(mensagemOriginal).orElse(null);
            }
            if (servico == null) {
                Optional<String> sugestao = conversationBookingGateway.sugerirServico(mensagemOriginal);
                if (sugestao.isPresent()) {
                    ConversationState atual = conversationStateService.buscarPorNumero(numero);
                    if (atual.getDraftAtual() != null) {
                        atual.getDraftAtual().setServicoSugerido(sugestao.get());
                        atual.getDraftAtual().setEntradaServicoSugerida(mensagemOriginal);
                        conversationStateService.persistir(atual);
                    }
                    return "Voce quis dizer " + sugestao.get() + "?\n\n"
                            + "1) Sim\n"
                            + "2) Nao";
                }
                return "Esse servico nao esta disponivel.\n\n" + menuServicos();
            }

            conversationStateService.atualizarServico(numero, servico);
            return menuDias();
        }

        String servico = null;
        if (texto.matches("^[1-5]$")) {
            servico = switch (texto) {
                case "1" -> "unha";
                case "2" -> "cabelo";
                case "3" -> "sobrancelha";
                case "4" -> "cilios";
                case "5" -> "pe e mao";
                default -> null;
            };
        } else {
            if (texto.contains("unha") || texto.contains("manicure") || texto.contains("pedicure")) {
                servico = "unha";
            } else if (texto.contains("cabelo") || texto.contains("corte") || texto.contains("escova")) {
                servico = "cabelo";
            } else if (texto.contains("sobrancelha")) {
                servico = "sobrancelha";
            } else if (texto.contains("cilio")) {
                servico = "cilios";
            } else if (texto.contains("pe") && texto.contains("mao")) {
                servico = "pe e mao";
            }
        }
        if (servico == null) {
            return "Nao entendi. Por favor, escolha um servico:\n\n" +
                   "1) Unha\n" +
                   "2) Cabelo\n" +
                   "3) Sobrancelha\n" +
                   "4) Cilios\n" +
                   "5) Pe e mao\n\n" +
                   "Digite o numero ou o nome:";
        }
        conversationStateService.atualizarServico(numero, servico);
        return menuDias();
    }

    private String menuDias() {
        return "Perfeito! Para qual dia você gostaria?\n\n" + opcoesDias();
    }

    private String opcoesDias() {
        return "1) Segunda\n"
                + "2) Terça\n"
                + "3) Quarta\n"
                + "4) Quinta\n"
                + "5) Sexta\n"
                + "6) Sábado\n"
                + CHOICE_BACK;
    }

    private String processarEscolhaDia(String numero, String texto, String mensagemOriginal) {
        String dia = resolverDia(texto);
        if (dia == null) {
            return "Não entendi. Escolha um dia disponível:\n\n" + opcoesDias();
        }
        conversationStateService.atualizarDia(numero, dia);
        return menuHorarios(numero);
    }

    private String resolverDia(String texto) {
        if (texto == null) {
            return null;
        }
        if (texto.matches("^[1-6]$")) {
            return switch (texto) {
                case "1" -> "segunda";
                case "2" -> "terca";
                case "3" -> "quarta";
                case "4" -> "quinta";
                case "5" -> "sexta";
                case "6" -> "sabado";
                default -> null;
            };
        }
        if (texto.contains("segunda")) return "segunda";
        if (texto.contains("terca")) return "terca";
        if (texto.contains("quarta")) return "quarta";
        if (texto.contains("quinta")) return "quinta";
        if (texto.contains("sexta")) return "sexta";
        if (texto.contains("sabado")) return "sabado";
        return null;
    }

    private void voltarParaEscolhaDeDia(ConversationState state) {
        if (state == null) {
            return;
        }
        var draft = state.getDraftAtual();
        if (draft != null) {
            draft.setDia(null);
            draft.setHorario(null);
        }
        conversationStateService.atualizarStep(state);
        conversationStateService.persistir(state);
    }

    private String menuHorarios(String numero) {
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        String dia = state.getDraftAtual().getDia();

        if (conversationBookingGateway != null) {
            String servico = state.getDraftAtual().getServico();
            ConversationBookingGateway.ConsultaHorarios consulta =
                    conversationBookingGateway.consultarHorarios(servico, dia);

            if (consulta.status() == ConversationBookingGateway.Status.CATALOGO_NAO_CONFIGURADO) {
                return "Nenhum servico disponivel no momento.";
            }
            if (consulta.status() == ConversationBookingGateway.Status.SERVICO_INDISPONIVEL) {
                state.getDraftAtual().setServico(null);
                conversationStateService.atualizarStep(state);
                conversationStateService.persistir(state);
                return "Esse servico nao esta disponivel.\n\n" + menuServicos();
            }
            if (consulta.status() == ConversationBookingGateway.Status.PROFISSIONAL_AMBIGUO) {
                return "Esse servico tem mais de um profissional disponivel. Abra a agenda visual para escolher o profissional.";
            }
            if (consulta.status() == ConversationBookingGateway.Status.FALHA_TECNICA) {
                return MENSAGEM_FALHA_CONSULTA_HORARIOS;
            }
            if (consulta.status() == ConversationBookingGateway.Status.DIA_INVALIDO) {
                voltarParaEscolhaDeDia(state);
                return "Esse dia nao e valido para a agenda. Escolha outro dia:\n\n"
                        + menuDias();
            }
            if (!consulta.ok()) {
                return MENSAGEM_FALHA_CONSULTA_HORARIOS;
            }
            if (consulta.horarios().isEmpty()) {
                voltarParaEscolhaDeDia(state);
                return "Nao tenho horarios disponiveis para " + dia + ". Por favor, escolha outro dia:\n\n"
                        + "1) Segunda\n"
                        + "2) Terca\n"
                        + "3) Quarta\n"
                        + "4) Quinta\n"
                        + "5) Sexta\n"
                        + "6) Sabado";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Horarios disponiveis para ").append(dia).append(":\n\n");
            for (int i = 0; i < consulta.horarios().size(); i++) {
                sb.append(i + 1).append(") ")
                        .append(ConversationBookingGateway.formatarHorario(consulta.horarios().get(i)))
                        .append("\n");
            }
            sb.append("\nDigite o numero ou o horario (ex: 13h):");
            return sb.toString();
        }

        List<String> horarios = availabilityApplicationService.consultarDisponibilidade(dia);
        if (horarios.isEmpty()) {
            voltarParaEscolhaDeDia(state);
            return "Nao tenho horarios disponiveis para " + dia + ". Por favor, escolha outro dia:\n\n"
                    + "1) Segunda\n"
                    + "2) Terca\n"
                    + "3) Quarta\n"
                    + "4) Quinta\n"
                    + "5) Sexta\n"
                    + "6) Sabado";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Horarios disponiveis para ").append(dia).append(":\n\n");
        for (int i = 0; i < horarios.size(); i++) {
            sb.append(i + 1).append(") ").append(horarios.get(i)).append("\n");
        }
        sb.append("\nDigite o numero ou o horario (ex: 13h):");
        return sb.toString();
    }

    private String processarEscolhaHorario(String numero, String texto, String mensagemOriginal) {
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        String dia = state.getDraftAtual().getDia();

        if (conversationBookingGateway != null) {
            String servico = state.getDraftAtual().getServico();
            ConversationBookingGateway.ConsultaHorarios consulta =
                    conversationBookingGateway.consultarHorarios(servico, dia);
            if (!consulta.ok() || consulta.horarios().isEmpty()) {
                // Compatibilidade com estados produzidos antes da correcao: se a
                // conversa ficou em AGUARDANDO_HORARIO sem nenhum slot, uma entrada
                // que claramente representa dia deve trocar o dia imediatamente.
                if (resolverDia(texto) != null) {
                    return processarEscolhaDia(numero, texto, mensagemOriginal);
                }
                return menuHorarios(numero);
            }

            java.time.LocalTime escolhido = null;
            if (texto.matches("^\\d+$")) {
                try {
                    int indice = Integer.parseInt(texto) - 1;
                    if (indice >= 0 && indice < consulta.horarios().size()) {
                        escolhido = consulta.horarios().get(indice);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (escolhido == null) {
                escolhido = timeInputParser.parse(mensagemOriginal).orElse(null);
            }

            if (escolhido == null || !consulta.horarios().contains(escolhido)) {
                return "Esse horario nao esta disponivel. Escolha uma das opcoes acima.\n\n"
                        + menuHorarios(numero);
            }

            conversationStateService.atualizarHorario(
                    numero, ConversationBookingGateway.formatarHorario(escolhido));
            return menuNome(numero);
        }

        List<String> horarios = availabilityApplicationService.consultarDisponibilidade(dia);
        if (horarios.isEmpty()) {
            if (resolverDia(texto) != null) {
                return processarEscolhaDia(numero, texto, mensagemOriginal);
            }
            voltarParaEscolhaDeDia(state);
            return menuDias();
        }
        String horario = null;
        if (texto.matches("^\\d+$")) {
            try {
                int indice = Integer.parseInt(texto) - 1;
                if (indice >= 0 && indice < horarios.size()) {
                    horario = horarios.get(indice);
                }
            } catch (NumberFormatException e) {
                // Ignora
            }
        }
        if (horario == null) {
            var parsedTime = timeInputParser.parse(mensagemOriginal);
            if (parsedTime.isPresent()) {
                horario = formatarHorarioLocalTime(parsedTime.get());
            }
        }
        if (horario == null) {
            return "Nao entendi. Por favor, escolha um horario:\n\n" +
                   "Digite o numero ou o horario (ex: 13h):";
        }
        conversationStateService.atualizarHorario(numero, horario);
        return menuNome(numero);
    }

    private String menuNome(String numero) {
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        String nome = state.getNome();
        if (nome != null && !nome.isBlank()) {
            return menuConfirmacao(numero);
        }
        return "Perfeito! Qual e o seu nome?";
    }

    private String processarEscolhaNome(String numero, String mensagem) {
        String nome = mensagem.trim();
        if (nome.length() < 2 || nome.length() > 60) {
            return "Por favor, digite um nome valido:";
        }
        conversationStateService.atualizarNome(numero, nome);
        return menuConfirmacao(numero);
    }

    private String menuConfirmacao(String numero) {
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        String resumo = state.getDraftAtual().getResumo();
        return "Perfeito! Vou confirmar seu agendamento:\n\n" +
               resumo + "\n\n" +
               "1) Confirmar\n" +
               "2) Cancelar\n\n" +
               "Digite 1 para confirmar ou 2 para cancelar:";
    }

    private String processarConfirmacao(String numero, String texto) {
        if (ehConfirmacaoNegativa(texto)) {
            conversationStateService.limparEstado(numero);
            return "Agendamento cancelado.\n\n" +
                   "Deseja fazer algo mais?\n\n" +
                   "1) Agendar\n" +
                   "2) Meus agendamentos\n" +
                   "3) Cancelar";
        }
        if (ehConfirmacaoPositiva(texto)) {
            ConversationState state = conversationStateService.buscarPorNumero(numero);
            var draft = state.getDraftAtual();
            if (draft == null || !draft.isCompleto()) {
                return menuPrincipal();
            }
            if (draft.isConfirmado()) {
                return "Seu agendamento ja esta registrado.\n\n" +
                       "Deseja fazer algo mais?\n\n" +
                       "1) Agendar\n" +
                       "2) Meus agendamentos\n" +
                       "3) Cancelar";
            }

            if (conversationBookingGateway != null) {
                // Persiste a identidade da tentativa antes da escrita. Snapshots antigos que
                // nao tinham commandBase ganham uma aqui e retries passam a reutiliza-la.
                conversationStateService.persistir(state);

                ConversationBookingGateway.Confirmacao confirmacao =
                        conversationBookingGateway.confirmar(
                                draft.getCommandBase(),
                                numero,
                                state.getNome(),
                                draft.getServico(),
                                draft.getDia(),
                                draft.getHorario());

                if (confirmacao.status() == ConversationBookingGateway.Status.FALHA_TECNICA) {
                    return MENSAGEM_FALHA_TECNICA;
                }
                if (confirmacao.status() == ConversationBookingGateway.Status.HORARIO_INDISPONIVEL) {
                    draft.setHorario(null);
                    state.setStep(ConversationStep.AGUARDANDO_HORARIO);
                    conversationStateService.persistir(state);
                    return "Esse horario nao esta disponivel. Escolha uma das opcoes acima.\n\n"
                            + menuHorarios(numero);
                }
                if (!confirmacao.confirmada()) {
                    return "Nao consegui confirmar essa escolha. Abra a agenda novamente e selecione uma opcao disponivel.";
                }

                draft.setConfirmado(true);
                state.setStep(ConversationStep.FINALIZADO);
                conversationStateService.persistir(state);
                return "Agendamento confirmado com sucesso!\n\n" +
                       "Deseja fazer algo mais?\n\n" +
                       "1) Agendar\n" +
                       "2) Meus agendamentos\n" +
                       "3) Cancelar";
            }

            BookingResult resultado;
            try {
                resultado = bookingApplicationService.confirmar(
                        numero, state.getNome(), draft.getServico(), draft.getDia(), draft.getHorario());
            } catch (RuntimeException falhaTecnica) {
                return MENSAGEM_FALHA_TECNICA;
            }
            if (resultado.isFalhaTecnica()) {
                return MENSAGEM_FALHA_TECNICA;
            }
            if (!resultado.isConfirmado()) {
                return resultado.mensagem() + "\n\n" +
                       "Digite 2 para cancelar e escolher outro horario.";
            }
            draft.setConfirmado(true);
            state.setStep(ConversationStep.FINALIZADO);
            conversationStateService.persistir(state);
            return "Agendamento confirmado com sucesso!\n\n" +
                   "Deseja fazer algo mais?\n\n" +
                   "1) Agendar\n" +
                   "2) Meus agendamentos\n" +
                   "3) Cancelar";
        }
        return "Por favor, digite 1 para confirmar ou 2 para cancelar:";
    }

    private String consultarAgendamentos(String numero) {
        if (conversationBookingGateway != null) {
            List<ConversationBookingGateway.Agendamento> ativos =
                    conversationBookingGateway.listarAgendamentosAtivos(numero);
            if (ativos.isEmpty()) {
                return "Voce ainda nao tem agendamentos ativos.\n\n" + menuAcoes();
            }

            StringBuilder sb = new StringBuilder("Seus agendamentos ativos:\n\n");
            for (int i = 0; i < ativos.size(); i++) {
                var a = ativos.get(i);
                sb.append(i + 1).append(") ")
                        .append(a.servico()).append(" em ")
                        .append(formatarData(a.data())).append(" as ")
                        .append(ConversationBookingGateway.formatarHorario(a.horario()))
                        .append(a.confirmado() ? " - CONFIRMADO" : " - PENDENTE")
                        .append("\n");
            }
            sb.append("\n").append(menuAcoes());
            return sb.toString();
        }

        // Compatibilidade dos testes/instancias antigas construidas manualmente.
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        var draft = state.getDraftAtual();
        if (draft != null && draft.isCompleto()) {
            return "Voce tem um agendamento registrado:\n\n" +
                   draft.getResumo() + "\n\n" + menuAcoes();
        }
        return "Voce ainda nao tem agendamentos ativos.\n\n" + menuAcoes();
    }

    private String cancelarAgendamento(String numero) {
        if (conversationBookingGateway != null) {
            List<ConversationBookingGateway.Agendamento> ativos =
                    conversationBookingGateway.listarAgendamentosAtivos(numero);
            if (ativos.isEmpty()) {
                return "Voce nao tem agendamentos ativos para cancelar.\n\n" + menuAcoes();
            }
            if (ativos.size() == 1) {
                return cancelarAgendamento(numero, 0);
            }

            StringBuilder sb = new StringBuilder(
                    "Voce tem mais de um agendamento. Qual deseja cancelar?\n\n");
            for (int i = 0; i < ativos.size(); i++) {
                var a = ativos.get(i);
                sb.append(i + 1).append(") ")
                        .append(a.servico()).append(" em ")
                        .append(formatarData(a.data())).append(" as ")
                        .append(ConversationBookingGateway.formatarHorario(a.horario()))
                        .append("\n");
            }
            ConversationState state = conversationStateService.buscarPorNumero(numero);
            state.setStep(ConversationStep.AGUARDANDO_CANCELAMENTO);
            conversationStateService.persistir(state);
            sb.append("\nDigite apenas o numero ou, por exemplo: cancelar 1");
            return sb.toString();
        }

        ConversationState state = conversationStateService.buscarPorNumero(numero);
        var draft = state.getDraftAtual();
        if (draft != null && draft.isCompleto()) {
            conversationStateService.limparEstado(numero);
            return "Seu agendamento foi cancelado com sucesso.\n\n" + menuAcoes();
        }
        return "Voce nao tem agendamentos ativos para cancelar.\n\n" + menuAcoes();
    }

    private String cancelarAgendamento(String numero, int indice) {
        if (conversationBookingGateway == null) {
            return cancelarAgendamento(numero);
        }

        Optional<ConversationBookingGateway.Agendamento> cancelado =
                conversationBookingGateway.cancelarAgendamentoAtivo(numero, indice);
        if (cancelado.isEmpty()) {
            return "Nao encontrei esse agendamento.\n\n" + cancelarAgendamento(numero);
        }

        conversationStateService.limparEstado(numero);
        var a = cancelado.get();
        return "Agendamento cancelado com sucesso: " + a.servico() + " em " + formatarData(a.data())
                + " as " + ConversationBookingGateway.formatarHorario(a.horario())
                + ".\n\n" + menuAcoes();
    }

    private String menuAcoes() {
        return "Deseja fazer algo mais?\n\n"
                + "1) Agendar\n"
                + "2) Meus agendamentos\n"
                + "3) Cancelar";
    }

    private boolean ehCancelarFluxoAtual(String texto, ConversationStep step) {
        boolean emFormulario = step == ConversationStep.AGUARDANDO_SERVICO
                || step == ConversationStep.AGUARDANDO_DIA
                || step == ConversationStep.AGUARDANDO_HORARIO
                || step == ConversationStep.AGUARDANDO_NOME;

        if (!emFormulario) {
            return false;
        }

        // "cancelar agendamento"/"desmarcar" continuam sendo comandos explicitos para
        // cancelar um Appointment persistido. As formas abaixo abandonam apenas o draft.
        return texto.equals("cancelar")
                || texto.equals("cancela")
                || texto.equals("sair")
                || texto.equals("parar")
                || texto.equals("deixa")
                || texto.equals("deixa pra la")
                || texto.equals("deixa para la");
    }

    private boolean ehConfirmacaoPositiva(String texto) {
        return switch (texto) {
            case "1", "sim", "s", "isso", "isso mesmo", "correto", "certo",
                    "confirmar", "confirma", "pode ser", "ok", "beleza" -> true;
            default -> false;
        };
    }

    private boolean ehConfirmacaoNegativa(String texto) {
        return switch (texto) {
            case "2", "nao", "n", "negativo", "outro", "cancelar", "cancela",
                    "deixa", "deixa pra la", "deixa para la" -> true;
            default -> false;
        };
    }

    private boolean ehComandoAgendar(String texto) {
        return texto.equals("agendar")
                || texto.equals("marcar")
                || texto.equals("novo agendamento")
                || texto.equals("quero agendar")
                || texto.equals("quero marcar");
    }

    private boolean ehConsultaAgendamentos(String texto) {
        return texto.equals("meus agendamentos")
                || texto.equals("ver agendamento")
                || texto.equals("ver agendamentos")
                || texto.equals("qual meu agendamento")
                || texto.equals("agendou")
                || texto.equals("agendou?")
                || texto.equals("foi agendado")
                || texto.equals("esta agendado");
    }

    private boolean ehComandoCancelar(String texto) {
        return texto.equals("cancelar")
                || texto.equals("desmarcar")
                || texto.equals("cancelar agendamento")
                || texto.equals("desmarcar agendamento");
    }

    private Integer indiceDeCancelamento(String texto) {
        if (texto == null || !texto.matches("^(cancelar|desmarcar)\\s+\\d+$")) {
            return null;
        }
        String[] partes = texto.split("\\s+");
        try {
            int indiceHumano = Integer.parseInt(partes[partes.length - 1]);
            return indiceHumano - 1;
        } catch (NumberFormatException invalido) {
            return null;
        }
    }

    private String formatarData(java.time.LocalDate data) {
        if (data == null) {
            return "";
        }
        return String.format("%02d/%02d/%04d", data.getDayOfMonth(), data.getMonthValue(), data.getYear());
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcentos = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(java.util.Locale.ROOT);
    }

    private String formatarHorarioLocalTime(java.time.LocalTime time) {
        if (time.getMinute() == 0) {
            return time.getHour() + "h";
        }
        return time.getHour() + ":" + String.format("%02d", time.getMinute());
    }
}