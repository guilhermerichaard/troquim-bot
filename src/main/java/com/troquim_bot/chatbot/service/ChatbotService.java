package com.troquim_bot.chatbot.service;

import org.springframework.stereotype.Service;

import com.troquim_bot.chatbot.dto.ChatMessageRequest;
import com.troquim_bot.chatbot.dto.ChatMessageResponse;

/**
 * Service com lógica simples do chatbot.
 */
@Service
public class ChatbotService {

	public ChatMessageResponse processarMensagem(ChatMessageRequest request) {
		String mensagem = request.getMensagem().toLowerCase();
		String resposta;

		if (mensagem.contains("status")) {
			resposta = "Por favor, informe o número da sua Ordem de Serviço para consultar o status.";
		} else if (mensagem.contains("orçamento") || mensagem.contains("orcamento")) {
			resposta = "Para solicitar um orçamento, por favor informe:\n- Aparelho\n- Defeito relatado";
		} else if (mensagem.contains("horário") || mensagem.contains("horario")) {
			resposta = "Horário de atendimento:\nSegunda a Sexta: 8h às 18h\nSábado: 8h às 12h";
		} else {
			resposta = "Olá! Sou o assistente Troquim. Posso ajudar com:\n"
					+ "1 - Solicitar orçamento\n"
					+ "2 - Consultar status da OS\n"
					+ "3 - Falar com atendente";
		}

		ChatMessageResponse response = new ChatMessageResponse();
		response.setResposta(resposta);
		return response;
	}
}