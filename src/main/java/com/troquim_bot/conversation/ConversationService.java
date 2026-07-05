package com.troquim_bot.conversation;

import com.troquim_bot.ai.intent.IntentService;
import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.ai.llm.OllamaService;
import com.troquim_bot.ai.memory.ConversationMemory;
import com.troquim_bot.ai.memory.ConversationMessage;
import com.troquim_bot.ai.prompt.PromptService;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStateService;
import com.troquim_bot.conversation.state.ConversationStep;
import com.troquim_bot.conversation.state.AppointmentDraft;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerProfile;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.schedule.AppointmentBookingService;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ConversationService {

    private final IntentService intentService;
    private final QuickResponseService quickResponseService;
    private final ContextService contextService;
    private final ConversationStateService conversationStateService;
    private final ConversationMemory conversationMemory;
    private final OllamaService ollamaService;
    private final PromptService promptService;
    private final CustomerProfileService customerProfileService;
    private final AppointmentApplicationService appointmentApplicationService;
    private final AppointmentBookingService appointmentBookingService;
    private final com.troquim_bot.application.availability.AvailabilityApplicationService availabilityApplicationService;

    public ConversationService(IntentService intentService,
                               QuickResponseService quickResponseService,
                               ContextService contextService,
                               ConversationStateService conversationStateService,
                               ConversationMemory conversationMemory,
                               OllamaService ollamaService,
                               PromptService promptService,
                               CustomerProfileService customerProfileService,
                               AppointmentApplicationService appointmentApplicationService,
                               AppointmentBookingService appointmentBookingService,
                               com.troquim_bot.application.availability.AvailabilityApplicationService availabilityApplicationService) {
        this.intentService = intentService;
        this.quickResponseService = quickResponseService;
        this.contextService = contextService;
        this.conversationStateService = conversationStateService;
        this.conversationMemory = conversationMemory;
        this.ollamaService = ollamaService;
        this.promptService = promptService;
        this.customerProfileService = customerProfileService;
        this.appointmentApplicationService = appointmentApplicationService;
        this.appointmentBookingService = appointmentBookingService;
        this.availabilityApplicationService = availabilityApplicationService;
    }

    public String gerarResposta(String numero, String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return "Não consegui entender sua mensagem. Pode me enviar novamente?";
        }

        IntentType intentType = intentService.classificar(mensagem);
        CustomerProfile customerProfile = carregarPerfil(numero, intentType);
        String nomePreferido = customerProfileService.nomePreferido(customerProfile).orElse(null);
        ConversationState conversationState = conversationStateService.buscarPorNumero(numero, nomePreferido);
        Optional<String> nomeInformado = conversationStateService.extrairNomeInformado(mensagem);
        ConversationRoute route = ConversationRoute.rotear(
                conversationStateService,
                conversationState,
                mensagem,
                intentType
        );
        boolean tinhaDraftCompleto = draftAtualCompleto(conversationState);

        if (route.continuaFluxo()) {
            conversationState = conversationStateService.processarMensagem(numero, mensagem, nomePreferido);
        }

        customerProfile = sincronizarNome(numero, nomeInformado, conversationState, customerProfile);
        Optional<String> respostaAgendamento = registrarAgendamentoConcluido(
                numero,
                conversationState,
                tinhaDraftCompleto
        );
        if (respostaAgendamento.isPresent()) {
            return responderComMemoria(numero, mensagem, respostaAgendamento.get());
        }

        Optional<String> respostaIntencao = executarIntencao(
                route,
                numero,
                mensagem,
                conversationState,
                customerProfile,
                nomeInformado
        );
        if (respostaIntencao.isPresent()) {
            return responderComMemoria(numero, mensagem, respostaIntencao.get());
        }

        Optional<String> respostaFluxo = executarFluxo(route, conversationState, mensagem);
        if (respostaFluxo.isPresent()) {
            return responderComMemoria(numero, mensagem, respostaFluxo.get());
        }

        return gerarRespostaComOllama(numero, mensagem, intentType, conversationState, customerProfile);
    }

    private CustomerProfile carregarPerfil(String numero, IntentType intentType) {
        if (deveIniciarNovoAtendimento(numero, intentType)) {
            return customerProfileService.iniciarAtendimento(numero);
        }

        return customerProfileService.buscarOuCriar(numero);
    }

    private Optional<String> executarIntencao(ConversationRoute route,
                                              String numero,
                                              String mensagem,
                                              ConversationState conversationState,
                                              CustomerProfile customerProfile,
                                              Optional<String> nomeInformado) {
        IntentType intentType = route.intentType();
        
        // Verifica consulta de agendamento ANTES de retornar respostas rápidas
        Optional<String> respostaAgendamento = responderConsultaAgendamento(numero, intentType, mensagem, conversationState);
        if (respostaAgendamento.isPresent()) {
            return respostaAgendamento;
        }

        // Verifica consulta de disponibilidade ANTES de retornar respostas rápidas de horário de funcionamento
        Optional<String> respostaDisponibilidade = responderConsultaDisponibilidade(mensagem, conversationState);
        if (respostaDisponibilidade.isPresent()) {
            return respostaDisponibilidade;
        }

        Optional<String> respostaRapida = quickResponseService.buscarResposta(intentType);

        if (respostaRapida.isPresent() && deveUsarRespostaRapida(intentType, mensagem)) {
            if (intentType == IntentType.SAUDACAO) {
                return Optional.of(montarSaudacao(customerProfile));
            }

            return respostaRapida;
        }

        // Verifica se é uma confirmação curta (como "ata", "certo", "beleza", etc.)
        if (intentType == IntentType.DESCONHECIDO && isConfirmacaoCurta(mensagem)) {
            return Optional.of("Certo.");
        }

        if (intentType == IntentType.LEMBRAR_CLIENTE) {
            return Optional.of(montarRespostaLembranca(customerProfile));
        }

        if (intentType == IntentType.CONSULTAR_NOME) {
            if (nomeInformado.isPresent()) {
                return Optional.of("Perfeito, " + nomeInformado.get() + ". Vou lembrar.");
            }

            return Optional.of(montarRespostaNome(customerProfile));
        }

        Optional<String> respostaPorIntencao = conversationStateService.montarRespostaPorIntencao(
                conversationState,
                mensagem,
                intentType
        );
        if (respostaPorIntencao.isPresent()) {
            return respostaPorIntencao;
        }

        if (conversationStateService.isPerguntaSobreAgendamentos(mensagem)) {
            return Optional.of(conversationStateService.listarAgendamentosPendentes(conversationState));
        }

        return Optional.empty();
    }

    private Optional<String> responderConsultaAgendamento(String numero,
                                                          IntentType intentType,
                                                          String mensagem,
                                                          ConversationState conversationState) {
        if (!isConsultaAgendamento(intentType, mensagem)) {
            return Optional.empty();
        }

        // Busca appointments do customer usando o novo serviço
        CustomerId customerId = CustomerId.fromPhone(numero);
        
        Optional<Appointment> appointment = appointmentApplicationService.buscarAtivoPorCliente(customerId);
        
        return Optional.of(appointment
                .map(a -> montarResumoAgendamento(a, conversationState))
                .orElse("Você ainda não tem uma solicitação de agendamento registrada."));
    }

    private Optional<String> responderConsultaDisponibilidade(String mensagem, ConversationState conversationState) {
        String texto = normalizar(mensagem);
        
        // Detecta se é uma consulta de disponibilidade (tem dia mas não é pergunta sobre horário de funcionamento)
        boolean temDia = contem(texto, "segunda", "terca", "quarta", "quinta", "sexta", "sabado", "domingo", "hoje", "amanha");
        boolean perguntaHorario = contem(texto, "horario de funcionamento", "horario comercial", "que horas abre", 
                                         "que horas fecha", "horario de atendimento", "funciona que horas");
        boolean perguntaDisponibilidade = contem(texto, "disponivel", "disponibilidade", "tem vaga", "tem horario", 
                                                  "quais dias", "horarios disponivel", "horario disponivel");
        
        if (!temDia || perguntaHorario) {
            return Optional.empty();
        }

        // Tenta extrair serviço da mensagem atual primeiro, depois do draft
        String servico = extrairServico(texto);
        AppointmentDraft draft = conversationState.getDraftAtual();
        if (estaVazio(servico) && draft != null) {
            servico = draft.getServico();
        }
        
        if (estaVazio(servico)) {
            return Optional.of("Qual serviço você gostaria de agendar?");
        }

        // Se tem serviço e dia, lista horários disponíveis reais
        String dia = extrairDia(texto);
        if (dia != null) {
            List<String> horariosDisponiveis = availabilityApplicationService.consultarDisponibilidade(dia);
            if (horariosDisponiveis.isEmpty()) {
                return Optional.of("Não tenho horários disponíveis para " + servico + " na " + dia + ". Qual outro dia você prefere?");
            }
            String horarios = formatarListaHorarios(horariosDisponiveis.stream()
                .map(this::formatarHorario)
                .toList());
            return Optional.of("Tenho horários para " + servico + " na " + dia + ": " + horarios + ".");
        }

        return Optional.empty();
    }

    private String extrairDia(String texto) {
        if (contem(texto, "segunda")) return "segunda";
        if (contem(texto, "terca", "terça")) return "terça";
        if (contem(texto, "quarta")) return "quarta";
        if (contem(texto, "quinta")) return "quinta";
        if (contem(texto, "sexta")) return "sexta";
        if (contem(texto, "sabado", "sábado")) return "sábado";
        if (contem(texto, "domingo")) return "domingo";
        if (contem(texto, "hoje")) return "hoje";
        if (contem(texto, "amanha", "amanhã")) return "amanhã";
        return null;
    }

    private Optional<String> executarFluxo(ConversationRoute route,
                                           ConversationState conversationState,
                                           String mensagem) {
        if (!route.continuaFluxo()) {
            return Optional.empty();
        }

        return conversationStateService.montarRespostaAutomatica(conversationState, mensagem);
    }

    private boolean deveIniciarNovoAtendimento(String numero, IntentType intentType) {
        if (!conversationStateService.possuiEstado(numero)) {
            return true;
        }

        ConversationState conversationState = conversationStateService.buscarPorNumero(numero);
        return intentType == IntentType.SAUDACAO
                && !conversationStateService.conversaEmAndamento(conversationState);
    }

    private CustomerProfile sincronizarNome(String numero,
                                            Optional<String> nomeInformado,
                                            ConversationState conversationState,
                                            CustomerProfile customerProfile) {
        if (nomeInformado.isPresent()) {
            conversationStateService.atualizarNome(numero, nomeInformado.get());
            return customerProfileService.salvarNome(numero, nomeInformado.get());
        }

        String nomeAtendimento = conversationState.getNome();
        if (nomeAtendimento != null && !nomeAtendimento.isBlank()) {
            String nomePerfil = customerProfile.getNome();
            if (nomePerfil == null || !nomePerfil.equals(nomeAtendimento)) {
                return customerProfileService.salvarNome(numero, nomeAtendimento);
            }
        }

        return customerProfile;
    }

    private Optional<String> registrarAgendamentoConcluido(String numero,
                                                           ConversationState conversationState,
                                                           boolean tinhaDraftCompleto) {
        AppointmentDraft draft = conversationState.getDraftAtual();
        if (tinhaDraftCompleto || draft == null || !draft.isCompleto()) {
            return Optional.empty();
        }

        String resultado = appointmentBookingService.bookIfAvailable(
                numero,
                draft.getNome(),
                draft.getServico(),
                draft.getDia(),
                draft.getHorario()
        );

        if (isHorarioDisponivel(resultado)) {
            return Optional.of("Perfeito, " + draft.getNome()
                    + ". Seu horário para " + draft.getServico()
                    + " na " + draft.getDia()
                    + " às " + draft.getHorario()
                    + " foi reservado.");
        }

        if (isHorarioIndisponivel(resultado)) {
            draft.setHorario(null);
            conversationState.setStep(ConversationStep.AGUARDANDO_HORARIO);
            return Optional.of("Esse horário não está disponível. Qual outro horário você prefere?");
        }

        return Optional.of(resultado);
    }

    private boolean isHorarioDisponivel(String resultado) {
        return resultado != null && resultado.contains("Perfeito! Vou reservar");
    }

    private boolean isHorarioIndisponivel(String resultado) {
        return resultado != null && normalizar(resultado).contains("nao esta mais disponivel");
    }

    private boolean draftAtualCompleto(ConversationState conversationState) {
        AppointmentDraft draft = conversationState.getDraftAtual();
        return draft != null && draft.isCompleto();
    }

    private String montarSaudacao(CustomerProfile customerProfile) {
        return customerProfileService.nomePreferido(customerProfile)
                .map(nome -> "Boa tarde, " + nome + "! Como posso ajudar?")
                .orElse("Boa tarde! Como posso ajudar?");
    }

    private String montarRespostaNome(CustomerProfile customerProfile) {
        return customerProfileService.nomePreferido(customerProfile)
                .map(nome -> "Seu nome está salvo como " + nome + ".")
                .orElse("Você ainda não informou seu nome. Como prefere que eu te chame?");
    }

    private String montarRespostaLembranca(CustomerProfile customerProfile) {
        return customerProfileService.nomePreferido(customerProfile)
                .map(nome -> "Lembro sim, " + nome + ". Como posso ajudar?")
                .orElse("Ainda não tenho seu nome salvo. Como prefere que eu te chame?");
    }

    private String montarResumoAgendamento(Appointment appointment, ConversationState conversationState) {
        AppointmentDraft draft = conversationState.getDraftAtual();
        String servico = draft != null && draft.getServico() != null ? draft.getServico() : "serviço";
        String dia = draft != null && draft.getDia() != null ? draft.getDia() : appointment.getDate().toString();
        String horario = draft != null && draft.getHorario() != null ? draft.getHorario() : appointment.getStartTime().toString();
        
        return "Você tem um agendamento para " + servico
                + " na " + dia
                + " às " + horario + ".";
    }

    private boolean isConsultaAgendamento(IntentType intentType, String mensagem) {
        if (intentType == IntentType.CONSULTAR_AGENDAMENTO
                || intentType == IntentType.CONSULTAR_DIA_AGENDADO
                || intentType == IntentType.CONSULTAR_HORARIO_AGENDADO
                || intentType == IntentType.CONSULTAR_SERVICO_AGENDADO) {
            return true;
        }

        String texto = normalizar(mensagem);
        return contem(texto, "agendei", "marquei", "qual meu agendamento", "meu agendamento", "qual agendamento",
                "qual horario", "que horario", "qual servico", "que servico",
                "marquei para quando", "agendei para quando");
    }

    private boolean deveUsarRespostaRapida(IntentType intentType, String mensagem) {
        if (intentType == IntentType.SAUDACAO) {
            return true;
        }

        if (intentType == IntentType.AGRADECIMENTO) {
            return conversationStateService.isApenasAgradecimentoCurto(mensagem);
        }

        return true;
    }

    private boolean isConfirmacaoCurta(String mensagem) {
        String texto = normalizar(mensagem);
        if (texto.length() > 20) {
            return false;
        }
        return switch (texto) {
            case "ata", "certo", "beleza", "perfeito", "ok", "ta", "tá" -> true;
            default -> false;
        };
    }

    private String gerarRespostaComOllama(String numero,
                                          String mensagem,
                                          IntentType intentType,
                                          ConversationState conversationState,
                                          CustomerProfile customerProfile) {
        String resumoEstado = conversationStateService.montarResumo(conversationState);
        String contexto = contextService.montarContexto(numero, mensagem, intentType, resumoEstado, customerProfile);
        List<ConversationMessage> historico = conversationMemory.getConversation(numero);

        conversationMemory.addUserMessage(numero, mensagem);

        String prompt = promptService.montarPrompt(mensagem, contexto, historico);
        String resposta = ollamaService.responder(prompt);

        conversationMemory.addAssistantMessage(numero, resposta);

        return resposta;
    }

    private String responderComMemoria(String numero, String mensagem, String resposta) {
        conversationMemory.addUserMessage(numero, mensagem);
        conversationMemory.addAssistantMessage(numero, resposta);

        return resposta;
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
        return texto.contains(" " + termo + " ") || texto.startsWith(termo + " ") || texto.endsWith(" " + termo) || texto.equals(termo);
    }

    private boolean estaVazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String formatarHorario(String horario) {
        // Formata "09:00" para "9h"
        if (horario == null || horario.isBlank()) {
            return horario;
        }
        String[] partes = horario.split(":");
        if (partes.length == 2) {
            int hora = Integer.parseInt(partes[0]);
            return hora + "h";
        }
        return horario;
    }

    private String formatarListaHorarios(List<String> horarios) {
        if (horarios == null || horarios.isEmpty()) {
            return "";
        }
        if (horarios.size() == 1) {
            return horarios.get(0);
        }
        if (horarios.size() == 2) {
            return horarios.get(0) + " e " + horarios.get(1);
        }
        // Para 3 ou mais, junta todos com vírgula e adiciona "e" antes do último (sem vírgula antes do "e")
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < horarios.size(); i++) {
            if (i > 0) {
                if (i == horarios.size() - 1) {
                    sb.append(" e ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(horarios.get(i));
        }
        return sb.toString();
    }

    private String extrairServico(String texto) {
        if (contem(texto, "pe e mao", "pé e mão")) return "pé e mão";
        if (contem(texto, "manicure")) return "manicure";
        if (contem(texto, "pedicure")) return "pedicure";
        if (contem(texto, "unha", "mao", "mão")) return "unha";
        if (contemPalavra(texto, "pe")) return "pé";
        if (contem(texto, "cabelo", "corte", "escova", "progressiva")) return "cabelo";
        if (contem(texto, "sobrancelha")) return "sobrancelha";
        if (contem(texto, "cilios", "cílios")) return "cílios";
        if (contem(texto, "maquiagem")) return "maquiagem";
        return null;
    }

    private String normalizar(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return semAcentos.toLowerCase(Locale.ROOT);
    }

}
