package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.BusinessResponse;
import com.troquim_bot.controller.dto.UpdateBusinessRequest;
import com.troquim_bot.application.business.BusinessApplicationService;
import com.troquim_bot.business.Business;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST para gerenciamento de Business.
 * MVP assume apenas 1 salão.
 */
@RestController
@RequestMapping("/business")
public class BusinessController {

    private final BusinessApplicationService businessApplicationService;

    public BusinessController(BusinessApplicationService businessApplicationService) {
        this.businessApplicationService = businessApplicationService;
    }

    /**
     * GET /business
     * Retorna o Business atual.
     * Se não existir, cria um Business padrão do MVP.
     */
    @GetMapping
    public ResponseEntity<BusinessResponse> getBusiness() {
        Business business = businessApplicationService.buscarBusinessAtual()
            .orElseGet(() -> businessApplicationService.criarBusinessPadrao(
                "Meu Salão",
                null,
                null
            ));

        return ResponseEntity.ok(BusinessResponse.from(business));
    }

    /**
     * PUT /business
     * Atualiza dados básicos do Business (name, phone, address).
     */
    @PutMapping
    public ResponseEntity<BusinessResponse> updateBusiness(@RequestBody UpdateBusinessRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        // Atualiza apenas campos fornecidos (não nulos/vazios)
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            businessApplicationService.atualizarNome(request.getName().trim());
        }

        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            businessApplicationService.atualizarTelefone(request.getPhone().trim());
        }

        if (request.getAddress() != null && !request.getAddress().trim().isEmpty()) {
            businessApplicationService.atualizarEndereco(request.getAddress().trim());
        }

        Business business = businessApplicationService.buscarBusinessAtual()
            .orElseThrow(() -> new IllegalStateException("Business não encontrado após atualização"));

        return ResponseEntity.ok(BusinessResponse.from(business));
    }
}