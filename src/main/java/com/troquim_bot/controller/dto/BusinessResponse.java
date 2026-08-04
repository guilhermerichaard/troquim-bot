package com.troquim_bot.controller.dto;

import com.troquim_bot.business.Business;

import java.time.LocalDateTime;

/**
 * DTO para resposta de Business.
 * Usado apenas na camada de apresentação (REST).
 */
public class BusinessResponse {

    private String id;
    private String nome;
    private String telefone;
    private String endereco;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public BusinessResponse() {
    }

    public BusinessResponse(String id, String nome, String telefone, String endereco,
                           String status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static BusinessResponse from(Business business) {
        if (business == null) {
            return null;
        }

        return new BusinessResponse(
            business.getId().getValue().toString(),
            business.getNome(),
            business.getTelefone(),
            business.getEndereco(),
            business.getStatus().name(),
            business.getCriadoEm(),
            business.getAtualizadoEm()
        );
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEndereco() {
        return endereco;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
