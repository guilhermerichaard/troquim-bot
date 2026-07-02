package com.troquim_bot.controller.dto;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessStatus;
import com.troquim_bot.business.DiaSemana;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DTO para resposta de Business.
 * Usado apenas na camada de apresentação (REST).
 */
public class BusinessResponse {

    private String id;
    private String nome;
    private String telefone;
    private String endereco;
    private BusinessHoursResponse horarioFuncionamento;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public BusinessResponse() {
    }

    public BusinessResponse(String id, String nome, String telefone, String endereco,
                           BusinessHoursResponse horarioFuncionamento, String status,
                           LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
        this.horarioFuncionamento = horarioFuncionamento;
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
            BusinessHoursResponse.from(business.getHorarioFuncionamento()),
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

    public BusinessHoursResponse getHorarioFuncionamento() {
        return horarioFuncionamento;
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

    /**
     * DTO interno para horário de funcionamento.
     */
    public static class BusinessHoursResponse {
        private LocalTime abertura;
        private LocalTime fechamento;
        private Set<String> diasFuncionamento;

        public BusinessHoursResponse() {
        }

        public BusinessHoursResponse(LocalTime abertura, LocalTime fechamento, Set<String> diasFuncionamento) {
            this.abertura = abertura;
            this.fechamento = fechamento;
            this.diasFuncionamento = diasFuncionamento;
        }

        public static BusinessHoursResponse from(BusinessHours hours) {
            if (hours == null) {
                return null;
            }

            Set<String> dias = hours.getDiasFuncionamento().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

            return new BusinessHoursResponse(
                hours.getAbertura(),
                hours.getFechamento(),
                dias
            );
        }

        public LocalTime getAbertura() {
            return abertura;
        }

        public LocalTime getFechamento() {
            return fechamento;
        }

        public Set<String> getDiasFuncionamento() {
            return diasFuncionamento;
        }
    }
}