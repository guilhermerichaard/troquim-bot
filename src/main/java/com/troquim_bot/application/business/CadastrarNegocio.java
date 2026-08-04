package com.troquim_bot.application.business;

import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.repository.BusinessRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Caso de uso de cadastro da raiz de identidade do negócio.
 *
 * ENTRADA MÍNIMA: {@link BusinessId} EXPLÍCITO — nunca gerado aqui. Quem cadastra prova de
 * qual negócio está falando; não há dedução por nome nem criação implícita disparada por
 * Controller, IA ou Flow. É este caso de uso, e só ele, que a porta de onboarding chama
 * antes de {@link com.troquim_bot.application.catalog.ProvisionarNegocio}.
 *
 * IDEMPOTENTE: reexecutar com os MESMOS dados não falha nem duplica — devolve o negócio já
 * cadastrado. Reexecutar com o MESMO id mas dados DIFERENTES é um conflito de identidade e é
 * recusado: um BusinessId não pode passar a significar "outro negócio" silenciosamente.
 *
 * Contato (telefone/endereço) é OPCIONAL — onboarding pode estar incompleto. Nada aqui
 * inventa valor para "completar" o cadastro.
 */
@Component
public class CadastrarNegocio {

    private final BusinessRepository businessRepository;

    public CadastrarNegocio(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    @Transactional
    public Business cadastrar(BusinessId id, String nome, String telefone, String endereco) {
        if (id == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para cadastrar o negócio");
        }

        Business existente = businessRepository.findById(id);
        if (existente != null) {
            if (mesmosDados(existente, nome, telefone, endereco)) {
                return existente;
            }
            throw new IllegalStateException(
                    "BusinessId " + id + " já está cadastrado com dados diferentes; "
                            + "um mesmo id não pode passar a representar outro negócio");
        }

        Business novo = new Business(id, nome, telefone, endereco);
        return businessRepository.save(novo);
    }

    private static boolean mesmosDados(Business existente, String nome, String telefone, String endereco) {
        return Objects.equals(existente.getNome(), normalizado(nome))
                && Objects.equals(existente.getTelefone(), normalizado(telefone))
                && Objects.equals(existente.getEndereco(), normalizado(endereco));
    }

    private static String normalizado(String valor) {
        return valor == null ? null : valor.trim();
    }
}
