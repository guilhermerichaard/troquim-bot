package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.AppointmentResponse;
import com.troquim_bot.controller.dto.CreateAppointmentRequest;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentId;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST para gerenciamento de Appointments.
 */
@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentApplicationService appointmentApplicationService;

    public AppointmentController(AppointmentApplicationService appointmentApplicationService) {
        this.appointmentApplicationService = appointmentApplicationService;
    }

    /**
     * GET /appointments
     * Lista todos os agendamentos.
     */
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> listarTodos() {
        List<Appointment> appointments = appointmentApplicationService.listarTodos();
        List<AppointmentResponse> response = appointments.stream()
            .map(AppointmentResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /appointments/{id}
     * Busca um agendamento por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> buscarPorId(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            Appointment appointment = appointmentApplicationService.buscarPorId(AppointmentId.from(uuid))
                .orElse(null);

            if (appointment == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(AppointmentResponse.from(appointment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /appointments
     * Cria um novo agendamento.
     */
    @PostMapping
    public ResponseEntity<AppointmentResponse> criar(@RequestBody CreateAppointmentRequest request) {
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

            Appointment appointment = appointmentApplicationService.criarAgendamento(
                customerId, professionalId, serviceId, availabilityId,
                date, startTime, endTime
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /appointments/from-reservation/{reservationId}
     * Cria um agendamento a partir de uma reserva.
     */
    @PostMapping("/from-reservation/{reservationId}")
    public ResponseEntity<AppointmentResponse> criarDeReserva(@PathVariable String reservationId) {
        try {
            UUID uuid = UUID.fromString(reservationId);
            ReservationId rid = ReservationId.from(uuid);

            Appointment appointment = appointmentApplicationService.criarAgendamentoDeReserva(rid);

            return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * PUT /appointments/{id}/confirm
     * Confirma um agendamento.
     */
    @PutMapping("/{id}/confirm")
    public ResponseEntity<AppointmentResponse> confirmar(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            AppointmentId appointmentId = AppointmentId.from(uuid);

            Appointment appointment = appointmentApplicationService.confirmarAgendamento(appointmentId);

            return ResponseEntity.ok(AppointmentResponse.from(appointment));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * PUT /appointments/{id}/cancel
     * Cancela um agendamento.
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancelar(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            AppointmentId appointmentId = AppointmentId.from(uuid);

            Appointment appointment = appointmentApplicationService.cancelarAgendamento(appointmentId);

            return ResponseEntity.ok(AppointmentResponse.from(appointment));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * PUT /appointments/{id}/complete
     * Conclui um agendamento.
     */
    @PutMapping("/{id}/complete")
    public ResponseEntity<AppointmentResponse> concluir(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            AppointmentId appointmentId = AppointmentId.from(uuid);

            Appointment appointment = appointmentApplicationService.concluirAgendamento(appointmentId);

            return ResponseEntity.ok(AppointmentResponse.from(appointment));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DELETE /appointments/{id}
     * Cancela um agendamento (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            AppointmentId appointmentId = AppointmentId.from(uuid);

            appointmentApplicationService.cancelarAgendamento(appointmentId);

            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}