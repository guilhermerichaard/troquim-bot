package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.AuthenticatedOwner;
import com.troquim_bot.owner.application.OwnerConsoleQueryService;
import com.troquim_bot.owner.application.OwnerConsoleCommandService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * API de leitura do console do dono.
 *
 * businessId nunca entra por query/path/header: vem exclusivamente da sessão owner.
 */
@RestController
@RequestMapping("/api/v1/app")
public class OwnerConsoleController {

    private final OwnerConsoleQueryService queries;
    private final OwnerConsoleCommandService commands;

    public OwnerConsoleController(OwnerConsoleQueryService queries,
                                  OwnerConsoleCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview(HttpServletRequest request) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(queries.overview(identity.get()));
    }

    @GetMapping("/appointments")
    public ResponseEntity<?> appointments(
            HttpServletRequest request,
            @RequestParam(required = false) String date) {
        var owner = owner(request);
        if (owner.isEmpty()) return ResponseEntity.status(403).build();

        LocalDate parsed = null;
        if (date != null && !date.isBlank()) {
            try {
                parsed = LocalDate.parse(date);
            } catch (RuntimeException invalid) {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.ok(queries.appointments(owner.get(), parsed));
    }

    @GetMapping("/customers")
    public ResponseEntity<?> customers(HttpServletRequest request) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(queries.customers(identity.get()));
    }

    @GetMapping("/services")
    public ResponseEntity<?> services(HttpServletRequest request) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(queries.services(identity.get()));
    }

    @GetMapping("/professionals")
    public ResponseEntity<?> professionals(HttpServletRequest request) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(queries.professionals(identity.get()));
    }

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog(HttpServletRequest request) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(commands.catalog(identity.get()));
    }

    @GetMapping("/slots")
    public ResponseEntity<?> slots(HttpServletRequest request,
                                   @RequestParam String serviceId,
                                   @RequestParam String professionalId,
                                   @RequestParam String date) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.slots(identity.get(), serviceId, professionalId, LocalDate.parse(date)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PostMapping("/customers")
    public ResponseEntity<?> createCustomer(HttpServletRequest request,
                                            @RequestBody OwnerConsoleCommandService.CreateCustomer command) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.createCustomer(identity.get(), command));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PostMapping("/appointments")
    public ResponseEntity<?> createAppointment(HttpServletRequest request,
                                               @RequestBody OwnerConsoleCommandService.CreateAppointment command) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            var result = commands.createAppointment(identity.get(), command);
            return result.ok() ? ResponseEntity.ok(result)
                    : ResponseEntity.unprocessableEntity().body(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<?> cancelAppointment(HttpServletRequest request, @PathVariable String id) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.cancelAppointment(identity.get(), id));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PostMapping("/appointments/{id}/reschedule")
    public ResponseEntity<?> reschedule(HttpServletRequest request,
                                        @PathVariable String id,
                                        @RequestBody OwnerConsoleCommandService.Reschedule command) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.reschedule(identity.get(), id, command));
        } catch (OwnerConsoleCommandService.RescheduleRejectedException ex) {
            return ResponseEntity.unprocessableEntity().body(new ApiError(ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PostMapping("/services")
    public ResponseEntity<?> createService(HttpServletRequest request,
                                           @RequestBody OwnerConsoleCommandService.ServiceCommand command) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.createService(identity.get(), command));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<?> updateService(HttpServletRequest request, @PathVariable String id,
                                           @RequestBody OwnerConsoleCommandService.ServiceCommand command) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.updateService(identity.get(), id, command));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PostMapping("/services/{id}/status")
    public ResponseEntity<?> serviceStatus(HttpServletRequest request, @PathVariable String id,
                                           @RequestBody StatusCommand command) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.setServiceStatus(identity.get(), id, command.active()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PostMapping("/professionals")
    public ResponseEntity<?> createProfessional(HttpServletRequest request,
                                                @RequestBody OwnerConsoleCommandService.ProfessionalCommand command) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.createProfessional(identity.get(), command));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PutMapping("/professionals/{id}")
    public ResponseEntity<?> updateProfessional(HttpServletRequest request, @PathVariable String id,
                                                @RequestBody OwnerConsoleCommandService.ProfessionalCommand command) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.updateProfessional(identity.get(), id, command));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    @PostMapping("/professionals/{id}/status")
    public ResponseEntity<?> professionalStatus(HttpServletRequest request, @PathVariable String id,
                                                @RequestBody StatusCommand command) {
        var identity = owner(request);
        if (identity.isEmpty()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(commands.setProfessionalStatus(identity.get(), id, command.active()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
        }
    }

    private java.util.Optional<AuthenticatedOwner> owner(HttpServletRequest request) {
        return OwnerSessionCookieFilter.identidadeDe(request);
    }

    public record StatusCommand(boolean active) {}
    public record ApiError(String error) {}
}
