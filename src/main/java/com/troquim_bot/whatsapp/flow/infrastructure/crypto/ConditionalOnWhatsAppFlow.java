package com.troquim_bot.whatsapp.flow.infrastructure.crypto;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Feature flag do WhatsApp Flow. Todos os beans do módulo (controller, cifra,
 * provedor de chave, handlers) são condicionais a
 * {@code troquim.integrations.whatsapp.flow.enabled=true}. Desligado, a aplicação
 * inicia SEM chave privada configurada e nenhum bean do Flow é criado.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "troquim.integrations.whatsapp.flow", name = "enabled", havingValue = "true")
public @interface ConditionalOnWhatsAppFlow {
}
