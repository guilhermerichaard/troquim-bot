package com.troquim_bot.repository;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import java.util.List;
import java.util.Optional;

/**
 * Port de persistência de profissionais.
 *
 * TENANT OBRIGATÓRIO EM TODA CONSULTA, pelo mesmo motivo do catálogo de serviços:
 * assinatura sem {@link BusinessId} vazaria dado entre negócios silenciosamente.
 *
 * A consulta por serviço usa o vínculo OFICIAL {@code professional_services} (por
 * {@link ServiceId}), nunca o texto livre de `especialidades`.
 *
 * Interface pura, sem dependência de frameworks. A implementação vive na Infrastructure.
 */
public interface ProfessionalRepository {

    Professional salvar(Professional professional);

    /** Vazio quando o id não existe OU pertence a outro negócio — indistinguíveis de fora. */
    Optional<Professional> buscarPorId(BusinessId businessId, ProfessionalId id);

    /** Somente profissionais ATIVOS do negócio. */
    List<Professional> listarAtivos(BusinessId businessId);

    /**
     * Profissionais ATIVOS habilitados para um serviço, pelo vínculo explícito por ID.
     * Lista vazia é resposta legítima: significa "ninguém atende este serviço hoje", e
     * quem chama deve tratar isso com mensagem clara — nunca com um profissional default.
     */
    List<Professional> listarAtivosPorServico(BusinessId businessId, ServiceId servico);

    /** Inclui inativos; para administração, não para oferta ao cliente. */
    List<Professional> listarTodos(BusinessId businessId);

    void remover(BusinessId businessId, ProfessionalId id);
}
