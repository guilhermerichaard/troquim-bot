package com.troquim_bot.application.business;

import org.springframework.stereotype.Service;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.repository.BusinessRepository;

import java.util.Optional;

/**
 * Application Service para gerenciar dados administrativos do Business (nome, contato,
 * status). Cadastro em si é responsabilidade de {@link CadastrarNegocio} — este serviço só
 * lê e atualiza um negócio que já existe.
 */
@Service
public class BusinessApplicationService {

    private final BusinessRepository businessRepository;

    public BusinessApplicationService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    /**
     * Busca o Business atual (admin legado de single-salão).
     *
     * @return Optional com o Business se existir
     */
    public Optional<Business> buscarBusinessAtual() {
        return businessRepository.findAll().stream().findFirst();
    }

    /**
     * Busca o Business por ID.
     *
     * @param id ID do Business
     * @return Optional com o Business se encontrado
     */
    public Optional<Business> buscarPorId(BusinessId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessRepository.findById(id));
    }

    /**
     * Atualiza o nome do Business.
     */
    public Business atualizarNome(String novoNome) {
        Business business = getBusinessOrThrow();
        business.atualizarNome(novoNome);
        return businessRepository.save(business);
    }

    /**
     * Atualiza o telefone do Business.
     */
    public Business atualizarTelefone(String novoTelefone) {
        Business business = getBusinessOrThrow();
        business.atualizarContato(novoTelefone, business.getEndereco());
        return businessRepository.save(business);
    }

    /**
     * Atualiza o endereço do Business.
     */
    public Business atualizarEndereco(String novoEndereco) {
        Business business = getBusinessOrThrow();
        business.atualizarContato(business.getTelefone(), novoEndereco);
        return businessRepository.save(business);
    }

    /**
     * Ativa o Business (transição para ATIVO).
     */
    public Business ativarBusiness() {
        Business business = getBusinessOrThrow();
        business.ativar();
        return businessRepository.save(business);
    }

    /**
     * Desativa o Business (transição para INATIVO).
     */
    public Business desativarBusiness() {
        Business business = getBusinessOrThrow();
        business.desativar();
        return businessRepository.save(business);
    }

    /**
     * Verifica se existe um Business configurado.
     */
    public boolean existeBusiness() {
        return businessRepository.findAll().stream().findFirst().isPresent();
    }

    /**
     * Verifica se o Business está ativo.
     */
    public boolean isBusinessAtivo() {
        return buscarBusinessAtual()
            .map(Business::isAtivo)
            .orElse(false);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private Business getBusinessOrThrow() {
        return buscarBusinessAtual()
            .orElseThrow(() -> new IllegalStateException("Nenhum Business configurado"));
    }
}
