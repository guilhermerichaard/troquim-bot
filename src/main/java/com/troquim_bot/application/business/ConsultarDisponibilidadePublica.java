package com.troquim_bot.application.business;

import com.troquim_bot.application.availability.ConsultarDisponibilidade;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Disponibilidade pública de um dia, resolvida a partir do slug.
 *
 * NÃO CALCULA horário, NÃO consulta appointments nem calendário: delega inteiramente a
 * {@link ConsultarDisponibilidade#doDia}, a MESMA autoridade que o Flow usa. Se esta classe
 * algum dia contiver regra de agenda, existirão duas agendas.
 */
@Component
public class ConsultarDisponibilidadePublica {

    private final ResolverNegocioPublicoPorSlug resolverNegocioPublicoPorSlug;
    private final ConsultarDisponibilidade consultarDisponibilidade;

    public ConsultarDisponibilidadePublica(ResolverNegocioPublicoPorSlug resolverNegocioPublicoPorSlug,
                                           ConsultarDisponibilidade consultarDisponibilidade) {
        this.resolverNegocioPublicoPorSlug = resolverNegocioPublicoPorSlug;
        this.consultarDisponibilidade = consultarDisponibilidade;
    }

    /**
     * Vazio quando o SLUG não resolve (perfil DRAFT/inexistente, negócio inativo). Serviço ou
     * profissional que não existam, sejam de outro negócio ou estejam indisponíveis NÃO
     * produzem vazio aqui — {@link ConsultarDisponibilidade} já devolve isso como uma
     * {@code Condicao} explícita, e é assim que a resposta pública NUNCA revela se um id
     * pertence a outro tenant: a forma da resposta é a mesma em qualquer desses casos.
     */
    @Transactional(readOnly = true)
    public Optional<ConsultarDisponibilidade.AgendaDoDia> consultar(String slugBruto, ServiceId servico,
                                                                     ProfessionalId profissional, LocalDate data) {
        return resolverNegocioPublicoPorSlug.resolver(slugBruto)
                .map(negocio -> consultarDisponibilidade.doDia(
                        negocio.businessId(), servico, profissional, data));
    }
}
