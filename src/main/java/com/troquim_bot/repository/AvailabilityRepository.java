package com.troquim_bot.repository;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;

import java.util.List;
import java.util.Optional;

/**
 * Port de persistência da disponibilidade dos profissionais.
 *
 * TENANT OBRIGATÓRIO EM TODA OPERAÇÃO — inclusive na busca por id e na remoção.
 * Deliberadamente NÃO existem mais {@code findAll()}, {@code findById(AvailabilityId)} nem
 * {@code findByProfessionalId(ProfessionalId)}: assinaturas sem {@link BusinessId} deixam o
 * vazamento entre negócios acontecer sem que ninguém precise errar duas vezes. O tenant é
 * argumento explícito — nunca contexto implícito, ThreadLocal ou dedução da Infrastructure.
 *
 * Interface pura, sem dependência de frameworks. A implementação vive na Infrastructure.
 */
public interface AvailabilityRepository {

    Availability salvar(Availability availability);

    /**
     * Vazio quando o id não existe OU pertence a outro negócio — os dois casos são
     * indistinguíveis de fora, de propósito.
     */
    Optional<Availability> buscarPorId(BusinessId businessId, AvailabilityId id);

    boolean existe(BusinessId businessId, AvailabilityId id);

    /** Todas as disponibilidades do negócio, ativas e inativas. */
    List<Availability> listarPorNegocio(BusinessId businessId);

    List<Availability> listarPorProfissional(BusinessId businessId, ProfessionalId professionalId);

    /**
     * Disponibilidades ATIVAS do profissional naquele dia da semana — a consulta que a
     * geração de horários efetivamente usa.
     */
    List<Availability> listarAtivasPorProfissionalEDia(BusinessId businessId,
                                                        ProfessionalId professionalId,
                                                        DiaSemana dayOfWeek);

    void remover(BusinessId businessId, AvailabilityId id);
}
