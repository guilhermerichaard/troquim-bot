package com.troquim_bot.owner.application;

public interface OwnerAuthenticationCodeGateway {
    boolean enviar(String phoneE164, String code);
}
