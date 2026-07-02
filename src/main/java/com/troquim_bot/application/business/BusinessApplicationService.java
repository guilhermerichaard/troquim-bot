package com.troquim_bot.application.business;

import org.springframework.stereotype.Service;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.BusinessStatus;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.common.valueobject.Address;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.repository.BusinessRepository;
import com.troquim_bot.repository.InMemoryBusinessRepository;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Application Service para gerenciar o Business.
 * MVP assume apenas 1 salão (1 Business).
 * 
 * Responsabilidades:
 * - Criar/configurar Business padrão do MVP
 * - Buscar Business atual
 * - Atualizar dados básicos do Business
 */
@Service
public class BusinessApplicationService {

    private final BusinessRepository businessRepository;

    /**
     * Construtor para MVP com repositório em memória.
     */
    public BusinessApplicationService() {
        this(new InMemoryBusinessRepository());
    }

    /**
     * Construtor com injeção de dependência (para testes ou futura implementação JPA).
     */
    public BusinessApplicationService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    /**
     * Cria um Business padrão para o MVP.
     * 
     * @param nome Nome do negócio
     * @param telefone Telefone de contato
     * @param endereco Endereço completo
     * @return Business criado com status TRIAL
     */
    public Business criarBusinessPadrao(String nome, String telefone, String endereco) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do negócio é obrigatório");
        }

        BusinessId id = BusinessId.generate();
        
        Set<DiaSemana> diasFuncionamento = new HashSet<>();
        diasFuncionamento.add(DiaSemana.SEGUNDA);
        diasFuncionamento.add(DiaSemana.TERCA);
        diasFuncionamento.add(DiaSemana.QUARTA);
        diasFuncionamento.add(DiaSemana.QUINTA);
        diasFuncionamento.add(DiaSemana.SEXTA);
        
        BusinessHours horarioFuncionamento = new BusinessHours(
            LocalTime.of(9, 0),
            LocalTime.of(19, 0),
            diasFuncionamento
        );

        // MVP: usa valores padrão se não fornecidos
        String telefoneFinal = (telefone != null && !telefone.trim().isEmpty()) 
            ? telefone.trim() 
            : "(11) 99999-9999";
        
        String enderecoFinal = (endereco != null && !endereco.trim().isEmpty()) 
            ? endereco.trim() 
            : "São Paulo - SP";

        Business business = new Business(
            id,
            nome.trim(),
            telefoneFinal,
            enderecoFinal,
            horarioFuncionamento
        );

        return businessRepository.save(business);
    }

    /**
     * Busca o Business atual (MVP assume apenas 1).
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
     * 
     * @param novoNome Novo nome
     * @return Business atualizado
     */
    public Business atualizarNome(String novoNome) {
        Business business = getBusinessOrThrow();
        business.atualizarNome(novoNome);
        return businessRepository.save(business);
    }

    /**
     * Atualiza o telefone do Business.
     * 
     * @param novoTelefone Novo telefone
     * @return Business atualizado
     */
    public Business atualizarTelefone(String novoTelefone) {
        Business business = getBusinessOrThrow();
        business.atualizarContato(novoTelefone, business.getEndereco());
        return businessRepository.save(business);
    }

    /**
     * Atualiza o endereço do Business.
     * 
     * @param novoEndereco Novo endereço
     * @return Business atualizado
     */
    public Business atualizarEndereco(String novoEndereco) {
        Business business = getBusinessOrThrow();
        business.atualizarContato(business.getTelefone(), novoEndereco);
        return businessRepository.save(business);
    }

    /**
     * Atualiza o horário de funcionamento do Business.
     * 
     * @param abertura Horário de abertura
     * @param fechamento Horário de fechamento
     * @param diasFuncionamento Dias de funcionamento
     * @return Business atualizado
     */
    public Business atualizarHorarioFuncionamento(LocalTime abertura, LocalTime fechamento, Set<DiaSemana> diasFuncionamento) {
        Business business = getBusinessOrThrow();
        BusinessHours novoHorario = new BusinessHours(abertura, fechamento, diasFuncionamento);
        business.atualizarHorarioFuncionamento(novoHorario);
        return businessRepository.save(business);
    }

    /**
     * Ativa o Business (transição para ATIVO).
     * 
     * @return Business atualizado
     */
    public Business ativarBusiness() {
        Business business = getBusinessOrThrow();
        business.ativar();
        return businessRepository.save(business);
    }

    /**
     * Desativa o Business (transição para INATIVO).
     * 
     * @return Business atualizado
     */
    public Business desativarBusiness() {
        Business business = getBusinessOrThrow();
        business.desativar();
        return businessRepository.save(business);
    }

    /**
     * Verifica se existe um Business configurado.
     * 
     * @return true se existe Business
     */
    public boolean existeBusiness() {
        return businessRepository.findAll().stream().findFirst().isPresent();
    }

    /**
     * Verifica se o Business está ativo.
     * 
     * @return true se Business está ativo ou em trial
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