package com.troquim_bot.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.troquim_bot.model.Cliente;
import com.troquim_bot.model.OrdemServico;
import com.troquim_bot.model.StatusOrdemServico;
import com.troquim_bot.repository.ClienteRepository;
import com.troquim_bot.repository.OrdemServicoRepository;

/**
 * Service com regras de negócio para Ordem de Serviço.
 */
@Service
public class OrdemServicoService {

	private final OrdemServicoRepository ordemServicoRepository;
	private final ClienteRepository clienteRepository;

	public OrdemServicoService(OrdemServicoRepository ordemServicoRepository, ClienteRepository clienteRepository) {
		this.ordemServicoRepository = ordemServicoRepository;
		this.clienteRepository = clienteRepository;
	}

	@Transactional(readOnly = true)
	public List<OrdemServico> listarTodos() {
		return ordemServicoRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Optional<OrdemServico> buscarPorId(Long id) {
		return ordemServicoRepository.findById(id);
	}

	@Transactional
	public OrdemServico salvar(OrdemServico ordemServico) {
		// Garante que a data de criação seja definida
		if (ordemServico.getDataCriacao() == null) {
			ordemServico.setDataCriacao(java.time.LocalDateTime.now());
		}
		// Se status não informado, define como ABERTA
		if (ordemServico.getStatus() == null) {
			ordemServico.setStatus(StatusOrdemServico.ABERTA);
		}
		return ordemServicoRepository.save(ordemServico);
	}

	@Transactional
	public Optional<OrdemServico> atualizar(Long id, OrdemServico ordemAtualizada) {
		return ordemServicoRepository.findById(id).map(ordemExistente -> {
			// Atualiza cliente se fornecido
			if (ordemAtualizada.getCliente() != null && ordemAtualizada.getCliente().getId() != null) {
				Cliente cliente = clienteRepository.findById(ordemAtualizada.getCliente().getId())
						.orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado"));
				ordemExistente.setCliente(cliente);
			}
			ordemExistente.setAparelho(ordemAtualizada.getAparelho());
			ordemExistente.setDefeitoRelatado(ordemAtualizada.getDefeitoRelatado());
			ordemExistente.setStatus(ordemAtualizada.getStatus());
			ordemExistente.setValorEstimado(ordemAtualizada.getValorEstimado());
			return ordemServicoRepository.save(ordemExistente);
		});
	}

	@Transactional
	public boolean excluir(Long id) {
		return ordemServicoRepository.findById(id).map(ordem -> {
			ordemServicoRepository.delete(ordem);
			return true;
		}).orElse(false);
	}
}