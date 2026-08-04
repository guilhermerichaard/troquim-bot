package com.troquim_bot.controller.dto;

import com.troquim_bot.application.business.ConsultarVitrinePublica;
import com.troquim_bot.business.BusinessPublicProfile;

import java.util.List;

/**
 * Vitrine pública de um negócio. Deliberadamente NÃO carrega: BusinessId, status interno do
 * Business, PublicationStatus, timestamps, ou qualquer outro dado administrativo — só o que
 * {@link BusinessPublicProfile} e o catálogo oferecem publicamente.
 */
public class PublicBusinessResponse {

    private String slug;
    private String name;
    private String shortDescription;
    private String publicPhone;
    private String publicAddress;
    private boolean catalogConfigured;
    private List<PublicServiceResponse> services;

    public PublicBusinessResponse() {
    }

    public PublicBusinessResponse(String slug, String name, String shortDescription, String publicPhone,
                                  String publicAddress, boolean catalogConfigured,
                                  List<PublicServiceResponse> services) {
        this.slug = slug;
        this.name = name;
        this.shortDescription = shortDescription;
        this.publicPhone = publicPhone;
        this.publicAddress = publicAddress;
        this.catalogConfigured = catalogConfigured;
        this.services = services;
    }

    public static PublicBusinessResponse from(ConsultarVitrinePublica.VitrinePublica vitrine) {
        BusinessPublicProfile perfil = vitrine.perfil();
        List<PublicServiceResponse> services = vitrine.catalogo().itens().stream()
                .map(PublicServiceResponse::from)
                .toList();

        return new PublicBusinessResponse(
                perfil.getSlug().getValue(),
                perfil.getNomePublico(),
                perfil.getDescricaoCurta(),
                perfil.getTelefonePublico(),
                perfil.getEnderecoPublico(),
                !vitrine.catalogo().naoConfigurado(),
                services);
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getPublicPhone() {
        return publicPhone;
    }

    public String getPublicAddress() {
        return publicAddress;
    }

    public boolean isCatalogConfigured() {
        return catalogConfigured;
    }

    public List<PublicServiceResponse> getServices() {
        return services;
    }
}
