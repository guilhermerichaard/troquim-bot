package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.CreateServiceRequest;
import com.troquim_bot.controller.dto.ServiceResponse;
import com.troquim_bot.controller.dto.UpdateServiceRequest;
import com.troquim_bot.application.service.ServiceApplicationService;
import com.troquim_bot.service.Service;
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

import java.util.List;
import java.util.UUID;

/**
 * Controller REST para gerenciamento de Services.
 */
@RestController
@RequestMapping("/services")
public class ServiceController {

    private final ServiceApplicationService serviceApplicationService;

    public ServiceController(ServiceApplicationService serviceApplicationService) {
        this.serviceApplicationService = serviceApplicationService;
    }

    /**
     * GET /services
     * Lista todos os serviços.
     */
    @GetMapping
    public ResponseEntity<List<ServiceResponse>> listarTodos() {
        List<Service> services = serviceApplicationService.listarTodos();
        List<ServiceResponse> response = services.stream()
            .map(ServiceResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /services/{id}
     * Busca um serviço por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> buscarPorId(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            Service service = serviceApplicationService.buscarPorId(ServiceId.from(uuid))
                .orElse(null);
            
            if (service == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(ServiceResponse.from(service));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /services
     * Cria um novo serviço.
     */
    @PostMapping
    public ResponseEntity<ServiceResponse> criar(@RequestBody CreateServiceRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        Service service = serviceApplicationService.criarServico(
            request.getName(),
            request.getDescription(),
            request.getDurationMinutes(),
            com.troquim_bot.common.valueobject.Money.of(request.getPrice(), "BRL")
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceResponse.from(service));
    }

    /**
     * PUT /services/{id}
     * Atualiza um serviço existente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> atualizar(@PathVariable String id, @RequestBody UpdateServiceRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UUID uuid = UUID.fromString(id);
            ServiceId serviceId = ServiceId.from(uuid);

            // Atualiza apenas campos fornecidos
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                serviceApplicationService.atualizarNome(serviceId, request.getName().trim());
            }

            if (request.getDescription() != null) {
                serviceApplicationService.atualizarDescricao(serviceId, request.getDescription().trim());
            }

            if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
                serviceApplicationService.atualizarDuracao(serviceId, request.getDurationMinutes());
            }

            if (request.getPrice() != null && request.getPrice() >= 0) {
                serviceApplicationService.atualizarPreco(
                    serviceId, 
                    com.troquim_bot.common.valueobject.Money.of(request.getPrice(), "BRL")
                );
            }

            Service service = serviceApplicationService.buscarPorId(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado"));

            return ResponseEntity.ok(ServiceResponse.from(service));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DELETE /services/{id}
     * Inativa um serviço (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            ServiceId serviceId = ServiceId.from(uuid);
            
            serviceApplicationService.inativarServico(serviceId);
            
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}