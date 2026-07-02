package com.troquim_bot.controller.dto;

import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.service.ServiceStatus;
import com.troquim_bot.common.valueobject.Money;

import java.time.LocalDateTime;

/**
 * DTO para resposta de Service.
 * Usado apenas na camada de apresentação (REST).
 */
public class ServiceResponse {

    private String id;
    private String nome;
    private String descricao;
    private ServiceDurationResponse duracao;
    private PriceResponse preco;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public ServiceResponse() {
    }

    public ServiceResponse(String id, String nome, String descricao, ServiceDurationResponse duracao,
                          PriceResponse preco, String status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.duracao = duracao;
        this.preco = preco;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public static ServiceResponse from(Service service) {
        if (service == null) {
            return null;
        }

        return new ServiceResponse(
            service.getId().getValue().toString(),
            service.getNome(),
            service.getDescricao(),
            ServiceDurationResponse.from(service.getDuracao()),
            PriceResponse.from(service.getPreco()),
            service.getStatus().name(),
            service.getCriadoEm(),
            service.getAtualizadoEm()
        );
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public ServiceDurationResponse getDuracao() {
        return duracao;
    }

    public PriceResponse getPreco() {
        return preco;
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
     * DTO interno para duração do serviço.
     */
    public static class ServiceDurationResponse {
        private int minutes;
        private String formatted;

        public ServiceDurationResponse() {
        }

        public ServiceDurationResponse(int minutes, String formatted) {
            this.minutes = minutes;
            this.formatted = formatted;
        }

        public static ServiceDurationResponse from(ServiceDuration duration) {
            if (duration == null) {
                return null;
            }

            return new ServiceDurationResponse(
                duration.getMinutes(),
                duration.getFormatted()
            );
        }

        public int getMinutes() {
            return minutes;
        }

        public String getFormatted() {
            return formatted;
        }
    }

    /**
     * DTO interno para preço do serviço.
     */
    public static class PriceResponse {
        private double amount;
        private String currency;

        public PriceResponse() {
        }

        public PriceResponse(double amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        public static PriceResponse from(Money money) {
            if (money == null) {
                return null;
            }

            return new PriceResponse(
                money.getAmount().doubleValue(),
                money.getCurrency()
            );
        }

        public double getAmount() {
            return amount;
        }

        public String getCurrency() {
            return currency;
        }
    }
}