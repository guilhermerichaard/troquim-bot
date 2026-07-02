package com.troquim_bot.repository;

import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;

import java.util.List;

/**
 * Repository abstraction para persistência de Professional.
 * 
 * Esta é uma interface pura sem dependência de frameworks.
 * A implementação concreta será definida na camada de infraestrutura.
 */
public interface ProfessionalRepository {

    /**
     * Salva um Professional (cria ou atualiza).
     */
    Professional save(Professional professional);

    /**
     * Busca um Professional por ID.
     * 
     * @return Professional se encontrado, null caso contrário
     */
    Professional findById(ProfessionalId id);

    /**
     * Verifica se existe um Professional com o ID informado.
     */
    boolean exists(ProfessionalId id);

    /**
     * Busca todos os Professionals.
     */
    List<Professional> findAll();

    /**
     * Remove um Professional por ID.
     */
    void delete(ProfessionalId id);
}