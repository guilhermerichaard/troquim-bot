package com.troquim_bot.controller.dto;

import com.troquim_bot.application.catalog.ConsultarCatalogo;

/**
 * Profissional exposto na vitrine pública. Só id (real, do catálogo) e nome — nenhum outro
 * dado administrativo do profissional.
 */
public class PublicProfessionalResponse {

    private String id;
    private String name;

    public PublicProfessionalResponse() {
    }

    public PublicProfessionalResponse(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public static PublicProfessionalResponse from(ConsultarCatalogo.ProfissionalDoCatalogo profissional) {
        return new PublicProfessionalResponse(profissional.id().getValue().toString(), profissional.nome());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
