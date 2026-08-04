package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.AvailabilityResponse;
import com.troquim_bot.controller.dto.CreateAvailabilityRequest;
import com.troquim_bot.controller.dto.UpdateAvailabilityRequest;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.professional.ProfessionalId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST administrativo de disponibilidade.
 *
 * FRONTEIRA DO TENANT. É AQUI — e só aqui — que o negócio corrente é resolvido, a partir do
 * {@link TenantProvider}, e passado explicitamente para baixo. A Application e o Domain
 * nunca deduzem tenant: recebem-no por argumento. Enquanto esta API é administrativa e
 * mono-tenant, o ponto de resolução fica visível num lugar só, em vez de espalhado como
 * contexto implícito por todas as camadas.
 */
@RestController
@RequestMapping("/availability")
public class AvailabilityController {

    private final AvailabilityApplicationService availabilityApplicationService;
    private final TenantProvider tenantProvider;

    public AvailabilityController(AvailabilityApplicationService availabilityApplicationService,
                                  TenantProvider tenantProvider) {
        this.availabilityApplicationService = availabilityApplicationService;
        this.tenantProvider = tenantProvider;
    }

    /**
     * GET /availability
     * Lista as disponibilidades DO NEGÓCIO CORRENTE. Não existe listagem global.
     */
    @GetMapping
    public ResponseEntity<List<AvailabilityResponse>> listarTodos() {
        List<Availability> availabilities =
                availabilityApplicationService.listarPorNegocio(tenantProvider.currentBusinessId());
        List<AvailabilityResponse> response = availabilities.stream()
            .map(AvailabilityResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /availability/{id}
     * Busca uma disponibilidade por ID, dentro do negócio corrente.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AvailabilityResponse> buscarPorId(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            // Id de outro negócio devolve 404, e não 403: não revela que o id existe.
            Availability availability = availabilityApplicationService
                .buscarPorId(tenantProvider.currentBusinessId(), AvailabilityId.from(uuid))
                .orElse(null);

            if (availability == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(AvailabilityResponse.from(availability));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /availability
     * Cria uma nova disponibilidade.
     */
    @PostMapping
    public ResponseEntity<AvailabilityResponse> criar(@RequestBody CreateAvailabilityRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UUID professionalUuid = UUID.fromString(request.getProfessionalId());
            ProfessionalId professionalId = ProfessionalId.from(professionalUuid);
            DiaSemana dayOfWeek = DiaSemana.valueOf(request.getDayOfWeek());
            LocalTime startTime = LocalTime.parse(request.getStartTime());
            LocalTime endTime = LocalTime.parse(request.getEndTime());

            Availability availability = availabilityApplicationService.criarDisponibilidade(
                tenantProvider.currentBusinessId(),
                professionalId,
                dayOfWeek,
                startTime,
                endTime
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(AvailabilityResponse.from(availability));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * PUT /availability/{id}
     * Atualiza uma disponibilidade existente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AvailabilityResponse> atualizar(@PathVariable String id, @RequestBody UpdateAvailabilityRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UUID uuid = UUID.fromString(id);
            AvailabilityId availabilityId = AvailabilityId.from(uuid);
            BusinessId tenant = tenantProvider.currentBusinessId();

            // Atualiza apenas campos fornecidos
            if (request.getDayOfWeek() != null && request.getStartTime() != null && request.getEndTime() != null) {
                DiaSemana dayOfWeek = DiaSemana.valueOf(request.getDayOfWeek());
                LocalTime startTime = LocalTime.parse(request.getStartTime());
                LocalTime endTime = LocalTime.parse(request.getEndTime());
                availabilityApplicationService.atualizarHorario(tenant, availabilityId, dayOfWeek, startTime, endTime);
            } else {
                if (request.getDayOfWeek() != null) {
                    availabilityApplicationService.atualizarDayOfWeek(tenant, availabilityId, DiaSemana.valueOf(request.getDayOfWeek()));
                }
                if (request.getStartTime() != null) {
                    availabilityApplicationService.atualizarStartTime(tenant, availabilityId, LocalTime.parse(request.getStartTime()));
                }
                if (request.getEndTime() != null) {
                    availabilityApplicationService.atualizarEndTime(tenant, availabilityId, LocalTime.parse(request.getEndTime()));
                }
            }

            Availability availability = availabilityApplicationService.buscarPorId(tenant, availabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Disponibilidade não encontrada"));

            return ResponseEntity.ok(AvailabilityResponse.from(availability));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DELETE /availability/{id}
     * Inativa uma disponibilidade (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            AvailabilityId availabilityId = AvailabilityId.from(uuid);

            availabilityApplicationService.inativarDisponibilidade(
                tenantProvider.currentBusinessId(), availabilityId);

            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}