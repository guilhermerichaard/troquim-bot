package com.troquim_bot.support;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link ObjectProvider} de teste para capacidades OPCIONAIS.
 *
 * Os testes de conversa precisam expressar "esta capacidade não existe neste ambiente"
 * — que é o caso de produção quando o WhatsApp Flow está desligado. Sem isto, cada teste
 * teria de montar um provider anônimo.
 */
public final class OptionalBeans {

    private OptionalBeans() {
    }

    /** Capacidade ausente: o chamador deve cair no fallback. */
    public static <T> ObjectProvider<T> ausente() {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() {
                throw new NoSuchBeanDefinitionException("bean opcional ausente no teste");
            }

            @Override
            public T getObject(Object... args) {
                return getObject();
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }
        };
    }

    /** Capacidade presente, com o duplo informado. */
    public static <T> ObjectProvider<T> de(T bean) {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() {
                return bean;
            }

            @Override
            public T getObject(Object... args) {
                return bean;
            }

            @Override
            public T getIfAvailable() {
                return bean;
            }

            @Override
            public T getIfUnique() {
                return bean;
            }
        };
    }
}
