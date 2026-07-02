package com.troquim_bot.application.service;

import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.service.ServiceStatus;
import com.troquim_bot.common.valueobject.Money;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.repository.InMemoryServiceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application Service para gerenciar Services.
 * 
 * Responsabilidades:
 * - Criar serviços
 * - Listar serviços
 * - Buscar serviços
 * - Atualizar serviços
 * - Inativar serviços
 */
@org.springframework.stereotype.Service
public class ServiceApplicationService {

    private final ServiceRepository serviceRepository;

    /**
     * Construtor para MVP com repositório em memória.
     */
    public ServiceApplicationService() {
        this(new InMemoryServiceRepository());
    }

    /**
     * Construtor com injeção de dependência (para testes ou futura implementação JPA).
     */
    public ServiceApplicationService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    /**
     * Cria um novo serviço.
     * 
     * @param nome Nome do serviço
     * @param descricao Descrição do serviço (opcional)
     * @param duracaoMinutos Duração em minutos
     * @param preco Preço do serviço
     * @return Service criado com status ATIVO
     */
    public com.troquim_bot.service.Service criarServico(String nome, String descricao, int duracaoMinutos, Money preco) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do serviço é obrigatório");
        }
        if (preco == null) {
            throw new IllegalArgumentException("Preço é obrigatório");
        }

        ServiceId id = ServiceId.generate();
        ServiceDuration duracao = ServiceDuration.ofMinutes(duracaoMinutos);

        com.troquim_bot.service.Service service = new com.troquim_bot.service.Service(
            id,
            nome.trim(),
            descricao != null ? descricao.trim() : null,
            duracao,
            preco
        );

        return serviceRepository.save(service);
    }

    /**
     * Busca serviço por ID.
     * 
     * @param id ID do serviço
     * @return Optional com o Service se encontrado
     */
    public Optional<com.troquim_bot.service.Service> buscarPorId(ServiceId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(serviceRepository.findById(id));
    }

    /**
     * Lista todos os serviços.
     * 
     * @return Lista de todos os services
     */
    public List<com.troquim_bot.service.Service> listarTodos() {
        return serviceRepository.findAll();
    }

    /**
     * Lista apenas serviços ativos.
     * 
     * @return Lista de services ativos
     */
    public List<com.troquim_bot.service.Service> listarAtivos() {
        return serviceRepository.findAll().stream()
            .filter(com.troquim_bot.service.Service::isAtivo)
            .toList();
    }

    /**
     * Atualiza o nome do serviço.
     * 
     * @param id ID do serviço
     * @param novoNome Novo nome
     * @return Service atualizado
     */
    public com.troquim_bot.service.Service atualizarNome(ServiceId id, String novoNome) {
        com.troquim_bot.service.Service service = getServiceOrThrow(id);
        service.atualizarNome(novoNome);
        return serviceRepository.save(service);
    }

    /**
     * Atualiza a descrição do serviço.
     * 
     * @param id ID do serviço
     * @param novaDescricao Nova descrição
     * @return Service atualizado
     */
    public com.troquim_bot.service.Service atualizarDescricao(ServiceId id, String novaDescricao) {
        com.troquim_bot.service.Service service = getServiceOrThrow(id);
        service.atualizarDescricao(novaDescricao);
        return serviceRepository.save(service);
    }

    /**
     * Atualiza a duração do serviço.
     * 
     * @param id ID do serviço
     * @param duracaoMinutos Nova duração em minutos
     * @return Service atualizado
     */
    public com.troquim_bot.service.Service atualizarDuracao(ServiceId id, int duracaoMinutos) {
        com.troquim_bot.service.Service service = getServiceOrThrow(id);
        ServiceDuration novaDuracao = ServiceDuration.ofMinutes(duracaoMinutos);
        service.atualizarDuracao(novaDuracao);
        return serviceRepository.save(service);
    }

    /**
     * Atualiza o preço do serviço.
     * 
     * @param id ID do serviço
     * @param novoPreco Novo preço
     * @return Service atualizado
     */
    public com.troquim_bot.service.Service atualizarPreco(ServiceId id, Money novoPreco) {
        com.troquim_bot.service.Service service = getServiceOrThrow(id);
        service.atualizarPreco(novoPreco);
        return serviceRepository.save(service);
    }

    /**
     * Inativa um serviço.
     * 
     * @param id ID do serviço
     * @return Service inativado
     */
    public com.troquim_bot.service.Service inativarServico(ServiceId id) {
        com.troquim_bot.service.Service service = getServiceOrThrow(id);
        service.desativar();
        return serviceRepository.save(service);
    }

    /**
     * Ativa um serviço.
     * 
     * @param id ID do serviço
     * @return Service ativado
     */
    public com.troquim_bot.service.Service ativarServico(ServiceId id) {
        com.troquim_bot.service.Service service = getServiceOrThrow(id);
        service.ativar();
        return serviceRepository.save(service);
    }

    /**
     * Verifica se um serviço existe.
     * 
     * @param id ID do serviço
     * @return true se existe
     */
    public boolean existe(ServiceId id) {
        if (id == null) {
            return false;
        }
        return serviceRepository.exists(id);
    }

    /**
     * Remove um serviço.
     * 
     * @param id ID do serviço
     */
    public void deletarServico(ServiceId id) {
        if (id == null) {
            throw new IllegalArgumentException("ID do serviço é obrigatório");
        }
        serviceRepository.delete(id);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private com.troquim_bot.service.Service getServiceOrThrow(ServiceId id) {
        com.troquim_bot.service.Service service = serviceRepository.findById(id);
        if (service == null) {
            throw new IllegalArgumentException("Serviço não encontrado");
        }
        return service;
    }
}