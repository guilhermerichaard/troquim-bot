package com.troquim_bot.ai.memory;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ConversationMemory {

    private static final int MAX_MESSAGES = 20;

    private final ConcurrentMap<String, List<ConversationMessage>> conversations = new ConcurrentHashMap<>();

    public void addUserMessage(String numero, String mensagem) {
        addMessage(numero, new ConversationMessage("user", mensagem, LocalDateTime.now()));
    }

    public void addAssistantMessage(String numero, String resposta) {
        addMessage(numero, new ConversationMessage("assistant", resposta, LocalDateTime.now()));
    }

    public List<ConversationMessage> getConversation(String numero) {
        List<ConversationMessage> messages = conversations.get(chave(numero));

        if (messages == null) {
            return List.of();
        }

        return List.copyOf(messages);
    }

    public void clearConversation(String numero) {
        conversations.remove(chave(numero));
    }

    private void addMessage(String numero, ConversationMessage message) {
        conversations.compute(chave(numero), (key, messages) -> {
            List<ConversationMessage> updatedMessages = messages == null
                    ? new ArrayList<>()
                    : new ArrayList<>(messages);

            updatedMessages.add(message);

            while (updatedMessages.size() > MAX_MESSAGES) {
                updatedMessages.remove(0);
            }

            return updatedMessages;
        });
    }

    private String chave(String numero) {
        return numero == null ? "" : numero;
    }
}
