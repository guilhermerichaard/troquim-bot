package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.AuthenticatedOwner;
import com.troquim_bot.owner.application.OwnerConsoleQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public OwnerConsoleController(OwnerConsoleQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview(HttpServletRequest request) {
        return owner(request).map(o -> ResponseEntity.ok(queries.overview(o)))
                .orElseGet(() -> ResponseEntity.status(403).build());
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
        return owner(request).map(o -> ResponseEntity.ok(queries.customers(o)))
                .orElseGet(() -> ResponseEntity.status(403).build());
    }

    @GetMapping("/services")
    public ResponseEntity<?> services(HttpServletRequest request) {
        return owner(request).map(o -> ResponseEntity.ok(queries.services(o)))
                .orElseGet(() -> ResponseEntity.status(403).build());
    }

    @GetMapping("/professionals")
    public ResponseEntity<?> professionals(HttpServletRequest request) {
        return owner(request).map(o -> ResponseEntity.ok(queries.professionals(o)))
                .orElseGet(() -> ResponseEntity.status(403).build());
    }

    private java.util.Optional<AuthenticatedOwner> owner(HttpServletRequest request) {
        return OwnerSessionCookieFilter.identidadeDe(request);
    }
}
