package com.troquim_bot.conversation;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.application.catalog.ConfirmarAgendamentoDoCatalogo;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.availability.RelogioDoNegocio;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adaptador da conversa textual para os casos de uso CANONICOS de catalogo e agenda.
 *
 * A conversa interpreta texto; nenhuma regra de disponibilidade ou identidade de servico
 * vive aqui. Toda decisao de negocio e delegada para ConsultarCatalogo,
 * AvailabilityApplicationService e ConfirmarAgendamentoDoCatalogo.
 *
 * O TenantProvider e resolvido nesta borda do canal. Daqui para baixo o BusinessId segue
 * explicito para os casos de uso.
 */
@Service
public class ConversationBookingGateway {

    public enum Status {
        OK,
        CATALOGO_NAO_CONFIGURADO,
        SERVICO_INDISPONIVEL,
        PROFISSIONAL_AMBIGUO,
        DIA_INVALIDO,
        HORARIO_INVALIDO,
        HORARIO_INDISPONIVEL,
        FALHA_TECNICA
    }

    public record Servico(String nome) {
    }

    public record ConsultaHorarios(Status status, String servico, String dia,
                                   List<LocalTime> horarios) {

        public static ConsultaHorarios erro(Status status, String servico, String dia) {
            return new ConsultaHorarios(status, servico, dia, List.of());
        }

        public boolean ok() {
            return status == Status.OK;
        }
    }

    public record Confirmacao(Status status, BookingResult resultado,
                              String servico, String dia, String horario) {

        public boolean confirmada() {
            return status == Status.OK && resultado != null && resultado.isConfirmado();
        }
    }

    private record ServicoResolvido(Status status,
                                    ConsultarCatalogo.ItemDeCatalogo item,
                                    ProfessionalId profissional) {

        boolean ok() {
            return status == Status.OK && item != null && profissional != null;
        }
    }

    private final TenantProvider tenantProvider;
    private final ConsultarCatalogo consultarCatalogo;
    private final AvailabilityApplicationService availabilityApplicationService;
    private final ConfirmarAgendamentoDoCatalogo confirmarAgendamento;
    private final RelogioDoNegocio relogio;
    private final TimeInputParser timeInputParser;

    public ConversationBookingGateway(TenantProvider tenantProvider,
                                      ConsultarCatalogo consultarCatalogo,
                                      AvailabilityApplicationService availabilityApplicationService,
                                      ConfirmarAgendamentoDoCatalogo confirmarAgendamento,
                                      RelogioDoNegocio relogio) {
        this.tenantProvider = tenantProvider;
        this.consultarCatalogo = consultarCatalogo;
        this.availabilityApplicationService = availabilityApplicationService;
        this.confirmarAgendamento = confirmarAgendamento;
        this.relogio = relogio;
        this.timeInputParser = new TimeInputParser();
    }

    public List<Servico> listarServicos() {
        BusinessId businessId = tenantProvider.currentBusinessId();
        return consultarCatalogo.consultar(businessId).itens().stream()
                .map(item -> new Servico(item.nome()))
                .toList();
    }

    public Optional<String> nomeCanonicoDoServico(String nomeInterpretado) {
        ServicoResolvido resolvido = resolverServico(nomeInterpretado);
        return resolvido.ok() ? Optional.of(resolvido.item().nome()) : Optional.empty();
    }

    public Status statusDoServico(String nomeInterpretado) {
        return resolverServico(nomeInterpretado).status();
    }

    public ConsultaHorarios consultarHorarios(String nomeInterpretado, String diaInformado) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        ServicoResolvido servico = resolverServico(nomeInterpretado);
        if (!servico.ok()) {
            return ConsultaHorarios.erro(servico.status(), nomeInterpretado, diaInformado);
        }

        Optional<LocalDate> data = resolverData(diaInformado);
        if (data.isEmpty()) {
            return ConsultaHorarios.erro(Status.DIA_INVALIDO, servico.item().nome(), diaInformado);
        }

        List<LocalTime> horarios = availabilityApplicationService.horariosLivres(
                businessId, servico.item().id(), servico.profissional(), data.get());

