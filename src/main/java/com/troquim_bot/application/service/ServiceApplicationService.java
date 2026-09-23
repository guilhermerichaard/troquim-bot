package com.troquim_bot.application.service;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.common.valueobject.Money;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;

import java.util.List;
import java.util.Optional;

/**
 * Application Service de administração do catálogo de serviços.
 *
 * ESCOPO DE TENANT: toda operação é resolvida contra o negócio corrente, obtido do
 * {@link TenantProvider} na fronteira da Application e repassado EXPLICITAMENTE ao
 * repositório. A Infrastructure nunca deduz tenant.
 *
 * O construtor sem argumentos foi REMOVIDO de propósito: ele instanciava um repositório
 * em memória por conta própria, o que fazia a aplicação rodar com catálogo volátil sem
 * ninguém perceber. Dependência obrigatória é o que impede esse fallback silencioso.
 *
 * PREÇO é opcional e está fora do MVP: {@code null} significa "não precificado". Nada
 * aqui inventa valor nem converte ausência de preço em zero.
 */
@org.springframework.stereotype.Service
public class ServiceApplicationService {

    private final ServiceRepository serviceRepository;
    private final TenantProvider tenantProvider;

    public ServiceApplicationService(ServiceRepository serviceRepository,
                                     TenantProvider tenantProvider) {
        if (serviceRepository == null) {
            throw new IllegalArgumentException("ServiceRepository é obrigatório");
        }
        if (tenantProvider == null) {
            throw new IllegalArgumentException("TenantProvider é obrigatório");
        }
        this.serviceRepository = serviceRepository;
        this.tenantProvider = tenantProvider;
    }

    /**
     * Cria um serviço no negócio corrente. {@code preco} pode ser nulo (sem preço).
     */
    public com.troquim_bot.service.Service criarServico(String nome, String descricao,
                                                        int duracaoMinutos, Money preco) {
        return criarServico(tenantAtual(), nome, descricao, duracaoMinutos, preco);
    }

