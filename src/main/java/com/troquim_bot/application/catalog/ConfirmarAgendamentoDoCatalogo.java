package com.troquim_bot.application.catalog;

import com.troquim_bot.application.booking.BookingApplicationService;
import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Caso de uso de confirmação de agendamento A PARTIR DO CATÁLOGO PERSISTIDO.
 *
 * É o caminho OFICIAL de qualquer canal que ofereça o catálogo real (hoje o WhatsApp Flow;
 * amanhã a página pública e o /app). Existe para que as regras abaixo tenham UM dono:
 *
 * <ol>
 *   <li>o {@link BusinessId} é obrigatório e vem da sessão validada no servidor — nunca do
 *       corpo enviado pelo cliente;</li>
 *   <li>o serviço precisa pertencer àquele negócio;</li>
 *   <li>o serviço precisa estar ATIVO;</li>
 *   <li>o profissional precisa pertencer àquele negócio;</li>
 *   <li>o profissional precisa estar ATIVO;</li>
 *   <li>o profissional precisa estar HABILITADO para aquele {@link ServiceId}, pelo vínculo
 *       oficial — texto de especialidade não habilita ninguém.</li>
 * </ol>
 *
 * As quatro primeiras e a quinta caem todas na mesma consulta: {@link ConsultarCatalogo}
 * só devolve serviço ativo, do negócio informado, com profissionais ativos e habilitados.
 * Um serviço de outro tenant é indistinguível de um serviço inexistente — de propósito.
 *
 * NEUTRO: não conhece Flow, Meta, WhatsApp nem tela. Devolve erro CONTROLADO (um
 * {@link Recusa}) para toda violação, em vez de exceção: escolha inválida do cliente é
 * fluxo normal, e cada canal decide como dizer isso.
 *
 * IDENTIDADE DO SERVIÇO: o {@link ServiceId} recebido é o do catálogo e chega intacto ao
 * {@code appointment.service_id}. Nenhum fallback para id derivado de texto — se a
 * validação falha, o agendamento não acontece.
 */
@Component
public class ConfirmarAgendamentoDoCatalogo {

    private static final Logger log = LoggerFactory.getLogger(ConfirmarAgendamentoDoCatalogo.class);

    private final ConsultarCatalogo consultarCatalogo;
    private final BookingApplicationService bookingApplicationService;

    public ConfirmarAgendamentoDoCatalogo(ConsultarCatalogo consultarCatalogo,
                                          BookingApplicationService bookingApplicationService) {
        this.consultarCatalogo = consultarCatalogo;
        this.bookingApplicationService = bookingApplicationService;
    }

    /**
     * Pedido de confirmação.
     *
     * @param businessId tenant, obtido da sessão validada no servidor (obrigatório)
     * @param servico    {@link ServiceId} real do catálogo (obrigatório)
     * @param profissional {@link ProfessionalId} real do catálogo (obrigatório)
     * @param telefone   identidade do cliente, resolvida pelo servidor
     * @param nome       nome informado pelo cliente (apresentação)
     * @param chave      identidade do COMANDO, para idempotência
     */
    public record Pedido(BusinessId businessId,
                         ServiceId servico,
                         ProfessionalId profissional,
                         String telefone,
                         String nome,
                         LocalDate data,
                         LocalTime horario,
                         BookingCommandKey chave) {
    }

    /**
     * Por que o pedido foi recusado ANTES de qualquer escrita.
     *
     * Os dois motivos são deliberadamente grossos: detalhar "existe mas é de outro negócio"
     * transformaria a recusa em oráculo do catálogo alheio.
     */
    public enum Recusa {
        /** Não existe, é de outro negócio, está inativo ou não tem ninguém habilitado. */
        SERVICO_INDISPONIVEL,
        /** Não existe, é de outro negócio, está inativo ou não atende este serviço. */
        PROFISSIONAL_INDISPONIVEL
    }

    /** Ou o desfecho do agendamento, ou a recusa de catálogo. Nunca os dois. */
    public record Resultado(Optional<BookingResult> agendamento, Optional<Recusa> recusa) {

        public static Resultado de(BookingResult resultado) {
            return new Resultado(Optional.of(resultado), Optional.empty());
        }

        public static Resultado recusado(Recusa motivo) {
            return new Resultado(Optional.empty(), Optional.of(motivo));
        }

        public boolean foiRecusado() {
            return recusa.isPresent();
        }
    }

    public Resultado confirmar(Pedido pedido) {
        exigir(pedido != null, "Pedido de confirmação é obrigatório");
        exigir(pedido.businessId() != null,
                "BusinessId é obrigatório e deve vir da sessão validada no servidor");
        exigir(pedido.servico() != null, "ServiceId é obrigatório");
        exigir(pedido.profissional() != null, "ProfessionalId é obrigatório");

        // Uma consulta responde tenant + ativo + ofertável. Nada é reimplementado aqui.
        Optional<ConsultarCatalogo.ItemDeCatalogo> item =
                consultarCatalogo.porServico(pedido.businessId(), pedido.servico());
        if (item.isEmpty()) {
            log.info("Confirmação recusada: serviço fora do catálogo ofertável do negócio.");
            return Resultado.recusado(Recusa.SERVICO_INDISPONIVEL);
        }

        // Habilitação por ServiceId: a lista já contém apenas profissionais ATIVOS, do
        // MESMO negócio e vinculados a ESTE serviço.
        boolean habilitado = item.get().profissionais().stream()
                .anyMatch(p -> p.id().equals(pedido.profissional()));
        if (!habilitado) {
            log.info("Confirmação recusada: profissional não habilitado para o serviço escolhido.");
            return Resultado.recusado(Recusa.PROFISSIONAL_INDISPONIVEL);
        }

        // Criação delegada ao caminho TIPADO: duração e rótulo saem do catálogo, não do
        // que o cliente enviou.
        return Resultado.de(bookingApplicationService.confirmarEm(
                pedido.telefone(), pedido.nome(),
                item.get().id(), item.get().nome(),
                pedido.profissional(), pedido.data(), pedido.horario(),
                item.get().duracao(), pedido.chave()));
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new IllegalArgumentException(mensagem);
        }
    }
}
