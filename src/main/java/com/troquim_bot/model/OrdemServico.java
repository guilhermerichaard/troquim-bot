package com.troquim_bot.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidade que representa uma ordem de serviço.
 */
@Entity
@Table(name = "ordens_servico")
public class OrdemServico {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@Column(nullable = false)
	private String aparelho;

	@Column(columnDefinition = "TEXT")
	private String defeitoRelatado;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatusOrdemServico status;

	private BigDecimal valorEstimado;

	@Column(nullable = false, updatable = false)
	private LocalDateTime dataCriacao;

	public OrdemServico() {
		this.dataCriacao = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Cliente getCliente() {
		return cliente;
	}

	public void setCliente(Cliente cliente) {
		this.cliente = cliente;
	}

	public String getAparelho() {
		return aparelho;
	}

	public void setAparelho(String aparelho) {
		this.aparelho = aparelho;
	}

	public String getDefeitoRelatado() {
		return defeitoRelatado;
	}

	public void setDefeitoRelatado(String defeitoRelatado) {
		this.defeitoRelatado = defeitoRelatado;
	}

	public StatusOrdemServico getStatus() {
		return status;
	}

	public void setStatus(StatusOrdemServico status) {
		this.status = status;
	}

	public BigDecimal getValorEstimado() {
		return valorEstimado;
	}

	public void setValorEstimado(BigDecimal valorEstimado) {
		this.valorEstimado = valorEstimado;
	}

	public LocalDateTime getDataCriacao() {
		return dataCriacao;
	}

	public void setDataCriacao(LocalDateTime dataCriacao) {
		this.dataCriacao = dataCriacao;
	}
}