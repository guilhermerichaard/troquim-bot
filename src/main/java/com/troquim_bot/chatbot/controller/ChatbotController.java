package com.troquim_bot.chatbot.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.troquim_bot.chatbot.dto.ChatMessageRequest;
import com.troquim_bot.chatbot.dto.ChatMessageResponse;
import com.troquim_bot.chatbot.service.ChatbotService;

/**
 * Controller REST para o chatbot.
 */
@RestController
@RequestMapping("/chatbot")
public class ChatbotController {

	private final ChatbotService chatbotService;

	public ChatbotController(ChatbotService chatbotService) {
		this.chatbotService = chatbotService;
	}

	@PostMapping("/mensagem")
	public ResponseEntity<ChatMessageResponse> receberMensagem(@RequestBody ChatMessageRequest request) {
		ChatMessageResponse response = chatbotService.processarMensagem(request);
		return ResponseEntity.ok(response);
	}
}