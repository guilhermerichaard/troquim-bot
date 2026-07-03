package com.troquim_bot.repository;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;

import java.util.List;

/**
 * Repository abstraction para persistência de Availability.
 * 
 * Esta é uma interface pura sem dependência de frameworks.
 * A implementação concreta será definida na camada de infraestrutura.
 */
public interface AvailabilityRepository {

    /**
     * Salva um Availability (cria ou atualiza).
     */
    Availability save(Availability availability);

    /**
     * Busca um Availability por ID.
     * 
     * @return Availability se encontrado, null caso contrário
     */
    Availability findById(AvailabilityId id);

    /**
     * Verifica se existe um Availability com o ID informado.
     */
    boolean exists(AvailabilityId id);

    /**
     * Busca todos os Availabilities.
     */
    List<Availability> findAll();

    /**
     * Busca todos os Availabilities de um profissional.
     */
    List<Availability> findByProfessionalId(ProfessionalId professionalId);

    /**
     * Busca todos os Availabilities de um profissional em um dia específico.
     */
    List<Availability> findByProfessionalIdAndDayOfWeek(ProfessionalId professionalId, DiaSemana dayOfWeek);

    /**
     * Remove um Availability por ID.
     */
    void delete(AvailabilityId id);
}