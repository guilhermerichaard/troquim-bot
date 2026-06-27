package com.troquim_bot.chatbot.dto;

/**
 * DTO para receber mensagens do chatbot.
 */
public class ChatMessageRequest {

	private String telefone;
	private String mensagem;

	public String getTelefone() {
		return telefone;
	}

	public void setTelefone(String telefone) {
		this.telefone = telefone;
	}

	public String getMensagem() {
		return mensagem;
	}

	public void setMensagem(String mensagem) {
		this.mensagem = mensagem;
	}
}