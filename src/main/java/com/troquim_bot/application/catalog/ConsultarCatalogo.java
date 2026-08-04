package com.troquim_bot.application.catalog;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.Money;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Caso de uso de consulta do catálogo de um negócio.
 *
 * Fronteira NEUTRA: devolve um modelo próprio da Application. Nada aqui sabe o que é
 * WhatsApp, Meta, tela, Flow ou JSON — quem traduz para um canal específico é o adapter
 * daquele canal. Isso é o que permite Flow, conversa e /app consumirem o MESMO catálogo
 * sem que nenhum deles vire dono da regra.
 *
 * TENANT OBRIGATÓRIO em toda operação, sempre por argumento explícito.
 */
@Component
public class ConsultarCatalogo {

    private final ServiceRepository serviceRepository;
    private final ProfessionalRepository professionalRepository;

    public ConsultarCatalogo(ServiceRepository serviceRepository,
                             ProfessionalRepository professionalRepository) {
        this.serviceRepository = serviceRepository;
        this.professionalRepository = professionalRepository;
    }

    /** Profissional apto a executar um serviço. */
    public record ProfissionalDoCatalogo(ProfessionalId id, String nome) {
    }

    /**
     * Serviço ofertável, com os profissionais que o atendem.
     *
     * {@code preco} é {@link Optional} e permanece vazio quando não há preço definido —
     * nunca zero, nunca moeda default.
     */
    public record ItemDeCatalogo(ServiceId id,
                                 String nome,
                                 String descricao,
                                 Duration duracao,
                                 Optional<Money> preco,
                                 List<ProfissionalDoCatalogo> profissionais) {

        /** Serviço sem ninguém habilitado não é ofertável, ainda que esteja ativo. */
        public boolean ofertavel() {
            return !profissionais.isEmpty();
        }
    }

    /**
     * Resultado da consulta. "Catálogo vazio" é estado explícito e observável, não uma
     * lista vazia que o chamador precise adivinhar o significado — e jamais um catálogo
     * fixo de emergência.
     */
    public record Catalogo(BusinessId businessId, List<ItemDeCatalogo> itens) {

        /** Nenhum serviço ativo com profissional habilitado: negócio não configurado. */
        public boolean naoConfigurado() {
            return itens.isEmpty();
        }

        public Optional<ItemDeCatalogo> porId(ServiceId servico) {
            if (servico == null) {
                return Optional.empty();
            }
            return itens.stream().filter(item -> item.id().equals(servico)).findFirst();
        }
    }

    /**
     * Catálogo ofertável do negócio: serviços ATIVOS que tenham ao menos um profissional
     * ATIVO e habilitado por {@link ServiceId}.
     */
    public Catalogo consultar(BusinessId businessId) {
        exigirTenant(businessId);

        List<ItemDeCatalogo> itens = serviceRepository.listarAtivos(businessId).stream()
                .map(servico -> montarItem(businessId, servico))
                .filter(ItemDeCatalogo::ofertavel)
                .toList();

        return new Catalogo(businessId, itens);
    }

    /**
     * Um serviço específico do negócio, já com seus profissionais.
     *
     * Vazio quando o id não existe, pertence a outro negócio, está inativo ou não tem
     * profissional habilitado — todos indistinguíveis de fora, de propósito.
     */
    public Optional<ItemDeCatalogo> porServico(BusinessId businessId, ServiceId servico) {
        exigirTenant(businessId);
        if (servico == null) {
            return Optional.empty();
        }
        return serviceRepository.buscarPorId(businessId, servico)
                .filter(Service::isAtivo)
                .map(encontrado -> montarItem(businessId, encontrado))
                .filter(ItemDeCatalogo::ofertavel);
    }

    private ItemDeCatalogo montarItem(BusinessId businessId, Service servico) {
        // A habilitação vem do vínculo por ServiceId; o repositório já delega a decisão
        // final ao agregado (Professional.atende), então aqui não há segunda regra.
        List<ProfissionalDoCatalogo> profissionais =
                professionalRepository.listarAtivosPorServico(businessId, servico.getId()).stream()
                        .map(ConsultarCatalogo::paraProfissionalDoCatalogo)
                        .toList();

        return new ItemDeCatalogo(
                servico.getId(),
                servico.getNome(),
                servico.getDescricao(),
                duracaoDe(servico.getDuracao()),
                servico.getPreco(),
                profissionais);
    }

    private static ProfissionalDoCatalogo paraProfissionalDoCatalogo(Professional profissional) {
        return new ProfissionalDoCatalogo(profissional.getId(), profissional.getNome());
    }

    private static Duration duracaoDe(ServiceDuration duracao) {
        return Duration.ofMinutes(duracao.getMinutes());
    }

    private static void exigirTenant(BusinessId businessId) {
        if (businessId == null) {
            throw new IllegalArgumentException(
                    "BusinessId é obrigatório para consultar catálogo; consulta sem tenant é recusada");
        }
    }
}
