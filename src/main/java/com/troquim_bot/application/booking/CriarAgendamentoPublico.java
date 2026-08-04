package com.troquim_bot.application.booking;

import com.troquim_bot.application.business.ResolverNegocioPublicoPorSlug;
import com.troquim_bot.application.catalog.ConfirmarAgendamentoDoCatalogo;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Caso de uso de agendamento público, idempotente por HTTP.
 *
 * Canal NOVO sobre o mesmo caminho oficial de confirmação
 * ({@link ConfirmarAgendamentoDoCatalogo}) — não reimplementa validação de catálogo, conflito
 * de agenda nem persistência. As únicas responsabilidades daqui são:
 * <ol>
 *   <li>resolver o {@link BusinessId} a partir do slug público, pela ÚNICA porta
 *       ({@link ResolverNegocioPublicoPorSlug}) — nunca lendo Repository;</li>
 *   <li>normalizar/validar o telefone do cliente ({@link PhoneNumber});</li>
 *   <li>montar a {@link BookingCommandKey} a partir do cabeçalho {@code Idempotency-Key}
 *       (via {@link BookingCommandKey#deChaveExclusiva}), NÃO da chave do WhatsApp Flow;</li>
 *   <li>consultar um recibo já existente ANTES de validar catálogo ou executar negócio —
 *       o resultado de uma chave já concluída é imutável, mesmo quando o desfecho foi uma
 *       recusa de catálogo (ver {@link RegistrarDesfechoDeBookingSemAgendamento});</li>
 *   <li>quando o catálogo recusa a seleção, registrar o recibo
 *       {@link BookingIdempotencyOutcome#SELECAO_INDISPONIVEL} de forma ATÔMICA, sem criar
 *       nenhuma entidade de negócio — do contrário a chave nunca fica vinculada ao payload
 *       recusado, e um reuso com payload diferente não seria detectado;</li>
 *   <li>traduzir o desfecho para um resultado NEUTRO desta camada — nenhum código HTTP
 *       aparece aqui, essa tradução é do controller.</li>
 * </ol>
 *
 * Deliberadamente SEM {@code @Transactional} NESTA classe: cada escrita (registro de recusa
 * OU o caminho canônico de booking) tem sua PRÓPRIA transação, no serviço que a possui
 * ({@link RegistrarDesfechoDeBookingSemAgendamento} ou
 * {@link BookingApplicationService#confirmarEm}). Envolver esta orquestração numa transação
 * adicional aqui não muda a atomicidade de nenhuma delas, mas somaria confusão sobre quem é
 * a autoridade transacional — que continua sendo só quem escreve.
 */
@Component
public class CriarAgendamentoPublico {

    private static final Logger log = LoggerFactory.getLogger(CriarAgendamentoPublico.class);

    private final ResolverNegocioPublicoPorSlug resolverNegocioPublicoPorSlug;
    private final ConfirmarAgendamentoDoCatalogo confirmarAgendamentoDoCatalogo;
    private final BookingIdempotencyStore idempotencyStore;
    private final RegistrarDesfechoDeBookingSemAgendamento registrarDesfechoDeBookingSemAgendamento;

    public CriarAgendamentoPublico(ResolverNegocioPublicoPorSlug resolverNegocioPublicoPorSlug,
                                   ConfirmarAgendamentoDoCatalogo confirmarAgendamentoDoCatalogo,
                                   BookingIdempotencyStore idempotencyStore,
                                   RegistrarDesfechoDeBookingSemAgendamento registrarDesfechoDeBookingSemAgendamento) {
        this.resolverNegocioPublicoPorSlug = resolverNegocioPublicoPorSlug;
        this.confirmarAgendamentoDoCatalogo = confirmarAgendamentoDoCatalogo;
        this.idempotencyStore = idempotencyStore;
        this.registrarDesfechoDeBookingSemAgendamento = registrarDesfechoDeBookingSemAgendamento;
    }

    /**
     * @param slug             slug público do negócio
     * @param idempotencyKey   valor OPACO do cabeçalho {@code Idempotency-Key}, já validado
     *                         (charset/tamanho) pelo controller
     * @param servico          {@link ServiceId} do catálogo, já parseado pelo controller
     * @param profissional     {@link ProfessionalId} do catálogo, já parseado pelo controller
     * @param data             data solicitada, já parseada pelo controller
     * @param horario          horário solicitado, já parseado pelo controller
     * @param nomeCliente      nome informado pelo cliente (apresentação)
     * @param telefoneCliente  telefone informado pelo cliente, AINDA CRU (normalizado aqui)
     */
    public record Pedido(String slug,
                         String idempotencyKey,
                         ServiceId servico,
                         ProfessionalId profissional,
                         LocalDate data,
                         LocalTime horario,
                         String nomeCliente,
                         String telefoneCliente) {
    }

    public enum Status {
        /** Customer/Reservation/Appointment criados (ou retry idêntico do mesmo comando). */
        CONFIRMADO,
        /** Slug inválido, inexistente, DRAFT, ou negócio não ATIVO — todos indistinguíveis. */
        NEGOCIO_NAO_ENCONTRADO,
        /** Serviço ou profissional nonexistente/inativo/outro-tenant/não habilitado. */
        SELECAO_INDISPONIVEL,
        /** Conflito real de agenda: o horário deixou de estar livre. */
        HORARIO_INDISPONIVEL,
        /** Dados do pedido não puderam ser processados pelo caminho canônico. */
        PEDIDO_INVALIDO,
        /** Mesma Idempotency-Key usada com um payload diferente do da primeira tentativa. */
        CHAVE_IDEMPOTENCIA_REUTILIZADA,
        /** Falha técnica (infraestrutura/persistência) — estado indeterminado, retry seguro. */
        FALHA_TEMPORARIA
    }

    /** Ou o desfecho confirmado (com os dados que a resposta pública expõe), ou o status de erro. */
    public record Resultado(Status status, ServiceId servico, ProfessionalId profissional,
                            LocalDate data, LocalTime horario) {

        public static Resultado confirmado(ServiceId servico, ProfessionalId profissional,
                                           LocalDate data, LocalTime horario) {
            return new Resultado(Status.CONFIRMADO, servico, profissional, data, horario);
        }

        public static Resultado de(Status status) {
            return new Resultado(status, null, null, null, null);
        }

        public boolean isConfirmado() {
            return status == Status.CONFIRMADO;
        }
    }

    public Resultado criar(Pedido pedido) {
        if (pedido == null) {
            return Resultado.de(Status.PEDIDO_INVALIDO);
        }

        Optional<ResolverNegocioPublicoPorSlug.NegocioPublico> negocio =
                resolverNegocioPublicoPorSlug.resolver(pedido.slug());
        if (negocio.isEmpty()) {
            return Resultado.de(Status.NEGOCIO_NAO_ENCONTRADO);
        }
        BusinessId businessId = negocio.get().businessId();

        String telefoneNormalizado;
        try {
            telefoneNormalizado = new PhoneNumber(pedido.telefoneCliente()).getE164();
        } catch (IllegalArgumentException e) {
            return Resultado.de(Status.PEDIDO_INVALIDO);
        }

        if (pedido.servico() == null || pedido.profissional() == null
                || pedido.data() == null || pedido.horario() == null
                || pedido.nomeCliente() == null || pedido.nomeCliente().isBlank()) {
            return Resultado.de(Status.PEDIDO_INVALIDO);
        }

        BookingCommandKey chave;
        try {
            chave = BookingCommandKey.deChaveExclusiva(businessId, pedido.idempotencyKey(),
                    telefoneNormalizado, pedido.servico(), pedido.profissional(),
                    pedido.data(), pedido.horario());
        } catch (IllegalArgumentException e) {
            return Resultado.de(Status.PEDIDO_INVALIDO);
        }

        // Consulta o recibo ANTES de validar catálogo ou tocar em qualquer negócio: o
        // resultado de uma chave já concluída é IMUTÁVEL, mesmo quando foi uma recusa de
        // catálogo — reenviar a mesma chave com o mesmo payload tem de continuar devolvendo
        // o mesmo desfecho, ainda que o catálogo tenha mudado desde então.
        Optional<BookingIdempotencyRecord> recibo;
        try {
            recibo = idempotencyStore.buscar(chave.valor());
        } catch (RuntimeException e) {
            log.error("Falha técnica ao consultar recibo de agendamento público: {}",
                    e.getClass().getSimpleName());
            return Resultado.de(Status.FALHA_TEMPORARIA);
        }

        if (recibo.isPresent()) {
            if (!chave.fingerprint().equals(recibo.get().fingerprint())) {
                return Resultado.de(Status.CHAVE_IDEMPOTENCIA_REUTILIZADA);
            }
            return traduzirDesfecho(recibo.get().status(), pedido);
        }

        ConfirmarAgendamentoDoCatalogo.Pedido pedidoCatalogo = new ConfirmarAgendamentoDoCatalogo.Pedido(
                businessId, pedido.servico(), pedido.profissional(), telefoneNormalizado,
                pedido.nomeCliente(), pedido.data(), pedido.horario(), chave);

        ConfirmarAgendamentoDoCatalogo.Resultado resultado;
        try {
            resultado = confirmarAgendamentoDoCatalogo.confirmar(pedidoCatalogo);
        } catch (BookingCommandKeyReutilizadaException e) {
            return Resultado.de(Status.CHAVE_IDEMPOTENCIA_REUTILIZADA);
        } catch (RuntimeException e) {
            log.error("Falha técnica ao confirmar agendamento público: {}", e.getClass().getSimpleName());
            return Resultado.de(Status.FALHA_TEMPORARIA);
        }

        if (resultado.foiRecusado()) {
            // Recusa de catálogo NUNCA passa por BookingApplicationService — sem este
            // registro explícito, a chave nunca fica vinculada a este payload, e um reuso
            // com payload diferente não seria detectado (violaria "mesma chave, payload
            // diferente = 409", inclusive quando a primeira resposta foi um erro).
            try {
                BookingIdempotencyOutcome desfecho =
                        registrarDesfechoDeBookingSemAgendamento.registrarSelecaoIndisponivel(chave);
                return traduzirDesfecho(desfecho, pedido);
            } catch (BookingCommandKeyReutilizadaException e) {
                return Resultado.de(Status.CHAVE_IDEMPOTENCIA_REUTILIZADA);
            } catch (RuntimeException e) {
                log.error("Falha técnica ao registrar recusa de catálogo: {}", e.getClass().getSimpleName());
                return Resultado.de(Status.FALHA_TEMPORARIA);
            }
        }

        BookingResult booking = resultado.agendamento().orElseThrow();
        return switch (booking.status()) {
            case CONFIRMADO -> Resultado.confirmado(pedido.servico(), pedido.profissional(),
                    pedido.data(), pedido.horario());
            case INDISPONIVEL -> Resultado.de(Status.HORARIO_INDISPONIVEL);
            case INVALIDO -> Resultado.de(Status.PEDIDO_INVALIDO);
            case FALHA_TECNICA -> Resultado.de(Status.FALHA_TEMPORARIA);
            // Regra do MVP ("uma base, um agendamento") não incide sobre deChaveExclusiva:
            // o valor da chave já é fixo por (businessId, Idempotency-Key), então o desvio
            // de base nunca dispara para este caminho. Mapeado por segurança, sem detalhar.
            case SESSAO_JA_CONFIRMADA -> Resultado.de(Status.FALHA_TEMPORARIA);
        };
    }

    /** Traduz um desfecho NEUTRO já persistido (recibo) para o resultado público desta camada. */
    private Resultado traduzirDesfecho(BookingIdempotencyOutcome desfecho, Pedido pedido) {
        return switch (desfecho) {
            // Fingerprint já conferido por quem chama: os dados do pedido atual SÃO os
            // mesmos que produziram este recibo.
            case CONFIRMADO -> Resultado.confirmado(pedido.servico(), pedido.profissional(),
                    pedido.data(), pedido.horario());
            case HORARIO_INDISPONIVEL -> Resultado.de(Status.HORARIO_INDISPONIVEL);
            case SELECAO_INDISPONIVEL -> Resultado.de(Status.SELECAO_INDISPONIVEL);
            case PEDIDO_INVALIDO -> Resultado.de(Status.PEDIDO_INVALIDO);
            // SESSAO_JA_CONFIRMADA não incide sobre deChaveExclusiva (ver acima); FALHA_TECNICA
            // nunca é gravada. Ambos mapeados por segurança, sem detalhar ao cliente.
            case SESSAO_JA_CONFIRMADA, FALHA_TECNICA -> Resultado.de(Status.FALHA_TEMPORARIA);
        };
    }
}
