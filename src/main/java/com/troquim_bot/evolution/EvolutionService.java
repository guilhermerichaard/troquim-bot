package com.troquim_bot.evolution;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class EvolutionService {

    private final RestTemplate restTemplate = new RestTemplate();

    private final String evolutionUrl = "http://localhost:8081";
    private final String instanceName = "troquim";
    private final String apiKey = "87F36A639F1C-4C18-8B5B-E283BC8CAF92";

    public void enviarMensagem(String numero, String texto) {
        String url = evolutionUrl + "/message/sendText/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apiKey);

        Map<String, Object> body = Map.of(
                "number", numero,
                "text", texto
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
        restTemplate.postForEntity(url, request, String.class);

        System.out.println("Status HTTP: " + response.getStatusCode());
        System.out.println("Resposta Evolution:");
        System.out.println(response.getBody());
            }
}