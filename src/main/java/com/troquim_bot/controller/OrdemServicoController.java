package com.troquim_bot.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.troquim_bot.model.OrdemServico;
import com.troquim_bot.service.OrdemServicoService;

/**
 * Controller REST para gerenciamento de Ordens de Serviço.
 */
@RestController
@RequestMapping("/ordens")
public class OrdemServicoController {

	private final OrdemServicoService ordemServicoService;

	public OrdemServicoController(OrdemServicoService ordemServicoService) {
		this.ordemServicoService = ordemServicoService;
	}

	@GetMapping
	public ResponseEntity<List<OrdemServico>> listarTodos() {
		return ResponseEntity.ok(ordemServicoService.listarTodos());
	}

	@GetMapping("/{id}")
	public ResponseEntity<OrdemServico> buscarPorId(@PathVariable Long id) {
		Optional<OrdemServico> ordem = ordemServicoService.buscarPorId(id);
		return ordem.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<OrdemServico> salvar(@RequestBody OrdemServico ordemServico) {
		OrdemServico ordemSalva = ordemServicoService.salvar(ordemServico);
		return ResponseEntity.status(HttpStatus.CREATED).body(ordemSalva);
	}

	@PutMapping("/{id}")
	public ResponseEntity<OrdemServico> atualizar(@PathVariable Long id, @RequestBody OrdemServico ordemAtualizada) {
		Optional<OrdemServico> ordem = ordemServicoService.atualizar(id, ordemAtualizada);
		return ordem.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> excluir(@PathVariable Long id) {
		boolean excluido = ordemServicoService.excluir(id);
		if (excluido) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}
}