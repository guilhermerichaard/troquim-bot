package com.troquim_bot.application.professional;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.service.ServiceId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Application Service de administração de profissionais.
 *
 * ESCOPO DE TENANT: toda operação é resolvida contra o negócio corrente, obtido do
 * {@link TenantProvider} na fronteira da Application e repassado EXPLICITAMENTE ao
 * repositório.
 *
 * O construtor sem argumentos foi REMOVIDO: ele criava um repositório em memória por
 * conta própria, fazendo a aplicação rodar com dados voláteis sem sinal nenhum.
 *
 * MÚLTIPLOS PROFISSIONAIS são suportados desde já. Um negócio com um único profissional
 * é caso particular, não premissa: não existe aqui id fixo, flag de "profissional único"
 * nem regra que pressuponha cardinalidade.
 *
 * VÍNCULO COM SERVIÇOS: {@code servicosHabilitados} (por {@link ServiceId}) é a associação
 * oficial. {@code especialidades} é texto livre descritivo e não decide quem atende o quê.
 */
@org.springframework.stereotype.Service
public class ProfessionalApplicationService {

    private final ProfessionalRepository professionalRepository;
    private final TenantProvider tenantProvider;

    public ProfessionalApplicationService(ProfessionalRepository professionalRepository,
                                          TenantProvider tenantProvider) {
        if (professionalRepository == null) {
            throw new IllegalArgumentException("ProfessionalRepository é obrigatório");
        }
        if (tenantProvider == null) {
            throw new IllegalArgumentException("TenantProvider é obrigatório");
        }
        this.professionalRepository = professionalRepository;
        this.tenantProvider = tenantProvider;
    }

    /**
     * Cria um profissional no negócio corrente.
     *
     * @param servicosHabilitados vínculo oficial com o catálogo; pode vir vazio, e nesse
     *                            caso o profissional simplesmente não é ofertado até ser
     *                            habilitado — nunca é tratado como "atende tudo".
     * @param especialidades      texto livre descritivo, opcional.
     */
    public Professional criarProfissional(String nome, Set<ServiceId> servicosHabilitados,
                                          Set<String> especialidades, String telefone) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do profissional é obrigatório");
        }
        if (telefone == null || telefone.trim().isEmpty()) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }

        Professional professional = new Professional(
                ProfessionalId.generate(),
                tenantAtual(),
                nome.trim(),
                servicosHabilitados,
                especialidades,
                telefone.trim());

        return professionalRepository.salvar(professional);
    }

    public Optional<Professional> buscarPorId(ProfessionalId id) {
        if (id == null) {
            return Optional.empty();
        }
        return professionalRepository.buscarPorId(tenantAtual(), id);
    }

    public List<Professional> buscarTodos() {
        return professionalRepository.listarTodos(tenantAtual());
    }

    public List<Professional> listarAtivos() {
        return professionalRepository.listarAtivos(tenantAtual());
    }

    /** Profissionais habilitados para um serviço, pelo vínculo explícito por ID. */
    public List<Professional> listarAtivosPorServico(ServiceId servico) {
        if (servico == null) {
            throw new IllegalArgumentException("ServiceId é obrigatório");
        }
        return professionalRepository.listarAtivosPorServico(tenantAtual(), servico);
    }

    public Professional atualizarProfissional(ProfessionalId id, String nome,
                                              Set<String> especialidades, String telefone) {
        Professional professional = exigirProfissional(id);
        if (nome != null && !nome.trim().isEmpty()) {
            professional.atualizarNome(nome);
        }
        if (especialidades != null && !especialidades.isEmpty()) {
            professional.atualizarEspecialidades(especialidades);
        }
        if (telefone != null && !telefone.trim().isEmpty()) {
            professional.atualizarTelefone(telefone);
        }
        return professionalRepository.salvar(professional);
    }

    /** Habilita o profissional para um serviço do MESMO negócio. */
    public Professional habilitarPara(ProfessionalId id, ServiceId servico) {
        Professional professional = exigirProfissional(id);
        professional.habilitarPara(servico);
        return professionalRepository.salvar(professional);
    }

    public Professional desabilitarPara(ProfessionalId id, ServiceId servico) {
        Professional professional = exigirProfissional(id);
        professional.desabilitarPara(servico);
        return professionalRepository.salvar(professional);
    }

    public Professional inativarProfissional(ProfessionalId id) {
        Professional professional = exigirProfissional(id);
        professional.desativar();
        return professionalRepository.salvar(professional);
    }

    public Professional ativarProfissional(ProfessionalId id) {
        Professional professional = exigirProfissional(id);
        professional.ativar();
        return professionalRepository.salvar(professional);
    }

    public boolean existe(ProfessionalId id) {
        return id != null && professionalRepository.buscarPorId(tenantAtual(), id).isPresent();
    }

    public void deletarProfissional(ProfessionalId id) {
        if (id == null) {
            throw new IllegalArgumentException("ID do profissional é obrigatório");
        }
        professionalRepository.remover(tenantAtual(), id);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private BusinessId tenantAtual() {
        BusinessId businessId = tenantProvider.currentBusinessId();
        if (businessId == null) {
            throw new IllegalStateException("Negócio corrente não resolvido; operação recusada");
        }
        return businessId;
    }

    private Professional exigirProfissional(ProfessionalId id) {
        return professionalRepository.buscarPorId(tenantAtual(), id)
                .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado"));
    }
}
