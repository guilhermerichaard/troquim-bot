package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.CreateProfessionalRequest;
import com.troquim_bot.controller.dto.ProfessionalResponse;
import com.troquim_bot.controller.dto.UpdateProfessionalRequest;
import com.troquim_bot.application.professional.ProfessionalApplicationService;
import com.troquim_bot.professional.Professional;
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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller REST para gerenciamento de Professionals.
 */
@RestController
@RequestMapping("/professionals")
public class ProfessionalController {

    private final ProfessionalApplicationService professionalApplicationService;

    public ProfessionalController(ProfessionalApplicationService professionalApplicationService) {
        this.professionalApplicationService = professionalApplicationService;
    }

    /**
     * GET /professionals
     * Retorna todos os Professionals.
     */
    @GetMapping
    public ResponseEntity<List<ProfessionalResponse>> getAllProfessionals() {
        List<Professional> professionals = professionalApplicationService.buscarTodos();
        
        List<ProfessionalResponse> response = professionals.stream()
            .map(ProfessionalResponse::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /professionals/{id}
     * Retorna um Professional por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProfessionalResponse> getProfessionalById(@PathVariable String id) {
        try {
            ProfessionalId professionalId = ProfessionalId.from(java.util.UUID.fromString(id));
            
            Professional professional = professionalApplicationService.buscarPorId(professionalId)
                .orElseThrow(() -> new IllegalArgumentException("Professional não encontrado com ID: " + id));

            return ResponseEntity.ok(ProfessionalResponse.from(professional));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /professionals
     * Cria um novo Professional.
     */
    @PostMapping
    public ResponseEntity<ProfessionalResponse> createProfessional(@RequestBody CreateProfessionalRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Professional professional = professionalApplicationService.criarProfessional(
                request.getNome(),
                request.getEspecialidades(),
                request.getTelefone()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(ProfessionalResponse.from(professional));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * PUT /professionals/{id}
     * Atualiza um Professional existente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProfessionalResponse> updateProfessional(
            @PathVariable String id,
            @RequestBody UpdateProfessionalRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            ProfessionalId professionalId = ProfessionalId.from(java.util.UUID.fromString(id));
            
            // Check if professional exists
            if (!professionalApplicationService.existeProfessional(professionalId)) {
                return ResponseEntity.notFound().build();
            }

            Professional professional = professionalApplicationService.atualizarProfessional(
                professionalId,
                request.getNome(),
                request.getEspecialidades(),
                request.getTelefone()
            );

            return ResponseEntity.ok(ProfessionalResponse.from(professional));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * DELETE /professionals/{id}
     * Inativa um Professional.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfessional(@PathVariable String id) {
        try {
            ProfessionalId professionalId = ProfessionalId.from(java.util.UUID.fromString(id));
            
            // Check if professional exists
            if (!professionalApplicationService.existeProfessional(professionalId)) {
                return ResponseEntity.notFound().build();
            }

            professionalApplicationService.inativarProfessional(professionalId);

            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
