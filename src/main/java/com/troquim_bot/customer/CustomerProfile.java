package com.troquim_bot.customer;

import java.time.LocalDateTime;

/**
 * DTO de compatibilidade para o perfil do cliente.
 * 
 * Não armazena estado próprio — é construído a partir de Customer,
 * que é a única fonte da verdade.
 * 
 * Mantido para não quebrar o contrato público de CustomerProfileService
 * usado por ConversationService e ConversationContextResolver.
 */
public class CustomerProfile {

    private final String numero;
    private String nome;
    private String apelido;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private int totalAtendimentos;
    private LocalDateTime ultimoAtendimento;

    public CustomerProfile(String numero) {
        LocalDateTime agora = LocalDateTime.now();
        this.numero = numero;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    /**
     * Constrói um CustomerProfile a partir de um Customer.
     */
    public static CustomerProfile fromCustomer(Customer customer, String numero) {
        CustomerProfile profile = new CustomerProfile(numero);
        if (customer.getName() != null) {
            // Se o sobrenome for "Sr" (fallback para nomes sem sobrenome),
            // retorna apenas o primeiro nome para compatibilidade
            String sobrenome = customer.getName().getLastName();
            if ("Sr".equals(sobrenome)) {
                profile.nome = customer.getName().getFirstName();
            } else {
                profile.nome = customer.getName().getFullName();
            }
        }
        profile.apelido = customer.getApelido();
        profile.totalAtendimentos = customer.getTotalAtendimentos();
        profile.ultimoAtendimento = customer.getUltimoAtendimento();
        return profile;
    }

    public String getNumero() {
        return numero;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
        tocar();
    }

    public String getApelido() {
        return apelido;
    }

    public void setApelido(String apelido) {
        this.apelido = apelido;
        tocar();
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public int getTotalAtendimentos() {
        return totalAtendimentos;
    }

    public LocalDateTime getUltimoAtendimento() {
        return ultimoAtendimento;
    }

    public void registrarNovoAtendimento() {
        this.totalAtendimentos++;
        this.ultimoAtendimento = LocalDateTime.now();
        tocar();
    }

    public void atualizarUltimoAtendimento() {
        this.ultimoAtendimento = LocalDateTime.now();
        tocar();
    }

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}