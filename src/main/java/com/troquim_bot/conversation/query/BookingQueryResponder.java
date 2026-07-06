package com.troquim_bot.conversation.query;

import com.troquim_bot.ai.intent.IntentType;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.conversation.language.ConversationTextUtils;
import com.troquim_bot.conversation.state.AppointmentDraft;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.customer.CustomerId;

import java.util.List;
import java.util.Optional;

/**
 * Responde consultas de leitura sobre agendamentos e disponibilidade.
 *
 * Não cria, altera ou cancela agendamentos. Essa classe existe para retirar do
 * ConversationService a responsabilidade de consultar Application Services e
 * formatar respostas de consulta.
 */
public class BookingQueryResponder {

    private final AppointmentApplicationService appointmentApplicationService;
    private final AvailabilityApplicationService availabilityApplicationService;

    public BookingQueryResponder(AppointmentApplicationService appointmentApplicationService,
                                 AvailabilityApplicationService availabilityApplicationService) {
        this.appointmentApplicationService = appointmentApplicationService;
        this.availabilityApplicationService = availabilityApplicationService;
    }

    public Optional<String> responderConsultaAgendamento(String numero,
                                                          IntentType intentType,
                                                          String mensagem,
                                                          ConversationState conversationState) {
        if (!isConsultaAgendamento(intentType, mensagem)) {
            return Optional.empty();
        }

        CustomerId customerId = CustomerId.fromPhone(numero);
        Optional<Appointment> appointment = appointmentApplicationService.buscarAtivoPorCliente(customerId);

        return Optional.of(appointment
                .map(a -> montarResumoAgendamento(a, conversationState))
                .orElse("Você ainda não tem uma solicitação de agendamento registrada."));
    }

    public Optional<String> responderConsultaDisponibilidade(String mensagem, ConversationState conversationState) {
        String texto = ConversationTextUtils.normalizar(mensagem);

        boolean temDia = ConversationTextUtils.contem(texto,
                "segunda", "terca", "quarta", "quinta", "sexta", "sabado", "domingo", "hoje", "amanha");
        boolean perguntaHorario = ConversationTextUtils.contem(texto,
                "horario de funcionamento", "horario comercial", "que horas abre",
                "que horas fecha", "horario de atendimento", "funciona que horas");

        if (!temDia || perguntaHorario) {
            return Optional.empty();
        }

        String servico = extrairServico(texto);
        AppointmentDraft draft = conversationState.getDraftAtual();
        if (ConversationTextUtils.estaVazio(servico) && draft != null) {
            servico = draft.getServico();
        }

        if (ConversationTextUtils.estaVazio(servico)) {
            return Optional.of("Qual serviço você gostaria de agendar?");
        }

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

    private boolean isConsultaAgendamento(IntentType intentType, String mensagem) {
        if (intentType == IntentType.CONSULTAR_AGENDAMENTO
                || intentType == IntentType.CONSULTAR_DIA_AGENDADO
                || intentType == IntentType.CONSULTAR_HORARIO_AGENDADO
                || intentType == IntentType.CONSULTAR_SERVICO_AGENDADO) {
            return true;
        }

        String texto = ConversationTextUtils.normalizar(mensagem);
        return ConversationTextUtils.contem(texto,
                "agendei", "marquei", "qual meu agendamento", "meu agendamento", "qual agendamento",
                "qual horario", "que horario", "qual servico", "que servico",
                "marquei para quando", "agendei para quando");
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

    private String formatarHorario(String horario) {
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

    private String extrairDia(String texto) {
        if (ConversationTextUtils.contem(texto, "segunda")) return "segunda";
        if (ConversationTextUtils.contem(texto, "terca", "terça")) return "terça";
        if (ConversationTextUtils.contem(texto, "quarta")) return "quarta";
        if (ConversationTextUtils.contem(texto, "quinta")) return "quinta";
        if (ConversationTextUtils.contem(texto, "sexta")) return "sexta";
        if (ConversationTextUtils.contem(texto, "sabado", "sábado")) return "sábado";
        if (ConversationTextUtils.contem(texto, "domingo")) return "domingo";
        if (ConversationTextUtils.contem(texto, "hoje")) return "hoje";
        if (ConversationTextUtils.contem(texto, "amanha", "amanhã")) return "amanhã";
        return null;
    }

    private String extrairServico(String texto) {
        if (ConversationTextUtils.contem(texto, "pe e mao", "pé e mão")) return "pé e mão";
        if (ConversationTextUtils.contem(texto, "manicure")) return "manicure";
        if (ConversationTextUtils.contem(texto, "pedicure")) return "pedicure";
        if (ConversationTextUtils.contem(texto, "unha", "mao", "mão")) return "unha";
        if (ConversationTextUtils.contemPalavra(texto, "pe")) return "pé";
        if (ConversationTextUtils.contem(texto, "cabelo", "corte", "escova", "progressiva")) return "cabelo";
        if (ConversationTextUtils.contem(texto, "sobrancelha")) return "sobrancelha";
        if (ConversationTextUtils.contem(texto, "cilios", "cílios")) return "cílios";
        if (ConversationTextUtils.contem(texto, "maquiagem")) return "maquiagem";
        return null;
    }
}
