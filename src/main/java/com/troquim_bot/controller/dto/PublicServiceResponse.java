package com.troquim_bot.controller.dto;

import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.common.valueobject.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serviço ofertável exposto na vitrine pública.
 *
 * {@code price} é {@code null} quando o serviço não tem preço definido — NUNCA zero
 * inventado: a ausência de preço é um dado real do catálogo, e esconder isso atrás de um
 * zero faria o serviço parecer gratuito.
 */
public class PublicServiceResponse {

    private String id;
    private String name;
    private String description;
    private int durationMinutes;
    private BigDecimal price;
    private List<PublicProfessionalResponse> professionals;

    public PublicServiceResponse() {
    }

    public PublicServiceResponse(String id, String name, String description, int durationMinutes,
                                 BigDecimal price, List<PublicProfessionalResponse> professionals) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.professionals = professionals;
    }

    public static PublicServiceResponse from(ConsultarCatalogo.ItemDeCatalogo item) {
        List<PublicProfessionalResponse> profissionais = item.profissionais().stream()
                .map(PublicProfessionalResponse::from)
                .toList();
        BigDecimal preco = item.preco().map(Money::getAmount).orElse(null);
        return new PublicServiceResponse(
                item.id().getValue().toString(),
                item.nome(),
                item.descricao(),
                (int) item.duracao().toMinutes(),
                preco,
                profissionais);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public List<PublicProfessionalResponse> getProfessionals() {
        return professionals;
    }
}
