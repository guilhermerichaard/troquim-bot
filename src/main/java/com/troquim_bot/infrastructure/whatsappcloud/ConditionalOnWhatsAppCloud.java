package com.troquim_bot.infrastructure.whatsappcloud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Feature flag da integração WhatsApp Cloud. Todos os beans da integração
 * (inbound, outbound, receipt store, controller, validação) são condicionais a
 * {@code troquim.integrations.whatsapp.cloud.enabled=true}. Desligada, a aplicação
 * inicia SEM credenciais e nenhum bean da integração é criado.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "troquim.integrations.whatsapp.cloud", name = "enabled", havingValue = "true")
public @interface ConditionalOnWhatsAppCloud {
}
