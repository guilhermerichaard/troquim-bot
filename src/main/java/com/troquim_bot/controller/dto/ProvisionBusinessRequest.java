package com.troquim_bot.controller.dto;

import java.util.List;

/**
 * Payload administrativo de provisionamento inicial.
 *
 * O Controller apenas traduz estes dados para o caso de uso ProvisionarNegocio.
 * Nenhuma regra de catálogo, vínculo ou disponibilidade vive neste DTO.
 */
public record ProvisionBusinessRequest(
        List<ServiceInput> services,
        ProfessionalInput professional,
        List<DayScheduleInput> businessHours) {

    public record ServiceInput(String name, int durationMinutes) {
    }

    public record ProfessionalInput(String name,
                                    String phone,
                                    List<String> services,
                                    List<DayScheduleInput> availability) {
    }

    public record DayScheduleInput(String day, List<PeriodInput> periods) {
    }

    public record PeriodInput(String start, String end) {
    }
}