        return new ConsultaHorarios(Status.OK, servico.item().nome(), diaInformado, horarios);
    }

    public Confirmacao confirmar(String commandBase,
                                 String telefone,
                                 String nomeCliente,
                                 String nomeInterpretado,
                                 String diaInformado,
                                 String horarioInformado) {
        BusinessId businessId = tenantProvider.currentBusinessId();

        ServicoResolvido servico = resolverServico(nomeInterpretado);
        if (!servico.ok()) {
            return new Confirmacao(servico.status(), null, nomeInterpretado, diaInformado, horarioInformado);
        }

        Optional<LocalDate> data = resolverData(diaInformado);
        if (data.isEmpty()) {
            return new Confirmacao(Status.DIA_INVALIDO, null,
                    servico.item().nome(), diaInformado, horarioInformado);
        }

        Optional<LocalTime> horario = timeInputParser.parse(horarioInformado);
        if (horario.isEmpty()) {
            return new Confirmacao(Status.HORARIO_INVALIDO, null,
                    servico.item().nome(), diaInformado, horarioInformado);
        }

        // A leitura anterior serve somente para UX. A confirmacao oficial revalida o mesmo
        // slot dentro da secao critica antes de qualquer escrita.
        boolean livre = availabilityApplicationService.estaLivre(
                businessId, servico.item().id(), servico.profissional(), data.get(), horario.get());
        if (!livre) {
            return new Confirmacao(Status.HORARIO_INDISPONIVEL, null,
                    servico.item().nome(), diaInformado, horarioInformado);
        }

        BookingCommandKey chave = BookingCommandKey.de(
                commandBase,
                businessId.getValue(),
                telefone,
                servico.item().id(),
                servico.profissional(),
                data.get(),
                horario.get());

        try {
            ConfirmarAgendamentoDoCatalogo.Resultado confirmado = confirmarAgendamento.confirmar(
                    new ConfirmarAgendamentoDoCatalogo.Pedido(
                            businessId,
                            servico.item().id(),
                            servico.profissional(),
                            telefone,
                            nomeCliente,
                            data.get(),
                            horario.get(),
                            chave));

            if (confirmado.foiRecusado()) {
                Status status = confirmado.recusa().orElse(null)
                        == ConfirmarAgendamentoDoCatalogo.Recusa.SERVICO_INDISPONIVEL
                        ? Status.SERVICO_INDISPONIVEL
                        : Status.PROFISSIONAL_AMBIGUO;
                return new Confirmacao(status, null,
                        servico.item().nome(), diaInformado, horarioInformado);
            }

            BookingResult resultado = confirmado.agendamento().orElse(null);
            if (resultado == null) {
                return new Confirmacao(Status.FALHA_TECNICA, BookingResult.falhaTecnica(),
                        servico.item().nome(), diaInformado, horarioInformado);
            }
            if (resultado.isConfirmado()) {
                return new Confirmacao(Status.OK, resultado,
                        servico.item().nome(), diaInformado, horarioInformado);
            }
            if (resultado.isConflito()) {
                return new Confirmacao(Status.HORARIO_INDISPONIVEL, resultado,
                        servico.item().nome(), diaInformado, horarioInformado);
            }
            if (resultado.isFalhaTecnica()) {
                return new Confirmacao(Status.FALHA_TECNICA, resultado,
                        servico.item().nome(), diaInformado, horarioInformado);
            }
            return new Confirmacao(Status.HORARIO_INVALIDO, resultado,
                    servico.item().nome(), diaInformado, horarioInformado);
        } catch (RuntimeException falhaTecnica) {
            return new Confirmacao(Status.FALHA_TECNICA, BookingResult.falhaTecnica(),
                    servico.item().nome(), diaInformado, horarioInformado);
        }
    }

    public boolean horarioPertenceAOferta(String nomeInterpretado, String diaInformado,
                                          String horarioInformado) {
        Optional<LocalTime> horario = timeInputParser.parse(horarioInformado);
        if (horario.isEmpty()) {
            return false;
        }
        ConsultaHorarios consulta = consultarHorarios(nomeInterpretado, diaInformado);
        return consulta.ok() && consulta.horarios().contains(horario.get());
    }

    public static String formatarHorario(LocalTime horario) {
        if (horario == null) {
            return "";
        }
        if (horario.getMinute() == 0) {
            return horario.getHour() + "h";
        }
        return horario.getHour() + ":" + String.format("%02d", horario.getMinute());
    }

    private ServicoResolvido resolverServico(String nomeInterpretado) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        ConsultarCatalogo.Catalogo catalogo = consultarCatalogo.consultar(businessId);
        if (catalogo.naoConfigurado()) {
            return new ServicoResolvido(Status.CATALOGO_NAO_CONFIGURADO, null, null);
        }

        List<ConsultarCatalogo.ItemDeCatalogo> candidatos = catalogo.itens().stream()
                .filter(item -> mesmoServico(item.nome(), nomeInterpretado))
                .toList();
        if (candidatos.size() != 1) {
            return new ServicoResolvido(Status.SERVICO_INDISPONIVEL, null, null);
        }

        ConsultarCatalogo.ItemDeCatalogo item = candidatos.get(0);
        if (item.profissionais().size() != 1) {
            // A conversa textual ainda nao coleta profissional. Nunca escolhemos o primeiro
            // silenciosamente: isso seria uma decisao de negocio fabricada pelo canal.
            return new ServicoResolvido(Status.PROFISSIONAL_AMBIGUO, item, null);
        }

        return new ServicoResolvido(Status.OK, item, item.profissionais().get(0).id());
    }

    private Optional<LocalDate> resolverData(String diaInformado) {
        String dia = normalizar(diaInformado);
        LocalDate hoje = relogio.hoje();

        if ("hoje".equals(dia)) {
            return Optional.of(hoje);
        }
        if ("amanha".equals(dia)) {
            return Optional.of(hoje.plusDays(1));
        }

        DayOfWeek alvo = switch (dia) {
            case "segunda" -> DayOfWeek.MONDAY;
            case "terca" -> DayOfWeek.TUESDAY;
            case "quarta" -> DayOfWeek.WEDNESDAY;
            case "quinta" -> DayOfWeek.THURSDAY;
            case "sexta" -> DayOfWeek.FRIDAY;
            case "sabado" -> DayOfWeek.SATURDAY;
            case "domingo" -> DayOfWeek.SUNDAY;
            default -> null;
        };
        if (alvo != null) {
            return Optional.of(hoje.with(TemporalAdjusters.nextOrSame(alvo)));
        }

        try {
            return Optional.of(LocalDate.parse(diaInformado));
        } catch (RuntimeException invalido) {
            return Optional.empty();
        }
    }

    private static boolean mesmoServico(String catalogo, String interpretado) {
        String a = singularSimples(normalizar(catalogo));
        String b = singularSimples(normalizar(interpretado));
        return !a.isBlank() && a.equals(b);
    }

    private static String singularSimples(String valor) {
        if (valor.endsWith("s") && valor.length() > 3) {
            return valor.substring(0, valor.length() - 1);
        }
        return valor;
    }

    private static String normalizar(String texto) {
        String base = texto == null ? "" : texto;
        String semAcentos = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
