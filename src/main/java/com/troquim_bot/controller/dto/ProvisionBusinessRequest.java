package com.troquim_bot.controller.dto;

import java.util.List;

/**
 * Payload administrativo de onboarding do tenant corrente.
 *
 * O Controller apenas traduz estes dados para casos de uso de Application existentes.
 * Nenhuma regra de catálogo, agenda, identidade pública ou publicação vive neste DTO.
 */
public record ProvisionBusinessRequest(
        BusinessInput business,
        List<ServiceInput> services,
        ProfessionalInput professional,
        List<DayScheduleInput> businessHours,
        PublicProfileInput publicProfile) {

    public record BusinessInput(String name, String phone, String address) {
    }

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

    public record PublicProfileInput(String slug,
                                     String publicName,
                                     String shortDescription,
                                     String publicPhone,
                                     String publicAddress,
                                     boolean publish) {
    }
}
