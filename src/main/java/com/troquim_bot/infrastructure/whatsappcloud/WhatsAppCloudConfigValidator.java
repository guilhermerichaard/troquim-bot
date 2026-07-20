package com.troquim_bot.infrastructure.whatsappcloud;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validação fail-fast da configuração da integração, no espírito de
 * {@code AdminApiKeyConfig}: em produção (profile azure) com a integração LIGADA,
 * a ausência de qualquer credencial obrigatória — ou whitespace ao redor — falha o
 * startup com mensagem clara. Fora do azure a validação é leniente (dev/test podem
 * usar fixtures). Desligada, este bean nem é criado (a app inicia sem credenciais).
 *
 * Sem trim silencioso: um segredo malformado deve ser corrigido, não aceito. Nenhum
 * valor é registrado — apenas o NOME da propriedade em falta.
 */
@Component
@ConditionalOnWhatsAppCloud
public class WhatsAppCloudConfigValidator {

    private final WhatsAppCloudProperties properties;
    private final Environment environment;

    public WhatsAppCloudConfigValidator(WhatsAppCloudProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (!environment.acceptsProfiles(Profiles.of("azure"))) {
            return;
        }

        // Credenciais/ids operacionalmente obrigatórios quando a integração está ligada.
        Map<String, String> required = new LinkedHashMap<>();
        required.put("troquim.integrations.whatsapp.cloud.verify-token", properties.getVerifyToken());
        required.put("troquim.integrations.whatsapp.cloud.app-secret", properties.getAppSecret());
        required.put("troquim.integrations.whatsapp.cloud.access-token", properties.getAccessToken());
        required.put("troquim.integrations.whatsapp.cloud.phone-number-id", properties.getPhoneNumberId());
        required.put("troquim.integrations.whatsapp.cloud.graph-api-version", properties.getGraphApiVersion());

        for (Map.Entry<String, String> entry : required.entrySet()) {
            requireConfigured(entry.getKey(), entry.getValue());
        }

        // base-url tem default; waba-id é opcional — mas whitespace é sempre malformado.
        rejectWhitespace("troquim.integrations.whatsapp.cloud.base-url", properties.getBaseUrl());
        rejectWhitespace("troquim.integrations.whatsapp.cloud.waba-id", properties.getWabaId());
    }

    private void requireConfigured(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " deve ser configurada quando a integracao WhatsApp Cloud esta ligada "
                            + "no profile azure. Defina a variavel de ambiente correspondente antes de iniciar.");
        }
        rejectWhitespace(name, value);
    }

    private void rejectWhitespace(String name, String value) {
        if (value != null && !value.equals(value.strip())) {
            throw new IllegalStateException(
                    name + " possui whitespace no inicio/fim e nunca casaria/funcionaria como esperado. "
                            + "Forneca o valor sem espacos ao redor (cheque newline final no segredo do container).");
        }
    }
}
