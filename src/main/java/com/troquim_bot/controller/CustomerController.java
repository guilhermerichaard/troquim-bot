package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.CreateCustomerRequest;
import com.troquim_bot.controller.dto.CustomerResponse;
import com.troquim_bot.controller.dto.UpdateCustomerRequest;
import com.troquim_bot.application.customer.CustomerApplicationService;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;

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
 * Controller REST para gerenciamento de Customers.
 */
@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerApplicationService customerApplicationService;

    public CustomerController(CustomerApplicationService customerApplicationService) {
        this.customerApplicationService = customerApplicationService;
    }

    /**
     * GET /customers
     * Lista todos os clientes.
     */
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> listarTodos() {
        List<Customer> customers = customerApplicationService.listarTodos();
        List<CustomerResponse> response = customers.stream()
            .map(CustomerResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /customers/{id}
     * Busca um cliente por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> buscarPorId(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            Customer customer = customerApplicationService.buscarPorId(CustomerId.from(uuid))
                .orElse(null);

            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(CustomerResponse.from(customer));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /customers
     * Cria um novo cliente.
     */
    @PostMapping
    public ResponseEntity<CustomerResponse> criar(@RequestBody CreateCustomerRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Customer customer = customerApplicationService.criarCliente(
                request.getName(),
                request.getPhone(),
                request.getNotes()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(customer));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * PUT /customers/{id}
     * Atualiza um cliente existente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> atualizar(@PathVariable String id, @RequestBody UpdateCustomerRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            UUID uuid = UUID.fromString(id);
            CustomerId customerId = CustomerId.from(uuid);

            // Atualiza apenas campos fornecidos
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                customerApplicationService.atualizarNome(customerId, request.getName().trim());
            }

            if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
                customerApplicationService.atualizarTelefone(customerId, request.getPhone().trim());
            }

            if (request.getNotes() != null) {
                customerApplicationService.atualizarObservacoes(customerId, request.getNotes());
            }

            Customer customer = customerApplicationService.buscarPorId(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));

            return ResponseEntity.ok(CustomerResponse.from(customer));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DELETE /customers/{id}
     * Inativa um cliente (soft delete).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable String id) {
        try {
            UUID uuid = UUID.fromString(id);
            CustomerId customerId = CustomerId.from(uuid);

            customerApplicationService.inativarCliente(customerId);

            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}