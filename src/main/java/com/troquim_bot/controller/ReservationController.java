package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.CreateReservationRequest;
import com.troquim_bot.controller.dto.ReservationResponse;
import com.troquim_bot.controller.dto.UpdateReservationRequest;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.reservation.ReservationId;
import com.troquim_bot.service.ServiceId;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST para gerenciamento de Reservations.
 */
@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationApplicationService reservationApplicationService;

    public ReservationController(ReservationApplicationService reservationApplicationService) {
        this.reservationApplicationService = reservationApplicationService;
    }

    /**
     * GET /reservations
     * Lista todas as reservas.
     */
    @GetMapping
    public ResponseEntity<List<ReservationResponse>> listarTodos() {
        List<Reservation> reservations = reservationApplicationService.listarTodos();
        List<ReservationResponse> response = reservations.stream()
            .map(ReservationResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /reservations/{id}
     * Busca uma reserva por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> buscarPorId(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            Reservation reservation = reservationApplicationService.buscarPorId(ReservationId.from(uuid))
                .orElse(null);

            if (reservation == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(ReservationResponse.from(reservation));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /reservations
     * Cria uma nova reserva.
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> criar(@RequestBody CreateReservationRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UUID customerUuid = UUID.fromString(request.getCustomerId());
            UUID professionalUuid = UUID.fromString(request.getProfessionalId());
            UUID serviceUuid = UUID.fromString(request.getServiceId());
            UUID availabilityUuid = UUID.fromString(request.getAvailabilityId());

            CustomerId customerId = CustomerId.from(customerUuid);
            ProfessionalId professionalId = ProfessionalId.from(professionalUuid);
            ServiceId serviceId = ServiceId.from(serviceUuid);
            AvailabilityId availabilityId = AvailabilityId.from(availabilityUuid);
            LocalDate date = LocalDate.parse(request.getDate());
            LocalTime startTime = LocalTime.parse(request.getStartTime());
            LocalTime endTime = LocalTime.parse(request.getEndTime());
            LocalDateTime expiresAt = LocalDateTime.parse(request.getExpiresAt());

            Reservation reservation = reservationApplicationService.criarReserva(
                customerId, professionalId, serviceId, availabilityId,
                date, startTime, endTime, expiresAt
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(reservation));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * PUT /reservations/{id}
     * Atualiza uma reserva existente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ReservationResponse> atualizar(@PathVariable String id, @RequestBody UpdateReservationRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UUID uuid = UUID.fromString(id);
            ReservationId reservationId = ReservationId.from(uuid);

            if (request.getDate() != null) {
                reservationApplicationService.atualizarData(reservationId, LocalDate.parse(request.getDate()));
            }
            if (request.getStartTime() != null) {
                reservationApplicationService.atualizarStartTime(reservationId, LocalTime.parse(request.getStartTime()));
            }
            if (request.getEndTime() != null) {
                reservationApplicationService.atualizarEndTime(reservationId, LocalTime.parse(request.getEndTime()));
            }
            if (request.getExpiresAt() != null) {
                reservationApplicationService.atualizarExpiresAt(reservationId, LocalDateTime.parse(request.getExpiresAt()));
            }

            Reservation reservation = reservationApplicationService.buscarPorId(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva não encontrada"));

            return ResponseEntity.ok(ReservationResponse.from(reservation));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DELETE /reservations/{id}
     * Cancela uma reserva (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            ReservationId reservationId = ReservationId.from(uuid);

            reservationApplicationService.cancelarReserva(reservationId);

            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}