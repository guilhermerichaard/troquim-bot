package com.troquim_bot.application.catalog;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.ProfessionalRepository;
import com.troquim_bot.repository.ServiceRepository;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Caso de uso de provisionamento inicial do catálogo de um negócio.
 *
 * Substitui o seed em migration: dado de cliente não pertence a schema versionado. Aqui o
 * provisionamento é uma operação de aplicação, executada sob demanda, com tenant explícito.
 *
 * IDEMPOTENTE POR NATUREZA, não por flag: a identidade usada para decidir "já existe" é o
 * NOME normalizado dentro do negócio. Rodar duas vezes não duplica serviço nem profissional,
 * e não sobrescreve o que já estiver lá — provisionar é completar o que falta, nunca
 * reescrever o que o dono já ajustou.
 *
 * Nenhum nome, telefone, duração ou id de cliente específico vive nesta classe: tudo chega
 * por parâmetro.
 */
@Component
public class ProvisionarNegocio {

    private static final Logger log = LoggerFactory.getLogger(ProvisionarNegocio.class);

    private final ServiceRepository serviceRepository;
    private final ProfessionalRepository professionalRepository;

    public ProvisionarNegocio(ServiceRepository serviceRepository,
                              ProfessionalRepository professionalRepository) {
        this.serviceRepository = serviceRepository;
        this.professionalRepository = professionalRepository;
    }

    /** Serviço a provisionar. Sem preço: precificação está fora do MVP. */
    public record ServicoDesejado(String nome, int duracaoMinutos) {
    }

    /** Profissional a provisionar, habilitado para os serviços informados por nome. */
    public record ProfissionalDesejado(String nome, String telefone, List<String> servicosPorNome) {
    }

    public record Resultado(List<Service> servicosCriados, List<Service> servicosJaExistentes,
                            Optional<Professional> profissionalCriado,
                            Optional<Professional> profissionalJaExistente,
                            List<String> habilitacoesAdicionadas) {

        public boolean fezAlgo() {
            return !servicosCriados.isEmpty() || profissionalCriado.isPresent()
                    || !habilitacoesAdicionadas.isEmpty();
        }
    }

    @Transactional
    public Resultado provisionar(BusinessId businessId,
                                 List<ServicoDesejado> servicos,
                                 ProfissionalDesejado profissional) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para provisionar");
        }
        if (servicos == null || servicos.isEmpty()) {
            throw new IllegalArgumentException("Ao menos um serviço é obrigatório para provisionar");
        }

        List<Service> criados = new ArrayList<>();
        List<Service> existentes = new ArrayList<>();

        List<Service> jaNoCatalogo = serviceRepository.listarTodos(businessId);
        for (ServicoDesejado desejado : servicos) {
            Optional<Service> encontrado = porNome(jaNoCatalogo, desejado.nome());
            if (encontrado.isPresent()) {
                existentes.add(encontrado.get());
                continue;
            }
            Service novo = serviceRepository.salvar(Service.novoSemPreco(
                    ServiceId.generate(), businessId, desejado.nome(), null,
                    ServiceDuration.ofMinutes(desejado.duracaoMinutos())));
            criados.add(novo);
            jaNoCatalogo = new ArrayList<>(jaNoCatalogo);
            jaNoCatalogo.add(novo);
        }

        Optional<Professional> profCriado = Optional.empty();
        Optional<Professional> profExistente = Optional.empty();
        List<String> habilitacoes = new ArrayList<>();

        if (profissional != null) {
            List<Service> catalogoFinal = jaNoCatalogo;
            Optional<Professional> jaExiste = professionalRepository.listarTodos(businessId).stream()
                    .filter(p -> mesmoNome(p.getNome(), profissional.nome()))
                    .findFirst();

            Professional alvo = jaExiste.orElseGet(() -> new Professional(
                    ProfessionalId.generate(), businessId, profissional.nome(),
                    java.util.Set.of(), java.util.Set.of(), profissional.telefone()));

            // Habilita apenas o que falta: reexecutar não recria vínculo já existente.
            for (String nomeServico : profissional.servicosPorNome()) {
                Optional<Service> servico = porNome(catalogoFinal, nomeServico);
                if (servico.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Serviço '" + nomeServico + "' não existe no negócio; não é possível habilitar");
                }
                if (!alvo.getServicosHabilitados().contains(servico.get().getId())) {
                    alvo.habilitarPara(servico.get().getId());
                    habilitacoes.add(nomeServico);
                }
            }

            professionalRepository.salvar(alvo);
            if (jaExiste.isPresent()) {
                profExistente = Optional.of(alvo);
            } else {
                profCriado = Optional.of(alvo);
            }
        }

        log.info("Provisionamento concluído: {} serviço(s) criado(s), {} já existente(s), "
                        + "profissional {}, {} habilitação(ões) adicionada(s)",
                criados.size(), existentes.size(),
                profCriado.isPresent() ? "criado" : (profExistente.isPresent() ? "já existente" : "não informado"),
                habilitacoes.size());

        return new Resultado(criados, existentes, profCriado, profExistente, habilitacoes);
    }

    private static Optional<Service> porNome(List<Service> catalogo, String nome) {
        return catalogo.stream().filter(s -> mesmoNome(s.getNome(), nome)).findFirst();
    }

    /** Identidade de provisionamento: nome normalizado, para não duplicar por acento/caixa. */
    private static boolean mesmoNome(String a, String b) {
        return a != null && b != null
                && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }
}
