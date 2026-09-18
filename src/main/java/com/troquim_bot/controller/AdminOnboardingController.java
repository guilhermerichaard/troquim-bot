package com.troquim_bot.controller;

import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.controller.dto.ProvisionBusinessRequest;
import com.troquim_bot.controller.dto.ProvisionBusinessResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Porta administrativa fina para o onboarding do tenant corrente.
 *
 * A rota não cria Service/Professional/Availability diretamente. Ela apenas converte
 * o payload HTTP e delega a operação inteira ao caso de uso idempotente
 * {@link ProvisionarNegocio}, mantendo Application/Domain como fonte única.
 */
@RestController
@RequestMapping("/api/v1/admin/onboarding")
public class AdminOnboardingController {

    private final ProvisionarNegocio provisionarNegocio;
    private final TenantProvider tenantProvider;

    public AdminOnboardingController(ProvisionarNegocio provisionarNegocio,
                                     TenantProvider tenantProvider) {
        this.provisionarNegocio = provisionarNegocio;
        this.tenantProvider = tenantProvider;
    }

    @PostMapping("/provision")
    public ResponseEntity<?> provisionar(@RequestBody ProvisionBusinessRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payload obrigatório"));
        }

        try {
            var businessId = tenantProvider.currentBusinessId();

            List<ProvisionarNegocio.ServicoDesejado> services =
                    request.services() == null ? List.of() : request.services().stream()
                            .map(service -> new ProvisionarNegocio.ServicoDesejado(
                                    service.name(), service.durationMinutes()))
                            .toList();

            ProvisionarNegocio.ProfissionalDesejado professional =
                    toProfessional(request.professional());

            BusinessHours businessHours = request.businessHours() == null
                    ? null
                    : BusinessHours.deSemana(toSchedule(request.businessHours()));

            ProvisionarNegocio.Resultado result = provisionarNegocio.provisionar(
                    businessId, services, professional, businessHours);

            return ResponseEntity.ok(
                    ProvisionBusinessResponse.from(businessId.getValue(), result));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return ResponseEntity.badRequest().body(Map.of("error", invalid.getMessage()));
        }
    }

    private static ProvisionarNegocio.ProfissionalDesejado toProfessional(
            ProvisionBusinessRequest.ProfessionalInput input) {
        if (input == null) {
            return null;
        }

        return new ProvisionarNegocio.ProfissionalDesejado(
                input.name(),
                input.phone(),
                input.services() == null ? List.of() : List.copyOf(input.services()),
                input.availability() == null
                        ? Map.of()
                        : toSchedule(input.availability()));
    }

    private static Map<DiaSemana, List<IntervaloDeHorario>> toSchedule(
            List<ProvisionBusinessRequest.DayScheduleInput> days) {
        Map<DiaSemana, List<IntervaloDeHorario>> result = new EnumMap<>(DiaSemana.class);

        for (ProvisionBusinessRequest.DayScheduleInput dayInput : days) {
            if (dayInput == null) {
                throw new IllegalArgumentException("Dia de expediente/disponibilidade é obrigatório");
            }

            DiaSemana day = parseDay(dayInput.day());
            List<IntervaloDeHorario> target =
                    result.computeIfAbsent(day, ignored -> new ArrayList<>());

            if (dayInput.periods() == null) {
                continue;
            }

            for (ProvisionBusinessRequest.PeriodInput period : dayInput.periods()) {
                if (period == null) {
                    throw new IllegalArgumentException("Período é obrigatório");
                }
                target.add(IntervaloDeHorario.de(
                        LocalTime.parse(period.start()),
                        LocalTime.parse(period.end())));
            }
        }

        Map<DiaSemana, List<IntervaloDeHorario>> immutable = new EnumMap<>(DiaSemana.class);
        result.forEach((day, periods) -> immutable.put(day, List.copyOf(periods)));
        return immutable;
    }

    private static DiaSemana parseDay(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Dia é obrigatório");
        }

        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(Locale.ROOT);

        return DiaSemana.valueOf(normalized);
    }
}
