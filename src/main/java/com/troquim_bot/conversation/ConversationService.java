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
import com.troquim_bot.conversation.context.ConversationContextResolver;
import com.troquim_bot.customer.CustomerProfile;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.schedule.AppointmentBookingService;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import org.springframework.stereotype.Service;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.conversation.language.ConversationTextUtils;
import com.troquim_bot.conversation.query.BookingQueryResponder;

import java.util.List;
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
    private final StrictMvpMenuService strictMvpMenuService;
    private final BookingQueryResponder bookingQueryResponder;
    private final ConversationContextResolver conversationContextResolver;

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
                               com.troquim_bot.application.availability.AvailabilityApplicationService availabilityApplicationService,
                               StrictMvpMenuService strictMvpMenuService) {
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
        this.strictMvpMenuService = strictMvpMenuService;
        this.bookingQueryResponder = new BookingQueryResponder(
                appointmentApplicationService,
                availabilityApplicationService,
                customerProfileService
        );
        this.conversationContextResolver = new ConversationContextResolver(
                conversationStateService,
                customerProfileService
        );
    }

    public String gerarResposta(String numero, String mensagem) {
        return gerarResposta(numero, mensagem, null);
    }

    public String gerarResposta(String numero, String mensagem, com.troquim_bot.application.intent.IntentType v2IntentType) {
        if (mensagem == null || mensagem.isBlank()) {
            return "Não consegui entender sua mensagem. Pode me enviar novamente?";
        }

        // STRICT_MVP: delegar ao menu numerado primeiro
        if (strictMvpMenuService.isStrictMvpEnabled()) {
            ConversationState state = conversationStateService.buscarPorNumero(numero);
            String respostaMenu = strictMvpMenuService.processarMenu(numero, mensagem, state);
            if (respostaMenu != null) {
                return respostaMenu;
            }
        }

        IntentType intentType;
        if (v2IntentType != null) {
            intentType = mapV2ToLegacyIntentType(v2IntentType);
        } else {
            intentType = intentService.classificar(mensagem);
        }
        CustomerProfile customerProfile = conversationContextResolver.carregarPerfil(numero, intentType);
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

        customerProfile = conversationContextResolver.sincronizarNome(numero, nomeInformado, conversationState, customerProfile);
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

    private Optional<String> executarIntencao(ConversationRoute route,
                                              String numero,
                                              String mensagem,
                                              ConversationState conversationState,
                                              CustomerProfile customerProfile,
                                              Optional<String> nomeInformado) {
        IntentType intentType = route.intentType();

        // Cancelamento tem prioridade máxima: não pode ser interceptado por consulta de agendamento
        if (intentType == IntentType.CANCELAR_AGENDAMENTO) {
            return processarCancelamento(numero);
        }

        // Verifica consulta de agendamento ANTES de retornar respostas rápidas
        Optional<String> respostaAgendamento = bookingQueryResponder.responderConsultaAgendamento(numero, intentType, mensagem, conversationState);
        if (respostaAgendamento.isPresent()) {
            return respostaAgendamento;
        }

        // Verifica consulta de disponibilidade ANTES de retornar respostas rápidas de horário de funcionamento
        Optional<String> respostaDisponibilidade = bookingQueryResponder.responderConsultaDisponibilidade(mensagem, conversationState);
        if (respostaDisponibilidade.isPresent()) {
            return respostaDisponibilidade;
        }

        Optional<String> respostaRapida = quickResponseService.buscarResposta(intentType);

        if (respostaRapida.isPresent() && deveUsarRespostaRapida(intentType, mensagem)) {
            if (intentType == IntentType.SAUDACAO) {
                return Optional.of(conversationContextResolver.montarSaudacao(customerProfile));
            }

            return respostaRapida;
        }

        // Verifica se é uma confirmação curta (como "ata", "certo", "beleza", etc.)
        if (intentType == IntentType.DESCONHECIDO && isConfirmacaoCurta(mensagem)) {
            return Optional.of("Certo.");
        }

        // Perguntas fora de escopo de agendamento: redireciona educadamente
        if (intentType == IntentType.DESCONHECIDO && isOutOfScopeQuestion(mensagem)) {
            return Optional.of("Posso te ajudar melhor com agendamentos. Qual serviço você deseja marcar?");
        }

        if (intentType == IntentType.LEMBRAR_CLIENTE) {
            return Optional.of(conversationContextResolver.montarRespostaLembranca(customerProfile));
        }

        if (intentType == IntentType.CONSULTAR_NOME) {
            if (nomeInformado.isPresent()) {
                return Optional.of("Perfeito, " + nomeInformado.get() + ". Vou lembrar.");
            }

            return Optional.of(conversationContextResolver.montarRespostaNome(customerProfile));
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

    private Optional<String> executarFluxo(ConversationRoute route,
                                           ConversationState conversationState,
                                           String mensagem) {
        if (!route.continuaFluxo()) {
            return Optional.empty();
        }

        return conversationStateService.montarRespostaAutomatica(conversationState, mensagem);
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
        return resultado != null && ConversationTextUtils.normalizar(resultado).contains("nao esta mais disponivel");
    }

    private boolean draftAtualCompleto(ConversationState conversationState) {
        AppointmentDraft draft = conversationState.getDraftAtual();
        return draft != null && draft.isCompleto();
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
        String texto = ConversationTextUtils.normalizar(mensagem);
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

        if (resposta == null) {
            String fallback = "Não consegui entender completamente sua mensagem. Pode reformular ou me dizer qual serviço deseja agendar?";
            conversationMemory.addAssistantMessage(numero, fallback);
            return fallback;
        }

        conversationMemory.addAssistantMessage(numero, resposta);

        return resposta;
    }

    private String responderComMemoria(String numero, String mensagem, String resposta) {
        conversationMemory.addUserMessage(numero, mensagem);
        conversationMemory.addAssistantMessage(numero, resposta);

        return resposta;
    }

    private boolean isOutOfScopeQuestion(String mensagem) {
        String texto = ConversationTextUtils.normalizar(mensagem);
        // Detects non-appointment questions like "Corinthians ou Flamengo?"
        if (!texto.contains("?")) {
            return false;
        }
        // Verify it doesn't match any known appointment-related terms
        boolean hasAppointmentTerms = ConversationTextUtils.contem(texto,
                "servico", "servicos", "horario", "dia", "agenda", "agendamento",
                "marcar", "unha", "cabelo", "corte", "manicure", "pedicure",
                "disponivel", "disponibilidade", "cancelar", "nome",
                "endereco", "localizacao", "preco", "valor", "orcamento");
        return !hasAppointmentTerms;
    }

    private Optional<String> processarCancelamento(String numero) {
        // Identidade oficial, sem criar Customer nem derivar id por telefone.
        List<Appointment> ativos = customerProfileService.localizarIdOficial(numero)
                .map(appointmentApplicationService::listarAtivosPorCliente)
                .orElse(List.of());

        if (ativos.isEmpty()) {
            return Optional.of("Você não tem nenhum agendamento ativo para cancelar.");
        }

        if (ativos.size() == 1) {
            Appointment appointment = ativos.get(0);
            appointmentApplicationService.cancelarAgendamento(appointment.getId());
            return Optional.of("Seu agendamento foi cancelado com sucesso.");
        }

        StringBuilder sb = new StringBuilder("Você tem mais de um agendamento. Qual deles você gostaria de cancelar?\n");
        for (int i = 0; i < ativos.size(); i++) {
            Appointment a = ativos.get(i);
            sb.append(i + 1).append(". ").append(a.getDate()).append(" às ").append(a.getStartTime()).append("\n");
        }
        return Optional.of(sb.toString().trim());
    }

    private IntentType mapV2ToLegacyIntentType(com.troquim_bot.application.intent.IntentType v2Type) {
        return switch (v2Type) {
            case GREETING -> IntentType.SAUDACAO;
            case BOOK_APPOINTMENT, RESCHEDULE_APPOINTMENT -> IntentType.AGENDAMENTO;
            case CANCEL_APPOINTMENT -> IntentType.DESCONHECIDO; // Cancelamento não mapeia para intenção de agendamento
            case CHECK_AVAILABILITY -> IntentType.DESCONHECIDO;
            case ASK_SERVICES -> IntentType.CONSULTAR_SERVICOS;
            case ASK_HOURS -> IntentType.CONSULTAR_HORARIOS;
            case ASK_LOCATION -> IntentType.CONSULTAR_ENDERECO;
            case ASK_WHO_ARE_YOU -> IntentType.CONSULTAR_QUEM_SOU;
            case HUMAN_ATTENDANT -> IntentType.HUMANO;
            case AGRADECIMENTO -> IntentType.AGRADECIMENTO;
            case DESPEDIDA -> IntentType.DESPEDIDA;
            case LEMBRAR_CLIENTE -> IntentType.LEMBRAR_CLIENTE;
            case CONSULTAR_AGENDAMENTO -> IntentType.CONSULTAR_AGENDAMENTO;
            case CONSULTAR_DIA_AGENDADO -> IntentType.CONSULTAR_DIA_AGENDADO;
            case CONSULTAR_HORARIO_AGENDADO -> IntentType.CONSULTAR_HORARIO_AGENDADO;
            case CONSULTAR_SERVICO_AGENDADO -> IntentType.CONSULTAR_SERVICO_AGENDADO;
            case CONSULTAR_NOME -> IntentType.CONSULTAR_NOME;
            case UNKNOWN -> IntentType.DESCONHECIDO;
        };
    }

}
