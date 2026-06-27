package com.troquim_bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.troquim_bot.model.Cliente;

/**
 * Repository para acesso a dados de Cliente.
 */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

}