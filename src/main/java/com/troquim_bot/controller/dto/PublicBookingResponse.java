package com.troquim_bot.controller.dto;

import com.troquim_bot.application.booking.CriarAgendamentoPublico;

/**
 * Resposta de sucesso do POST público de agendamento (200/201).
 *
 * Deliberadamente NÃO carrega: BusinessId, CustomerId, AppointmentId, ReservationId,
 * command key, fingerprint, telefone ou qualquer enum interno de status.
 */
public class PublicBookingResponse {

    private String status;
    private String serviceId;
    private String professionalId;
    private String date;
    private String time;

    public PublicBookingResponse() {
    }

    public PublicBookingResponse(String status, String serviceId, String professionalId,
                                 String date, String time) {
        this.status = status;
        this.serviceId = serviceId;
        this.professionalId = professionalId;
        this.date = date;
        this.time = time;
    }

    public static PublicBookingResponse from(CriarAgendamentoPublico.Resultado resultado) {
        return new PublicBookingResponse(
                "CONFIRMED",
                resultado.servico().getValue().toString(),
                resultado.profissional().getValue().toString(),
                resultado.data().toString(),
                resultado.horario().toString());
    }

    public String getStatus() {
        return status;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getProfessionalId() {
        return professionalId;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }
}
