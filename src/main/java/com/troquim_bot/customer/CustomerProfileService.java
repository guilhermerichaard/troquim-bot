package com.troquim_bot.customer;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class CustomerProfileService {

    private final ConcurrentMap<String, CustomerProfile> profiles = new ConcurrentHashMap<>();

    public Optional<CustomerProfile> localizarPorTelefone(String numero) {
        return Optional.ofNullable(profiles.get(chave(numero)));
    }

    public CustomerProfile buscarOuCriar(String numero) {
        return profiles.computeIfAbsent(chave(numero), CustomerProfile::new);
    }

    public CustomerProfile iniciarAtendimento(String numero) {
        return profiles.compute(chave(numero), (key, profile) -> {
            CustomerProfile customerProfile = profile == null ? new CustomerProfile(key) : profile;
            customerProfile.registrarNovoAtendimento();
            return customerProfile;
        });
    }

    public CustomerProfile salvarNome(String numero, String nome) {
        if (nome == null || nome.isBlank()) {
            return buscarOuCriar(numero);
        }

        return profiles.compute(chave(numero), (key, profile) -> {
            CustomerProfile customerProfile = profile == null ? new CustomerProfile(key) : profile;
            customerProfile.setNome(nome.trim());
            return customerProfile;
        });
    }

    public CustomerProfile atualizarNome(String numero, String nome) {
        return salvarNome(numero, nome);
    }

    public Optional<String> nomePreferido(CustomerProfile profile) {
        if (profile == null) {
            return Optional.empty();
        }

        if (temValor(profile.getApelido())) {
            return Optional.of(profile.getApelido().trim());
        }

        if (temValor(profile.getNome())) {
            return Optional.of(profile.getNome().trim());
        }

        return Optional.empty();
    }

    public Optional<String> nomePreferido(String numero) {
        return localizarPorTelefone(numero).flatMap(this::nomePreferido);
    }

    public CustomerProfile atualizarUltimoAtendimento(String numero) {
        return profiles.compute(chave(numero), (key, profile) -> {
            CustomerProfile customerProfile = profile == null ? new CustomerProfile(key) : profile;
            customerProfile.atualizarUltimoAtendimento();
            return customerProfile;
        });
    }

    private boolean temValor(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String chave(String numero) {
        return numero == null ? "" : numero;
    }
}
