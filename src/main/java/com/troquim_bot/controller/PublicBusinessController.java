package com.troquim_bot.controller;

import com.troquim_bot.application.business.ConsultarDisponibilidadePublica;
import com.troquim_bot.application.business.ConsultarVitrinePublica;
import com.troquim_bot.controller.dto.PublicAvailabilityResponse;
import com.troquim_bot.controller.dto.PublicBusinessResponse;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * API pública SOMENTE LEITURA. Nenhum método aqui consulta um Repository diretamente: tudo
 * passa pela Application (Resolver/Consultar*Publica*), que já decide o que é seguro expor.
 *
 * Sem autenticação — protegida no {@code SecurityConfigDefaultDeny} apenas para GET nesta
 * rota exata. Nenhum POST/PUT/PATCH/DELETE existe aqui de propósito: agendamento público é
 * fora de escopo desta etapa.
 */
@RestController
@RequestMapping("/api/v1/public/businesses")
public class PublicBusinessController {

    private final ConsultarVitrinePublica consultarVitrinePublica;
    private final ConsultarDisponibilidadePublica consultarDisponibilidadePublica;

    public PublicBusinessController(ConsultarVitrinePublica consultarVitrinePublica,
                                    ConsultarDisponibilidadePublica consultarDisponibilidadePublica) {
        this.consultarVitrinePublica = consultarVitrinePublica;
        this.consultarDisponibilidadePublica = consultarDisponibilidadePublica;
    }

    /**
     * GET /api/v1/public/businesses/{slug}
     *
     * Slug inválido, inexistente, em DRAFT, ou negócio INATIVO/SUSPENSO/DELETADO: 404 — os
     * quatro casos são indistinguíveis de fora, de propósito.
     */
    @GetMapping("/{slug}")
    public ResponseEntity<PublicBusinessResponse> vitrine(@PathVariable String slug) {
        return consultarVitrinePublica.consultar(slug)
                .map(PublicBusinessResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/public/businesses/{slug}/availability?serviceId=&professionalId=&date=
     *
     * UUID ou data malformados: 400. Slug que não resolve: 404. Serviço ou profissional
     * inexistente, de outro negócio ou indisponível: 200, com a condição explicando por quê
     * — NUNCA revela se o id pertence a outro tenant.
     */
    @GetMapping("/{slug}/availability")
    public ResponseEntity<PublicAvailabilityResponse> disponibilidade(
            @PathVariable String slug,
            @RequestParam String serviceId,
            @RequestParam String professionalId,
            @RequestParam String date) {

        ServiceId servico;
        ProfessionalId profissional;
        LocalDate data;
        try {
            servico = ServiceId.from(UUID.fromString(serviceId));
            profissional = ProfessionalId.from(UUID.fromString(professionalId));
            data = LocalDate.parse(date);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }

        return consultarDisponibilidadePublica.consultar(slug, servico, profissional, data)
                .map(PublicAvailabilityResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
