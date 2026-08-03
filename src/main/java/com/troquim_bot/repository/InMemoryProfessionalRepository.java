package com.troquim_bot.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.professional.ProfessionalStatus;
import com.troquim_bot.service.ServiceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementação em memória do {@link ProfessionalRepository}.
 *
 * Segue a mesma disciplina do repositório de serviços: o isolamento por negócio é aplicado
 * aqui também, para que um teste não passe verde por usar um duplo permissivo demais.
 *
 * A habilitação por serviço delega ao agregado ({@code Professional.atende}), em vez de
 * reimplementar a regra na consulta — evita duas fontes de verdade para "quem atende o quê".
 */
@Repository
@Profile({"test", "dev-inmemory"})
public class InMemoryProfessionalRepository implements ProfessionalRepository {

    private final ConcurrentMap<ProfessionalId, Professional> professionals = new ConcurrentHashMap<>();

    @Override
    public Professional salvar(Professional professional) {
        if (professional == null) {
            throw new IllegalArgumentException("Professional não pode ser nulo");
        }
        professionals.put(professional.getId(), professional);
        return professional;
    }

    @Override
    public Optional<Professional> buscarPorId(BusinessId businessId, ProfessionalId id) {
        if (businessId == null || id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(professionals.get(id))
                .filter(professional -> professional.pertenceAo(businessId));
    }

    @Override
    public List<Professional> listarAtivos(BusinessId businessId) {
        List<Professional> encontrados = new ArrayList<>();
        if (businessId == null) {
            return encontrados;
        }
        for (Professional professional : professionals.values()) {
            if (professional.pertenceAo(businessId)
                    && professional.getStatus() == ProfessionalStatus.ATIVO) {
                encontrados.add(professional);
            }
        }
        return encontrados;
    }

    @Override
    public List<Professional> listarAtivosPorServico(BusinessId businessId, ServiceId servico) {
        List<Professional> encontrados = new ArrayList<>();
        if (businessId == null || servico == null) {
            return encontrados;
        }
        for (Professional professional : professionals.values()) {
            // A decisão de "atende" é do agregado, não desta consulta.
            if (professional.pertenceAo(businessId) && professional.atende(servico)) {
                encontrados.add(professional);
            }
        }
        return encontrados;
    }

    @Override
    public List<Professional> listarTodos(BusinessId businessId) {
        List<Professional> encontrados = new ArrayList<>();
        if (businessId == null) {
            return encontrados;
        }
        for (Professional professional : professionals.values()) {
            if (professional.pertenceAo(businessId)) {
                encontrados.add(professional);
            }
        }
        return encontrados;
    }

    @Override
    public void remover(BusinessId businessId, ProfessionalId id) {
        if (businessId == null || id == null) {
            return;
        }
        professionals.computeIfPresent(id,
                (chave, professional) -> professional.pertenceAo(businessId) ? null : professional);
    }
}
