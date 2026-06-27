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

import com.troquim_bot.model.Cliente;
import com.troquim_bot.service.ClienteService;

/**
 * Controller REST para gerenciamento de Clientes.
 */
@RestController
@RequestMapping("/clientes")
public class ClienteController {

	private final ClienteService clienteService;

	public ClienteController(ClienteService clienteService) {
		this.clienteService = clienteService;
	}

	@GetMapping
	public ResponseEntity<List<Cliente>> listarTodos() {
		return ResponseEntity.ok(clienteService.listarTodos());
	}

	@GetMapping("/{id}")
	public ResponseEntity<Cliente> buscarPorId(@PathVariable Long id) {
		Optional<Cliente> cliente = clienteService.buscarPorId(id);
		return cliente.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<Cliente> salvar(@RequestBody Cliente cliente) {
		Cliente clienteSalvo = clienteService.salvar(cliente);
		return ResponseEntity.status(HttpStatus.CREATED).body(clienteSalvo);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Cliente> atualizar(@PathVariable Long id, @RequestBody Cliente clienteAtualizado) {
		Optional<Cliente> cliente = clienteService.atualizar(id, clienteAtualizado);
		return cliente.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> excluir(@PathVariable Long id) {
		boolean excluido = clienteService.excluir(id);
		if (excluido) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}
}