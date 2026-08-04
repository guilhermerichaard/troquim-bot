package com.troquim_bot.controller;

import com.troquim_bot.controller.dto.BusinessResponse;
import com.troquim_bot.controller.dto.UpdateBusinessRequest;
import com.troquim_bot.application.business.BusinessApplicationService;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller REST administrativo para gerenciamento de Business.
 *
 * TODA rota exige o {@link BusinessId} explícito no path — não existe "o negócio atual".
 * Protegida por ADMIN via {@code /business/**} na configuração de segurança.
 */
@RestController
@RequestMapping("/business")
public class BusinessController {

    private final BusinessApplicationService businessApplicationService;

    public BusinessController(BusinessApplicationService businessApplicationService) {
        this.businessApplicationService = businessApplicationService;
    }

    /**
     * GET /business/{businessId}
     * Retorna o Business informado. NÃO cria um automaticamente: cadastro é
     * responsabilidade explícita de CadastrarNegocio, nunca implícita de um GET.
     */
    @GetMapping("/{businessId}")
    public ResponseEntity<BusinessResponse> getBusiness(@PathVariable UUID businessId) {
        return businessApplicationService.buscarPorId(BusinessId.from(businessId))
            .map(BusinessResponse::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * PUT /business/{businessId}
     * Atualiza dados básicos do Business (name, phone, address) do negócio informado no
     * path. O corpo não carrega BusinessId — a identidade já veio pela URL.
     */
    @PutMapping("/{businessId}")
    public ResponseEntity<BusinessResponse> updateBusiness(@PathVariable UUID businessId,
                                                            @RequestBody UpdateBusinessRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        BusinessId id = BusinessId.from(businessId);
        if (!businessApplicationService.existeBusiness(id)) {
            return ResponseEntity.notFound().build();
        }

        // Atualiza apenas campos fornecidos (não nulos/vazios)
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            businessApplicationService.atualizarNome(id, request.getName().trim());
        }

        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            businessApplicationService.atualizarTelefone(id, request.getPhone().trim());
        }

        if (request.getAddress() != null && !request.getAddress().trim().isEmpty()) {
            businessApplicationService.atualizarEndereco(id, request.getAddress().trim());
        }

        Business business = businessApplicationService.buscarPorId(id)
            .orElseThrow(() -> new IllegalStateException("Business não encontrado após atualização"));

        return ResponseEntity.ok(BusinessResponse.from(business));
    }
}