    /**
     * Criação tenant-explicit para superfícies cuja identidade já foi resolvida por sessão.
     */
    public com.troquim_bot.service.Service criarServico(BusinessId businessId,
                                                        String nome, String descricao,
                                                        int duracaoMinutos, Money preco) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do serviço é obrigatório");
        }

        com.troquim_bot.service.Service service = preco == null
                ? com.troquim_bot.service.Service.novoSemPreco(
                        ServiceId.generate(), businessId, nome.trim(),
                        descricao != null ? descricao.trim() : null,
                        ServiceDuration.ofMinutes(duracaoMinutos))
                : com.troquim_bot.service.Service.novoComPreco(
                        ServiceId.generate(), businessId, nome.trim(),
                        descricao != null ? descricao.trim() : null,
                        ServiceDuration.ofMinutes(duracaoMinutos), preco);

        return serviceRepository.salvar(service);
    }

    public Optional<com.troquim_bot.service.Service> buscarPorId(ServiceId id) {
        return buscarPorId(tenantAtual(), id);
    }

    /**
     * Leitura tenant-explicit para superfícies autenticadas por sessão, como o console
     * do owner. Evita depender do TenantProvider piloto quando o businessId já foi
     * resolvido de forma autoritativa pela sessão.
     */
    public Optional<com.troquim_bot.service.Service> buscarPorId(BusinessId businessId, ServiceId id) {
        if (businessId == null || id == null) {
            return Optional.empty();
        }
        return serviceRepository.buscarPorId(businessId, id);
    }

    public List<com.troquim_bot.service.Service> listarTodos() {
        return listarTodos(tenantAtual());
    }

    public List<com.troquim_bot.service.Service> listarTodos(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return serviceRepository.listarTodos(businessId);
    }

    /** Filtragem por status é do repositório, não uma segunda regra aqui. */
    public List<com.troquim_bot.service.Service> listarAtivos() {
        return listarAtivos(tenantAtual());
    }

    public List<com.troquim_bot.service.Service> listarAtivos(BusinessId businessId) {
        if (businessId == null) {
            return List.of();
        }
        return serviceRepository.listarAtivos(businessId);
    }

    public com.troquim_bot.service.Service atualizarNome(ServiceId id, String novoNome) {
        return atualizarNome(tenantAtual(), id, novoNome);
    }

    public com.troquim_bot.service.Service atualizarNome(BusinessId businessId, ServiceId id, String novoNome) {
        com.troquim_bot.service.Service service = exigirServico(businessId, id);
        service.atualizarNome(novoNome);
        return serviceRepository.salvar(service);
    }

    public com.troquim_bot.service.Service atualizarDescricao(ServiceId id, String novaDescricao) {
        return atualizarDescricao(tenantAtual(), id, novaDescricao);
    }

    public com.troquim_bot.service.Service atualizarDescricao(BusinessId businessId, ServiceId id, String novaDescricao) {
        com.troquim_bot.service.Service service = exigirServico(businessId, id);
        service.atualizarDescricao(novaDescricao);
        return serviceRepository.salvar(service);
    }

    public com.troquim_bot.service.Service atualizarDuracao(ServiceId id, int duracaoMinutos) {
        return atualizarDuracao(tenantAtual(), id, duracaoMinutos);
    }

    public com.troquim_bot.service.Service atualizarDuracao(BusinessId businessId, ServiceId id, int duracaoMinutos) {
        com.troquim_bot.service.Service service = exigirServico(businessId, id);
        service.atualizarDuracao(ServiceDuration.ofMinutes(duracaoMinutos));
        return serviceRepository.salvar(service);
    }

    /** Define preço. Para remover, use {@link #removerPreco(ServiceId)}. */
    public com.troquim_bot.service.Service atualizarPreco(ServiceId id, Money novoPreco) {
        return atualizarPreco(tenantAtual(), id, novoPreco);
    }

    public com.troquim_bot.service.Service atualizarPreco(BusinessId businessId, ServiceId id, Money novoPreco) {
        com.troquim_bot.service.Service service = exigirServico(businessId, id);
        service.definirPreco(novoPreco);
        return serviceRepository.salvar(service);
    }

    /** Volta o serviço ao estado "não precificado". */
    public com.troquim_bot.service.Service removerPreco(ServiceId id) {
        return removerPreco(tenantAtual(), id);
    }

    public com.troquim_bot.service.Service removerPreco(BusinessId businessId, ServiceId id) {
        com.troquim_bot.service.Service service = exigirServico(businessId, id);
        service.removerPreco();
        return serviceRepository.salvar(service);
    }

    public com.troquim_bot.service.Service inativarServico(ServiceId id) {
        return inativarServico(tenantAtual(), id);
    }

    public com.troquim_bot.service.Service inativarServico(BusinessId businessId, ServiceId id) {
        com.troquim_bot.service.Service service = exigirServico(businessId, id);
        service.desativar();
        return serviceRepository.salvar(service);
    }

    public com.troquim_bot.service.Service ativarServico(ServiceId id) {
        return ativarServico(tenantAtual(), id);
    }

    public com.troquim_bot.service.Service ativarServico(BusinessId businessId, ServiceId id) {
        com.troquim_bot.service.Service service = exigirServico(businessId, id);
        service.ativar();
        return serviceRepository.salvar(service);
    }

    public boolean existe(ServiceId id) {
        return existe(tenantAtual(), id);
    }

    public boolean existe(BusinessId businessId, ServiceId id) {
        return businessId != null && id != null
                && serviceRepository.buscarPorId(businessId, id).isPresent();
    }

    public void deletarServico(ServiceId id) {
        deletarServico(tenantAtual(), id);
    }

    public void deletarServico(BusinessId businessId, ServiceId id) {
        if (businessId == null || id == null) {
            throw new IllegalArgumentException("BusinessId e ID do serviço são obrigatórios");
        }
        serviceRepository.remover(businessId, id);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private BusinessId tenantAtual() {
        BusinessId businessId = tenantProvider.currentBusinessId();
        if (businessId == null) {
            throw new IllegalStateException("Negócio corrente não resolvido; operação recusada");
        }
        return businessId;
    }

    /**
     * Serviço de OUTRO negócio é indistinguível de inexistente — o repositório já filtra
     * por tenant, então esta mensagem não revela dado alheio.
     */
    private com.troquim_bot.service.Service exigirServico(ServiceId id) {
        return exigirServico(tenantAtual(), id);
    }

    private com.troquim_bot.service.Service exigirServico(BusinessId businessId, ServiceId id) {
        if (businessId == null || id == null) {
            throw new IllegalArgumentException("BusinessId e ServiceId são obrigatórios");
        }
        return serviceRepository.buscarPorId(businessId, id)
                .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado"));
    }
}
