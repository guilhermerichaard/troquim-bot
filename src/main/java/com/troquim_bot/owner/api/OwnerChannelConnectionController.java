package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.AuthenticatedOwner;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService;
import com.troquim_bot.whatsapp.channel.application.ConectarWhatsAppChannelService.ConexaoInvalidaException;
import com.troquim_bot.whatsapp.channel.infrastructure.meta.MetaEmbeddedSignupProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Onboarding "Conectar WhatsApp Business" a partir do /app, ligado à identidade do
 * dono autenticado (não ao Bearer admin). Mesmo {@link ConectarWhatsAppChannelService}
 * do endpoint administrativo — sem regra duplicada, só um segundo entrypoint legítimo.
 *
 * businessId e ownerId vêm SEMPRE de {@link AuthenticatedOwner} (sessão), nunca do
 * corpo da requisição: o dono não pode escolher em nome de qual negócio conectar.
 */
@RestController
@RequestMapping("/api/v1/app/whatsapp/connection")
public class OwnerChannelConnectionController {

    private final ConectarWhatsAppChannelService service;
    private final MetaEmbeddedSignupProperties properties;

    public OwnerChannelConnectionController(ConectarWhatsAppChannelService service,
                                            MetaEmbeddedSignupProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/start")
    public ResponseEntity<IniciarResponse> iniciar(HttpServletRequest request) {
        AuthenticatedOwner identidade = identidadeOuNull(request);
        if (identidade == null) {
            return ResponseEntity.status(403).build();
        }
        if (!properties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        var resultado = service.iniciar(identidade.businessId().getValue(),
                Optional.of(identidade.ownerId().getValue()));
        return ResponseEntity.ok(new IniciarResponse(
                resultado.state(), properties.getAppId(), properties.getConfigId(),
                resultado.status().name()));
    }

    @PostMapping("/finish")
    public ResponseEntity<FinalizarResponse> finalizar(HttpServletRequest request,
                                                        @RequestBody(required = false) FinalizarRequest body) {
        AuthenticatedOwner identidade = identidadeOuNull(request);
        if (identidade == null) {
            return ResponseEntity.status(403).build();
        }
        if (!properties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (body == null || vazio(body.state()) || vazio(body.code())) {
            return ResponseEntity.badRequest().build();
        }
        var resultado = service.finalizar(identidade.businessId().getValue(),
                Optional.of(identidade.ownerId().getValue()),
                body.state().trim(), body.code().trim(), null, null);
        return ResponseEntity.ok(new FinalizarResponse(
                resultado.status().name(), resultado.wabaId().orElse(null),
                resultado.phoneNumberId().orElse(null)));
    }

    @DeleteMapping
    public ResponseEntity<Void> revogar(HttpServletRequest request) {
        AuthenticatedOwner identidade = identidadeOuNull(request);
        if (identidade == null) {
            return ResponseEntity.status(403).build();
        }
        service.revogar(identidade.businessId().getValue());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ConexaoInvalidaException.class)
    public ResponseEntity<Void> conexaoInvalida() {
        return ResponseEntity.badRequest().build();
    }

    private static AuthenticatedOwner identidadeOuNull(HttpServletRequest request) {
        return OwnerSessionCookieFilter.identidadeDe(request).orElse(null);
    }

    private static boolean vazio(String v) {
        return v == null || v.isBlank();
    }

    public record IniciarResponse(String state, String appId, String configId, String status) {}
    public record FinalizarRequest(String state, String code) {}
    public record FinalizarResponse(String status, String wabaId, String phoneNumberId) {}
}
