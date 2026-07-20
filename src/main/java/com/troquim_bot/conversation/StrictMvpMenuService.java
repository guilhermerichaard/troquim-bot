package com.troquim_bot.conversation;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.conversation.state.ConversationStep;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StrictMvpMenuService {

    private final ConversationStateService conversationStateService;
    private final AvailabilityApplicationService availabilityApplicationService;
    private final BookingApplicationService bookingApplicationService;
    private final ConversationNavigationPolicy navigationPolicy;
    private final TimeInputParser timeInputParser;
    private final boolean strictMvpEnabled;

    public StrictMvpMenuService(ConversationStateService conversationStateService,
                                AvailabilityApplicationService availabilityApplicationService,
                                BookingApplicationService bookingApplicationService,
                                @Value("${conversation.mode:STRICT_MVP}") String conversationMode) {
        this.conversationStateService = conversationStateService;
        this.availabilityApplicationService = availabilityApplicationService;
        this.bookingApplicationService = bookingApplicationService;
        this.navigationPolicy = new ConversationNavigationPolicy();
        this.timeInputParser = new TimeInputParser();
        this.strictMvpEnabled = "STRICT_MVP".equalsIgnoreCase(conversationMode);
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
        }

        String texto = normalizar(mensagem);
        ConversationStep step = state.getStep();

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

        return null;
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
        return menuServicos();
    }

    private String menuServicos() {
        return "Qual servico voce gostaria de agendar?\n\n" +
               "1) Unha\n" +
               "2) Cabelo\n" +
               "3) Sobrancelha\n" +
               "4) Cilios\n" +
               "5) Pe e mao\n\n" +
               "Digite o numero ou o nome do servico:";
    }

    private String processarEscolhaServico(String numero, String texto, String mensagemOriginal) {
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
        return "Perfeito! Para qual dia voce gostaria?\n\n" +
               "1) Segunda\n" +
               "2) Terca\n" +
               "3) Quarta\n" +
               "4) Quinta\n" +
               "5) Sexta\n" +
               "6) Sabado\n\n" +
               "Digite o numero ou o nome do dia:";
    }

    private String processarEscolhaDia(String numero, String texto, String mensagemOriginal) {
        String dia = null;
        if (texto.matches("^[1-6]$")) {
            dia = switch (texto) {
                case "1" -> "segunda";
                case "2" -> "terca";
                case "3" -> "quarta";
                case "4" -> "quinta";
                case "5" -> "sexta";
                case "6" -> "sabado";
                default -> null;
            };
        } else {
            if (texto.contains("segunda")) dia = "segunda";
            else if (texto.contains("terca")) dia = "terca";
            else if (texto.contains("quarta")) dia = "quarta";
            else if (texto.contains("quinta")) dia = "quinta";
            else if (texto.contains("sexta")) dia = "sexta";
            else if (texto.contains("sabado")) dia = "sabado";
        }
        if (dia == null) {
            return "Nao entendi. Por favor, escolha um dia:\n\n" +
                   "1) Segunda\n" +
                   "2) Terca\n" +
                   "3) Quarta\n" +
                   "4) Quinta\n" +
                   "5) Sexta\n" +
                   "6) Sabado\n\n" +
                   "Digite o numero ou o nome:";
        }
        conversationStateService.atualizarDia(numero, dia);
        return menuHorarios(numero);
    }

    private String menuHorarios(String numero) {
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        String dia = state.getDraftAtual().getDia();
        List<String> horarios = availabilityApplicationService.consultarDisponibilidade(dia);
        if (horarios.isEmpty()) {
            return "Nao tenho horarios disponiveis para " + dia + ". Por favor, escolha outro dia:\n\n" +
                   "1) Segunda\n" +
                   "2) Terca\n" +
                   "3) Quarta\n" +
                   "4) Quinta\n" +
                   "5) Sexta\n" +
                   "6) Sabado";
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
        List<String> horarios = availabilityApplicationService.consultarDisponibilidade(dia);
        if (horarios.isEmpty()) {
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
        if (texto.contains("2") || texto.contains("cancelar") || texto.contains("nao")) {
            conversationStateService.limparEstado(numero);
            return "Agendamento cancelado.\n\n" +
                   "Deseja fazer algo mais?\n\n" +
                   "1) Agendar\n" +
                   "2) Meus agendamentos\n" +
                   "3) Cancelar";
        }
        if (texto.contains("1") || texto.contains("confirmar") || texto.contains("sim")) {
            ConversationState state = conversationStateService.buscarPorNumero(numero);
            var draft = state.getDraftAtual();
            if (draft == null || !draft.isCompleto()) {
                return menuPrincipal();
            }
            if (draft.isConfirmado()) {
                return "Seu agendamento ja esta registrado. Em breve o salao confirmara a disponibilidade.\n\n" +
                       "Deseja fazer algo mais?\n\n" +
                       "1) Agendar\n" +
                       "2) Meus agendamentos\n" +
                       "3) Cancelar";
            }
            BookingResult resultado = bookingApplicationService.confirmar(
                    numero, state.getNome(), draft.getServico(), draft.getDia(), draft.getHorario());
            if (!resultado.isConfirmado()) {
                return resultado.mensagem() + "\n\n" +
                       "Digite 2 para cancelar e escolher outro horario.";
            }
            draft.setConfirmado(true);
            state.setStep(ConversationStep.FINALIZADO);
            conversationStateService.persistir(state);
            return "Seu agendamento foi registrado com sucesso! Em breve o salao confirmara a disponibilidade.\n\n" +
                   "Deseja fazer algo mais?\n\n" +
                   "1) Agendar\n" +
                   "2) Meus agendamentos\n" +
                   "3) Cancelar";
        }
        return "Por favor, digite 1 para confirmar ou 2 para cancelar:";
    }

    private String consultarAgendamentos(String numero) {
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        var draft = state.getDraftAtual();
        if (draft != null && draft.isCompleto()) {
            return "Voce tem um agendamento pendente:\n\n" +
                   draft.getResumo() + "\n\n" +
                   "Aguardando confirmacao do salao.\n\n" +
                   "Deseja fazer algo mais?\n\n" +
                   "1) Agendar\n" +
                   "2) Meus agendamentos\n" +
                   "3) Cancelar";
        }
        return "Voce ainda nao tem agendamentos ativos.\n\n" +
               "Deseja fazer algo mais?\n\n" +
               "1) Agendar\n" +
               "2) Meus agendamentos\n" +
               "3) Cancelar";
    }

    private String cancelarAgendamento(String numero) {
        ConversationState state = conversationStateService.buscarPorNumero(numero);
        var draft = state.getDraftAtual();
        if (draft != null && draft.isCompleto()) {
            conversationStateService.limparEstado(numero);
            return "Seu agendamento foi cancelado com sucesso.\n\n" +
                   "Deseja fazer algo mais?\n\n" +
                   "1) Agendar\n" +
                   "2) Meus agendamentos\n" +
                   "3) Cancelar";
        }
        return "Voce nao tem agendamentos ativos para cancelar.\n\n" +
               "Deseja fazer algo mais?\n\n" +
               "1) Agendar\n" +
               "2) Meus agendamentos\n" +
               "3) Cancelar";
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