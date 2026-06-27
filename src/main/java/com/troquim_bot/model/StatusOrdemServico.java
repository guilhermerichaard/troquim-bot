package com.troquim_bot.model;

/**
 * Status possíveis para uma ordem de serviço.
 */
public enum StatusOrdemServico {
	ABERTA,
	EM_ANALISE,
	AGUARDANDO_PECA,
	PRONTA,
	ENTREGUE,
	CANCELADA
}