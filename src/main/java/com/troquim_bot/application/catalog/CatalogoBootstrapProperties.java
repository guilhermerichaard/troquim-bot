package com.troquim_bot.application.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuração do bootstrap opcional do catálogo.
 *
 * Existe para que NENHUM dado de cliente (nome do salão, da profissional, telefone,
 * serviços) precise viver em código ou em migration versionada.
 */
@ConfigurationProperties(prefix = "troquim.bootstrap.catalogo")
public class CatalogoBootstrapProperties {

    /** Desligado por padrão. Ligar apenas para o primeiro provisionamento. */
    private boolean enabled = false;

    /** UUID do negócio alvo. Vazio = usa o tenant corrente. */
    private String businessId;

    private List<Servico> servicos = new ArrayList<>();

    private Profissional profissional;

    public static class Servico {
        private String nome;
        private int duracaoMinutos;

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public int getDuracaoMinutos() {
            return duracaoMinutos;
        }

        public void setDuracaoMinutos(int duracaoMinutos) {
            this.duracaoMinutos = duracaoMinutos;
        }
    }

    public static class Profissional {
        private String nome;
        private String telefone;

        /** Serviços que este profissional atende, por nome. Vazio = todos os provisionados. */
        private List<String> servicos = new ArrayList<>();

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getTelefone() {
            return telefone;
        }

        public void setTelefone(String telefone) {
            this.telefone = telefone;
        }

        public List<String> getServicos() {
            return servicos;
        }

        public void setServicos(List<String> servicos) {
            this.servicos = servicos;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public List<Servico> getServicos() {
        return servicos;
    }

    public void setServicos(List<Servico> servicos) {
        this.servicos = servicos;
    }

    public Profissional getProfissional() {
        return profissional;
    }

    public void setProfissional(Profissional profissional) {
        this.profissional = profissional;
    }
}
