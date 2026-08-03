package com.troquim_bot.whatsapp.flow.application.catalog;

import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ADAPTER do catálogo para o vocabulário do Flow.
 *
 * NÃO TEM CATÁLOGO PRÓPRIO. Toda pergunta sobre "quais serviços" e "quem atende" é
 * delegada a {@link ConsultarCatalogo}, o caso de uso neutro da Application que lê o
 * catálogo persistido por {@link BusinessId}. A lista fixa de cinco serviços que morava
 * aqui deixou de existir: ela era um segundo catálogo, invisível ao dono do negócio e
 * idêntico para todos os tenants.
 *
 * Sem fallback. Se o negócio não configurou catálogo, a resposta é "não configurado" —
 * jamais um catálogo de emergência.
 *
 * A única regra desta classe é de TRADUÇÃO: UUID textual ↔ identidade de domínio. O
 * parsing é ESTRITO — texto que não é UUID vira {@link Optional#empty()}, nunca um id
 * derivado por hash ou normalização.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowCatalogProvider {

    private final ConsultarCatalogo consultarCatalogo;

    public FlowCatalogProvider(ConsultarCatalogo consultarCatalogo) {
        this.consultarCatalogo = consultarCatalogo;
    }

    /**
     * Projeção do catálogo do negócio para as telas.
     *
     * {@code profissionais} é a UNIÃO dos profissionais habilitados nos serviços ofertáveis
     * — usada só na renderização inicial da tela, quando ainda não há serviço escolhido. A
     * decisão de quem atende O QUE continua sendo por serviço, em
     * {@link #profissionaisPara}.
     */
    public record CatalogoDoFlow(List<FlowServiceOption> servicos,
                                 List<FlowProfessionalOption> profissionais) {

        /** Estado explícito: o negócio não tem serviço ofertável hoje. */
        public boolean naoConfigurado() {
            return servicos.isEmpty();
        }
    }

    public CatalogoDoFlow catalogo(BusinessId businessId) {
        List<FlowServiceOption> servicos = new ArrayList<>();
        LinkedHashSet<FlowProfessionalOption> profissionais = new LinkedHashSet<>();

        for (ConsultarCatalogo.ItemDeCatalogo item : consultarCatalogo.consultar(businessId).itens()) {
            servicos.add(paraOpcao(item));
            item.profissionais().stream().map(FlowCatalogProvider::paraOpcao).forEach(profissionais::add);
        }
        return new CatalogoDoFlow(List.copyOf(servicos), List.copyOf(profissionais));
    }

    /**
     * Resolve o serviço a partir do id devolvido pela tela.
     *
     * Vazio quando: o texto não é UUID, o serviço não existe, pertence a outro negócio,
     * está inativo ou não tem ninguém habilitado. Indistinguíveis de fora, de propósito.
     */
    public Optional<FlowServiceOption> servicoPorId(BusinessId businessId, String id) {
        return comoUuid(id)
                .flatMap(uuid -> consultarCatalogo.porServico(businessId, ServiceId.from(uuid)))
                .map(FlowCatalogProvider::paraOpcao);
    }

    /** Profissionais ATIVOS habilitados para o serviço, pelo vínculo por ServiceId. */
    public List<FlowProfessionalOption> profissionaisPara(BusinessId businessId, FlowServiceOption servico) {
        if (servico == null) {
            return List.of();
        }
        return consultarCatalogo.porServico(businessId, servico.servicoId())
                .map(item -> item.profissionais().stream()
                        .map(FlowCatalogProvider::paraOpcao)
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * Resolve um profissional NO CONTEXTO do serviço. Vazio quando o id não é UUID, é
     * desconhecido, é de outro negócio, está inativo OU não atende aquele serviço — a tela
     * não é autoridade sobre compatibilidade.
     */
    public Optional<FlowProfessionalOption> profissionalPara(BusinessId businessId,
                                                             FlowServiceOption servico, String id) {
        Optional<UUID> alvo = comoUuid(id);
        if (alvo.isEmpty()) {
            return Optional.empty();
        }
        ProfessionalId procurado = ProfessionalId.from(alvo.get());
        return profissionaisPara(businessId, servico).stream()
                .filter(p -> p.professionalId().equals(procurado))
                .findFirst();
    }

    private static FlowServiceOption paraOpcao(ConsultarCatalogo.ItemDeCatalogo item) {
        return new FlowServiceOption(item.id(), item.nome(), item.duracao());
    }

    private static FlowProfessionalOption paraOpcao(ConsultarCatalogo.ProfissionalDoCatalogo profissional) {
        return new FlowProfessionalOption(profissional.id(), profissional.nome());
    }

    /**
     * Parsing ESTRITO. Um id malformado é erro controlado do cliente, não motivo para
     * derivar outro identificador — derivar produziria um agendamento apontando para um
     * serviço que ninguém cadastrou.
     */
    private static Optional<UUID> comoUuid(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id.trim()));
        } catch (IllegalArgumentException naoEhUuid) {
            return Optional.empty();
        }
    }
}
