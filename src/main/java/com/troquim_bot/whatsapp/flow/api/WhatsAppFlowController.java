package com.troquim_bot.whatsapp.flow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.troquim_bot.whatsapp.flow.application.FlowExchangeOutcome;
import com.troquim_bot.whatsapp.flow.application.FlowExchangeService;
import com.troquim_bot.whatsapp.flow.application.FlowRequest;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.FlowCipher;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.FlowKeyDecryptionException;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.FlowPayloadDecryptionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Endpoint do WhatsApp Flow de agendamento.
 *
 * Camada de INTERFACE, deliberadamente fina: decifra → delega → cifra → mapeia para HTTP.
 * Nenhuma regra de agendamento passa por aqui.
 *
 * A rota é pública no Spring Security porque a autenticação É o protocolo criptográfico:
 * só quem possui a chave AES cifrada com a nossa chave pública consegue produzir um corpo
 * que decifre e autentique (GCM). Um atacante sem a chave privada não passa do passo 1.
 *
 * Códigos do protocolo:
 * <ul>
 *   <li><b>421</b> — falha ao decifrar a chave AES; sinaliza à Meta que ela deve refazer o
 *       handshake de chave pública e reenviar;</li>
 *   <li><b>427</b> — {@code flow_token} inválido ou expirado;</li>
 *   <li><b>400</b> — envelope/corpo malformado;</li>
 *   <li><b>500</b> — falha inesperada.</li>
 * </ul>
 *
 * Nada do payload decifrado, da chave AES, do IV ou de dado pessoal é registrado em log —
 * apenas o tipo da falha.
 */
@RestController
@RequestMapping("/api/v1/whatsapp/flows")
@ConditionalOnWhatsAppFlow
public class WhatsAppFlowController {

    /** Não existe na especificação HTTP: é código próprio do protocolo de Flows. */
    private static final int STATUS_TOKEN_INVALIDO = 427;
    private static final int STATUS_CHAVE_DESSINCRONIZADA = 421;

    /**
     * Teto do corpo aceito. Uma troca de Flow real tem poucos KB; 64 KB dá folga larga
     * e ainda impede que a rota pública vire trabalho criptográfico ilimitado.
     */
    private static final int CORPO_MAX_BYTES = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(WhatsAppFlowController.class);

    private final FlowCipher cipher;
    private final FlowExchangeService exchangeService;
    // Mapper próprio, como no webhook da Cloud API: o corpo é lido bruto e não passa
    // pelos conversores do Spring.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhatsAppFlowController(FlowCipher cipher, FlowExchangeService exchangeService) {
        this.cipher = cipher;
        this.exchangeService = exchangeService;
    }

    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> exchange(HttpServletRequest httpRequest) throws IOException {
        final EncryptedFlowEnvelope envelope;
        try {
            envelope = lerEnvelope(httpRequest.getInputStream().readAllBytes());
        } catch (FlowPayloadDecryptionException e) {
            return ResponseEntity.badRequest().build();
        }

        final byte[] aesKey;
        try {
            aesKey = cipher.decifrarChaveAes(envelope.encryptedAesKey());
        } catch (FlowKeyDecryptionException e) {
            // 421 é a única resposta que faz a Meta ressincronizar a chave pública.
            log.warn("Falha ao decifrar a chave AES do WhatsApp Flow; solicitando ressincronia de chave");
            return ResponseEntity.status(STATUS_CHAVE_DESSINCRONIZADA).build();
        } catch (FlowPayloadDecryptionException e) {
            return ResponseEntity.badRequest().build();
        }

        final byte[] iv;
        final FlowRequest request;
        try {
            iv = cipher.decodificarIv(envelope.initialVector());
            String json = cipher.decifrarCorpo(envelope.encryptedFlowData(), aesKey, iv);
            request = desserializar(json);
        } catch (FlowPayloadDecryptionException e) {
            log.warn("Requisição do WhatsApp Flow malformada ou não autenticada");
            return ResponseEntity.badRequest().build();
        }

        FlowExchangeOutcome outcome;
        try {
            outcome = exchangeService.processar(request);
        } catch (RuntimeException e) {
            log.error("Erro ao processar troca do WhatsApp Flow: {}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return switch (outcome.status()) {
            case TOKEN_INVALIDO -> ResponseEntity.status(STATUS_TOKEN_INVALIDO).build();
            case REQUISICAO_INVALIDA -> ResponseEntity.badRequest().build();
            case OK -> cifrarResposta(outcome, aesKey, iv);
        };
    }

    private ResponseEntity<String> cifrarResposta(FlowExchangeOutcome outcome, byte[] aesKey, byte[] iv) {
        try {
            String json = objectMapper.writeValueAsString(outcome.resposta().comoMapa());
            // O corpo da resposta é a string base64 pura, não um JSON envelopado.
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(cipher.cifrarResposta(json, aesKey, iv));
        } catch (Exception e) {
            log.error("Falha ao cifrar a resposta do WhatsApp Flow: {}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Lê o envelope do corpo bruto. JSON inválido vira 400, nunca exceção de framework. */
    private EncryptedFlowEnvelope lerEnvelope(byte[] corpo) {
        if (corpo == null || corpo.length == 0) {
            throw new FlowPayloadDecryptionException("Corpo vazio no endpoint do Flow");
        }
        if (corpo.length > CORPO_MAX_BYTES) {
            // Rota pública: um corpo gigante seria trabalho de RSA/AES pago por nós antes
            // de qualquer autenticação. O corte vem antes de qualquer parsing.
            throw new FlowPayloadDecryptionException(
                    "Corpo excede o limite do endpoint do Flow (" + corpo.length + " bytes)");
        }
        try {
            JsonNode raiz = objectMapper.readTree(new String(corpo, StandardCharsets.UTF_8));
            return new EncryptedFlowEnvelope(
                    texto(raiz, "encrypted_flow_data"),
                    texto(raiz, "encrypted_aes_key"),
                    texto(raiz, "initial_vector"));
        } catch (Exception e) {
            throw new FlowPayloadDecryptionException(
                    "Envelope do Flow não é JSON válido (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /**
     * Converte o JSON decifrado no contrato da Application. Feito à mão (e não por binding
     * automático) porque {@code data} é um mapa livre de entrada não confiável, e porque
     * um JSON inválido precisa virar 400 controlado em vez de exceção de framework.
     */
    private FlowRequest desserializar(String json) {
        try {
            JsonNode raiz = objectMapper.readTree(json);
            Map<String, Object> data = new LinkedHashMap<>();
            JsonNode noData = raiz.get("data");
            if (noData != null && noData.isObject()) {
                data = objectMapper.convertValue(noData, Map.class);
            }
            return new FlowRequest(
                    texto(raiz, "version"),
                    texto(raiz, "action"),
                    texto(raiz, "screen"),
                    data,
                    texto(raiz, "flow_token"));
        } catch (Exception e) {
            throw new FlowPayloadDecryptionException(
                    "Corpo do Flow não é JSON válido (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private static String texto(JsonNode raiz, String campo) {
        JsonNode no = raiz.get(campo);
        return no == null || no.isNull() ? null : no.asText();
    }
}
