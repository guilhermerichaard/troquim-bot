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
 *
 * TODA operação exige o {@link BusinessId} explicitamente. Não há resolução implícita de
 * "o negócio atual": quem chama precisa provar de qual tenant está falando, e é isso que
 * impede a administração de um negócio vazar para outro.
 */
@Service
public class BusinessApplicationService {

    private final BusinessRepository businessRepository;

    public BusinessApplicationService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    /**
     * Busca o Business por ID.
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
    public Business atualizarNome(BusinessId id, String novoNome) {
        Business business = getBusinessOrThrow(id);
        business.atualizarNome(novoNome);
        return businessRepository.save(business);
    }

    /**
     * Atualiza o telefone do Business.
     */
    public Business atualizarTelefone(BusinessId id, String novoTelefone) {
        Business business = getBusinessOrThrow(id);
        business.atualizarContato(novoTelefone, business.getEndereco());
        return businessRepository.save(business);
    }

    /**
     * Atualiza o endereço do Business.
     */
    public Business atualizarEndereco(BusinessId id, String novoEndereco) {
        Business business = getBusinessOrThrow(id);
        business.atualizarContato(business.getTelefone(), novoEndereco);
        return businessRepository.save(business);
    }

    /**
     * Ativa o Business (transição para ATIVO).
     */
    public Business ativarBusiness(BusinessId id) {
        Business business = getBusinessOrThrow(id);
        business.ativar();
        return businessRepository.save(business);
    }

    /**
     * Desativa o Business (transição para INATIVO).
     */
    public Business desativarBusiness(BusinessId id) {
        Business business = getBusinessOrThrow(id);
        business.desativar();
        return businessRepository.save(business);
    }

    /**
     * Verifica se existe um Business com o ID informado.
     */
    public boolean existeBusiness(BusinessId id) {
        return id != null && businessRepository.exists(id);
    }

    /**
     * Verifica se o Business com o ID informado está ativo.
     */
    public boolean isBusinessAtivo(BusinessId id) {
        return buscarPorId(id)
            .map(Business::isAtivo)
            .orElse(false);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private Business getBusinessOrThrow(BusinessId id) {
        return buscarPorId(id)
            .orElseThrow(() -> new IllegalStateException("Business não encontrado: " + id));
    }
}
