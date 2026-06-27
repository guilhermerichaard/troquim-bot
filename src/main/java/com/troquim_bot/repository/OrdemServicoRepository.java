package com.troquim_bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.troquim_bot.model.OrdemServico;

/**
 * Repository para acesso a dados de Ordem de Serviço.
 */
public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {

}