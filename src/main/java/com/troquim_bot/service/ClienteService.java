package com.troquim_bot.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.troquim_bot.model.Cliente;
import com.troquim_bot.repository.ClienteRepository;

/**
 * Service com regras de negócio para Cliente.
 */
@Service
public class ClienteService {

	private final ClienteRepository clienteRepository;

	public ClienteService(ClienteRepository clienteRepository) {
		this.clienteRepository = clienteRepository;
	}

	@Transactional(readOnly = true)
	public List<Cliente> listarTodos() {
		return clienteRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Optional<Cliente> buscarPorId(Long id) {
		return clienteRepository.findById(id);
	}

	@Transactional
	public Cliente salvar(Cliente cliente) {
		return clienteRepository.save(cliente);
	}

	@Transactional
	public Optional<Cliente> atualizar(Long id, Cliente clienteAtualizado) {
		return clienteRepository.findById(id).map(clienteExistente -> {
			clienteExistente.setNome(clienteAtualizado.getNome());
			clienteExistente.setTelefone(clienteAtualizado.getTelefone());
			clienteExistente.setEmail(clienteAtualizado.getEmail());
			clienteExistente.setObservacao(clienteAtualizado.getObservacao());
			return clienteRepository.save(clienteExistente);
		});
	}

	@Transactional
	public boolean excluir(Long id) {
		return clienteRepository.findById(id).map(cliente -> {
			clienteRepository.delete(cliente);
			return true;
		}).orElse(false);
	}
}