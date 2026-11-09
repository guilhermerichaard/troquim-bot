package com.troquim_bot.conversation;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.appointment.AppointmentRescheduleApplicationService;
import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.booking.BookingCommandKey;
import com.troquim_bot.application.booking.BookingResult;
import com.troquim_bot.application.catalog.ConfirmarAgendamentoDoCatalogo;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.language.ServiceInterpretationLearningStore;
import com.troquim_bot.application.service.ServiceApplicationService;
import com.troquim_bot.application.waitlist.WaitlistApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.appointment.AppointmentStatus;
import com.troquim_bot.availability.RelogioDoNegocio;
import com.troquim_bot.automation.BookingAutomationPolicyRepository;
import com.troquim_bot.automation.UpsellApplicationService;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.waitlist.WaitlistEntry;

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

    public record Servico(String nome,
                          String descricao,
                          long duracaoMinutos,
                          Double preco,
                          String moeda) {

        public Servico(String nome) {
            this(nome, "", 0, null, null);
        }

        public String detalhes() {
            java.util.ArrayList<String> partes = new java.util.ArrayList<>();
            if (duracaoMinutos > 0) {
                if (duracaoMinutos >= 60 && duracaoMinutos % 60 == 0) {
                    partes.add((duracaoMinutos / 60) + "h");
                } else if (duracaoMinutos > 60) {
                    partes.add((duracaoMinutos / 60) + "h" + (duracaoMinutos % 60));
                } else {
                    partes.add(duracaoMinutos + " min");
                }
            }
            if (preco != null) {
                String simbolo = "BRL".equalsIgnoreCase(moeda) ? "R$" : (moeda == null ? "" : moeda);
                partes.add(simbolo + " " + String.format(java.util.Locale.of("pt", "BR"), "%.2f", preco));
            }
            return String.join(" · ", partes);
        }
    }

    /**
     * Projecao de leitura para a conversa. O Appointment persistido continua sendo a
     * autoridade; este record so carrega os campos necessarios para apresentacao.
     */
    public record Agendamento(String servico, LocalDate data, LocalTime horario,
                              boolean confirmado) {
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

    public record SlotSugerido(String servico, LocalDate data, LocalTime horario) {
    }

    public record Recomendacao(Status status, String servico, List<SlotSugerido> slots,
                               boolean repetindoHistorico) {
        public Recomendacao {
            slots = slots == null ? List.of() : List.copyOf(slots);
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

    public record WaitlistClaim(Status status,
                                String servico,
                                LocalDate data,
                                LocalTime horario,
                                String nomeCliente) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    public enum ReminderActionStatus {
        OK,
        NAO_ENCONTRADO,
        CANCELAMENTO_BLOQUEADO,
        HORARIO_INDISPONIVEL,
        FALHA_TECNICA
    }

    public record ReminderActionResult(ReminderActionStatus status, String message) {
        public boolean ok() { return status == ReminderActionStatus.OK; }
    }

    public record ReminderRescheduleOptions(ReminderActionStatus status,
                                            String serviceName,
                                            List<SlotSugerido> slots) {
        public ReminderRescheduleOptions {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
        public boolean ok() { return status == ReminderActionStatus.OK; }
    }

    public record UpsellOffer(String serviceId,
                              String serviceName,
                              Double price,
                              String professionalId,
                              LocalDate date,
                              LocalTime time) {
    }

    private record ServicoIdentificado(Status status,
                                       ConsultarCatalogo.ItemDeCatalogo item) {
        boolean ok() {
            return status == Status.OK && item != null;
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
    private final AppointmentApplicationService appointmentApplicationService;
    private final CustomerProfileService customerProfileService;
    private final ServiceApplicationService serviceApplicationService;
    private final ServiceInterpretationLearningStore interpretationLearningStore;
    private final ConfirmarAgendamentoDoCatalogo confirmarAgendamento;
    private final RelogioDoNegocio relogio;
    private final WaitlistApplicationService waitlistApplicationService;
    private final BookingAutomationPolicyRepository automationPolicies;
    private final AppointmentRescheduleApplicationService rescheduler;
    private final UpsellApplicationService upsells;
    private final TimeInputParser timeInputParser;

    public ConversationBookingGateway(TenantProvider tenantProvider,
                                      ConsultarCatalogo consultarCatalogo,
                                      AvailabilityApplicationService availabilityApplicationService,
                                      AppointmentApplicationService appointmentApplicationService,
                                      CustomerProfileService customerProfileService,
                                      ServiceApplicationService serviceApplicationService,
                                      ServiceInterpretationLearningStore interpretationLearningStore,
                                      ConfirmarAgendamentoDoCatalogo confirmarAgendamento,
                                      RelogioDoNegocio relogio,
                                      WaitlistApplicationService waitlistApplicationService,
                                      BookingAutomationPolicyRepository automationPolicies,
                                      AppointmentRescheduleApplicationService rescheduler,
                                      UpsellApplicationService upsells) {
        this.tenantProvider = tenantProvider;
        this.consultarCatalogo = consultarCatalogo;
        this.availabilityApplicationService = availabilityApplicationService;
        this.appointmentApplicationService = appointmentApplicationService;
        this.customerProfileService = customerProfileService;
        this.serviceApplicationService = serviceApplicationService;
        this.interpretationLearningStore = interpretationLearningStore;
        this.confirmarAgendamento = confirmarAgendamento;
        this.relogio = relogio;
        this.waitlistApplicationService = waitlistApplicationService;
        this.automationPolicies = automationPolicies;
        this.rescheduler = rescheduler;
        this.upsells = upsells;
        this.timeInputParser = new TimeInputParser();
    }

    public List<Servico> listarServicos() {
        BusinessId businessId = tenantProvider.currentBusinessId();
        return consultarCatalogo.consultar(businessId).itens().stream()
                .map(item -> new Servico(
                        item.nome(),
                        item.descricao(),
                        item.duracao().toMinutes(),
                        item.preco().map(valor -> valor.getAmount().doubleValue()).orElse(null),
                        item.preco().map(valor -> valor.getCurrency()).orElse(null)))
                .toList();
    }

    public Optional<String> nomeCanonicoDoServico(String nomeInterpretado) {
        ServicoIdentificado identificado = identificarServico(nomeInterpretado);
        return identificado.ok() ? Optional.of(identificado.item().nome()) : Optional.empty();
    }

    /**
     * Registra uma correcao somente depois da confirmacao explicita do cliente.
     * O ServiceId e resolvido do catalogo atual; nenhuma string sugerida vira regra por si.
     */
    public void aprenderCorrecaoDeServico(String entradaOriginal, String nomeCanonicoConfirmado) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        String entrada = normalizar(entradaOriginal);
        if (entrada.isBlank()) {
            return;
        }

        consultarCatalogo.consultar(businessId).itens().stream()
                .filter(item -> item.nome().equalsIgnoreCase(nomeCanonicoConfirmado))
                .findFirst()
                .ifPresent(item -> interpretationLearningStore.aprender(
                        businessId, chaveDeCorrecao(item.nome(), entrada), item.id()));
    }

    /**
     * Sugere um unico servico do catalogo quando a entrada parece conter um erro de
     * digitacao. A sugestao nunca decide o booking: Conversation pede confirmacao ao
     * cliente antes de gravar o servico no draft.
     */
    public Optional<String> sugerirServico(String entrada) {
        String texto = normalizar(entrada);
        if (texto.isBlank()) {
            return Optional.empty();
        }

        BusinessId businessId = tenantProvider.currentBusinessId();
        List<ConsultarCatalogo.ItemDeCatalogo> itens = consultarCatalogo.consultar(businessId).itens();

        record Candidato(String nome, int distancia) {}
        List<Candidato> candidatos = itens.stream()
                .map(item -> new Candidato(item.nome(), distanciaParaEntrada(item.nome(), texto)))
                .filter(candidato -> candidato.distancia() <= limiteDeCorrecao(candidato.nome()))
                .sorted(java.util.Comparator.comparingInt(Candidato::distancia)
                        .thenComparing(Candidato::nome))
                .toList();

        if (candidatos.isEmpty()) {
            return Optional.empty();
        }
        if (candidatos.size() > 1
                && candidatos.get(0).distancia() == candidatos.get(1).distancia()) {
            return Optional.empty();
        }
        return Optional.of(candidatos.get(0).nome());
    }

    public Status statusDoServico(String nomeInterpretado) {
        return identificarServico(nomeInterpretado).status();
    }

    /**
     * Turbo Booking: converte uma preferência já interpretada em slots reais.
     *
     * A intenção nunca cria disponibilidade. Cada candidato vem exclusivamente do caso
     * de uso canônico de disponibilidade e é apenas ordenado por preferência/compactação.
     */
    public Recomendacao recomendar(String telefone, BookingIntent intent) {
        if (intent == null) {
            return new Recomendacao(Status.SERVICO_INDISPONIVEL, "", List.of(), false);
        }

        BusinessId businessId = tenantProvider.currentBusinessId();
        ServicoIdentificado servico = intent.sameAsUsual()
                ? identificarServicoDoHistorico(businessId, telefone)
                : identificarServico(intent.serviceQuery());

        if (!servico.ok()) {
            return new Recomendacao(servico.status(),
                    servico.item() == null ? "" : servico.item().nome(),
                    List.of(), intent.sameAsUsual());
        }

        List<LocalDate> datas;
        if (intent.hasDayPreference()) {
            Optional<LocalDate> data = resolverData(intent.dayQuery());
            if (data.isEmpty()) {
                return new Recomendacao(Status.DIA_INVALIDO, servico.item().nome(),
                        List.of(), intent.sameAsUsual());
            }
            datas = List.of(data.get());
        } else {
            LocalDate hoje = relogio.hoje();
            datas = java.util.stream.IntStream.rangeClosed(0, 6)
                    .mapToObj(hoje::plusDays)
                    .toList();
        }

        List<SlotSugerido> slots = rankearSlotsUnicos(
                servico.item(),
                candidatosValidos(businessId, servico.item(), datas, intent),
                intent.targetTime(),
                3);

        return new Recomendacao(
                slots.isEmpty() ? Status.HORARIO_INDISPONIVEL : Status.OK,
                servico.item().nome(),
                slots,
                intent.sameAsUsual());
    }

    public Recomendacao recomendarHorariosDoDia(String nomeInterpretado,
                                               String diaInformado,
                                               int limite) {
        if (limite <= 0) {
            return new Recomendacao(Status.HORARIO_INVALIDO, "", List.of(), false);
        }

        BusinessId businessId = tenantProvider.currentBusinessId();
        ServicoIdentificado servico = identificarServico(nomeInterpretado);
        if (!servico.ok()) {
            return new Recomendacao(servico.status(),
                    servico.item() == null ? "" : servico.item().nome(), List.of(), false);
        }

        Optional<LocalDate> data = resolverData(diaInformado);
        if (data.isEmpty()) {
            return new Recomendacao(Status.DIA_INVALIDO, servico.item().nome(), List.of(), false);
        }

        List<SlotSugerido> slots = rankearSlotsUnicos(
                servico.item(),
                candidatosValidos(businessId, servico.item(), List.of(data.get()), null),
                null,
                limite);

        return new Recomendacao(
                slots.isEmpty() ? Status.HORARIO_INDISPONIVEL : Status.OK,
                servico.item().nome(),
                slots,
                false);
    }

    public Recomendacao alternativasParaConflito(String nomeInterpretado,
                                                   String diaInformado,
                                                   String horarioInformado) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        ServicoIdentificado servico = identificarServico(nomeInterpretado);
        if (!servico.ok()) {
            return new Recomendacao(servico.status(),
                    servico.item() == null ? "" : servico.item().nome(), List.of(), false);
        }

        Optional<LocalDate> data = resolverData(diaInformado);
        Optional<LocalTime> alvo = timeInputParser.parse(horarioInformado);
        if (data.isEmpty() || alvo.isEmpty()) {
            return new Recomendacao(Status.HORARIO_INVALIDO, servico.item().nome(), List.of(), false);
        }

        List<SlotSugerido> slots = rankearSlotsUnicos(
                servico.item(),
                candidatosValidos(businessId, servico.item(), List.of(data.get()), null),
                alvo.get(),
                3);

        return new Recomendacao(
                slots.isEmpty() ? Status.HORARIO_INDISPONIVEL : Status.OK,
                servico.item().nome(),
                slots,
                false);
    }

    private ServicoIdentificado identificarServicoDoHistorico(BusinessId businessId, String telefone) {
        return customerProfileService.localizarIdOficial(businessId, telefone)
                .flatMap(customerId -> appointmentApplicationService
                        .listarHistoricoPorCliente(customerId).stream()
                        .filter(appointment -> appointment.pertenceAoTenant(businessId))
                        .findFirst())
                .flatMap(appointment -> consultarCatalogo.porServico(businessId, appointment.getServiceId()))
                .map(item -> new ServicoIdentificado(Status.OK, item))
                .orElseGet(() -> new ServicoIdentificado(Status.SERVICO_INDISPONIVEL, null));
    }

    private int gapAdjacenteMinutos(BusinessId businessId,
                                    ProfessionalId profissional,
                                    LocalDate data,
                                    LocalTime horario,
                                    java.time.Duration duracaoServico) {
        int melhor = Integer.MAX_VALUE;
        LocalTime fimCandidato = horario.plus(duracaoServico);
        for (Appointment appointment : appointmentApplicationService.listarAtivos(businessId)) {
            if (!appointment.getProfessionalId().equals(profissional)
                    || !appointment.getDate().equals(data)) {
                continue;
            }
            long ateInicio = Math.abs(java.time.temporal.ChronoUnit.MINUTES.between(
                    appointment.getEndTime(), horario));
            long ateFim = Math.abs(java.time.temporal.ChronoUnit.MINUTES.between(
                    fimCandidato, appointment.getStartTime()));
            melhor = (int) Math.min(melhor, Math.min(ateInicio, ateFim));
        }
        return melhor == Integer.MAX_VALUE ? 24 * 60 : melhor;
    }

    public ConsultaHorarios consultarHorarios(String nomeInterpretado, String diaInformado) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        ServicoIdentificado servico = identificarServico(nomeInterpretado);
        if (!servico.ok()) {
            return ConsultaHorarios.erro(servico.status(), nomeInterpretado, diaInformado);
        }

        Optional<LocalDate> data = resolverData(diaInformado);
        if (data.isEmpty()) {
            return ConsultaHorarios.erro(Status.DIA_INVALIDO, servico.item().nome(), diaInformado);
        }

        List<LocalTime> horarios = servico.item().profissionais().stream()
                .flatMap(profissional -> availabilityApplicationService.horariosLivres(
                        businessId, servico.item().id(), profissional.id(), data.get()).stream())
                .distinct()
                .sorted()
                .toList();

        // Consulta bem-sucedida pode legitimamente devolver zero horários; isso não é
        // falha técnica. A camada de conversa decide como oferecer outro dia/waitlist.
        return new ConsultaHorarios(
                Status.OK,
                servico.item().nome(),
                diaInformado,
                horarios);
    }

    /**
     * Registra interesse em um slot futuro. A waitlist não reserva nada e nunca pula a
     * confirmação canônica quando o horário reaparece.
     */
    public boolean entrarNaEspera(String telefone,
                                  String nomeServico,
                                  String diaInformado,
                                  LocalTime earliestTime,
                                  LocalTime latestTime) {
        if (waitlistApplicationService == null) {
            return false;
        }

        BusinessId businessId = tenantProvider.currentBusinessId();
        ServicoResolvido servico = resolverServico(nomeServico);
        if (!servico.ok()) {
            return false;
        }

        LocalDate data = null;
        if (diaInformado != null && !diaInformado.isBlank()) {
            data = resolverData(diaInformado).orElse(null);
            if (data == null) {
                return false;
            }
        }

        try {
            waitlistApplicationService.join(
                    businessId,
                    telefone,
                    servico.item().id(),
                    servico.profissional(),
                    data,
                    earliestTime,
                    latestTime);
            return true;
        } catch (RuntimeException invalido) {
            return false;
        }
    }

    /**
     * Valida o clique do template de waitlist contra a entrada persistida, o catálogo
     * atual e a disponibilidade atual. O payload do botão nunca vira autoridade.
     */
    public WaitlistClaim prepararResgateWaitlist(String telefone,
                                                 java.util.UUID waitlistId,
                                                 LocalDate data,
                                                 LocalTime horario) {
        if (waitlistApplicationService == null || waitlistId == null || data == null || horario == null) {
            return new WaitlistClaim(Status.FALHA_TECNICA, "", data, horario, null);
        }

        WaitlistEntry entry = waitlistApplicationService.claim(waitlistId, telefone).orElse(null);
        if (entry == null) {
            return new WaitlistClaim(Status.SERVICO_INDISPONIVEL, "", data, horario, null);
        }

        BusinessId businessId = tenantProvider.currentBusinessId();
        if (!entry.getBusinessId().equals(businessId)
                || (entry.getRequestedDate() != null && !entry.getRequestedDate().equals(data))
                || (entry.getEarliestTime() != null && horario.isBefore(entry.getEarliestTime()))
                || (entry.getLatestTime() != null && horario.isAfter(entry.getLatestTime()))) {
            return new WaitlistClaim(Status.HORARIO_INVALIDO, "", data, horario, null);
        }

        var item = consultarCatalogo.porServico(businessId, entry.getServiceId()).orElse(null);
        if (item == null || item.profissionais().stream()
                .noneMatch(p -> p.id().equals(entry.getProfessionalId()))) {
            return new WaitlistClaim(Status.SERVICO_INDISPONIVEL, "", data, horario, null);
        }

        boolean livre = availabilityApplicationService.estaLivre(
                businessId, entry.getServiceId(), entry.getProfessionalId(), data, horario);
        if (!livre) {
            waitlistApplicationService.reactivate(waitlistId, telefone);
            return new WaitlistClaim(
                    Status.HORARIO_INDISPONIVEL, item.nome(), data, horario, null);
        }

        String nome = customerProfileService.nomePreferido(telefone).orElse(null);
        return new WaitlistClaim(Status.OK, item.nome(), data, horario, nome);
    }

    public Confirmacao confirmar(String commandBase,
                                 String telefone,
                                 String nomeCliente,
                                 String nomeInterpretado,
                                 String diaInformado,
                                 String horarioInformado) {
        BusinessId businessId = tenantProvider.currentBusinessId();

        ServicoIdentificado servico = identificarServico(nomeInterpretado);
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

        // "Qualquer profissional" e uma policy explicita: entre profissionais capazes e
        // realmente livres para o slot escolhido, preferimos o menor buraco adjacente e
        // usamos o id como desempate estavel. A confirmacao oficial ainda revalida dentro
        // da secao critica antes de qualquer escrita.
        Optional<ProfessionalId> profissional = profissionalParaSlot(
                businessId, servico.item(), data.get(), horario.get());
        if (profissional.isEmpty()) {
            return new Confirmacao(Status.HORARIO_INDISPONIVEL, null,
                    servico.item().nome(), diaInformado, horarioInformado);
        }

        BookingCommandKey chave = BookingCommandKey.de(
                commandBase,
                businessId.getValue(),
                telefone,
                servico.item().id(),
                profissional.get(),
                data.get(),
                horario.get());

        try {
            ConfirmarAgendamentoDoCatalogo.Resultado confirmado = confirmarAgendamento.confirmar(
                    new ConfirmarAgendamentoDoCatalogo.Pedido(
                            businessId,
                            servico.item().id(),
                            profissional.get(),
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

    /**
     * Le os agendamentos ATIVOS reais do cliente. ConversationState/draft nunca participa
     * desta consulta: depois do commit, a fonte da verdade e Appointment.
     */
    public List<Agendamento> listarAgendamentosAtivos(String telefone) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        return agendamentosAtivosDoCliente(businessId, telefone).stream()
                .map(appointment -> paraApresentacao(businessId, appointment))
                .toList();
    }

    /**
     * Cancela o agendamento pelo indice da lista retornada por listarAgendamentosAtivos.
     * A decisao de transicao PENDENTE/CONFIRMADO -> CANCELADO continua no agregado
     * Appointment, via AppointmentApplicationService.
     */
    public Optional<Agendamento> cancelarAgendamentoAtivo(String telefone, int indice) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        List<Appointment> ativos = agendamentosAtivosDoCliente(businessId, telefone);
        if (indice < 0 || indice >= ativos.size()) {
            return Optional.empty();
        }

        Appointment alvo = ativos.get(indice);
        Agendamento apresentacao = paraApresentacao(businessId, alvo);
        appointmentApplicationService.cancelarAgendamento(alvo.getId());

        if (waitlistApplicationService != null) {
            waitlistApplicationService.slotReleased(
                    businessId,
                    alvo.getServiceId(),
                    alvo.getProfessionalId(),
                    apresentacao.servico(),
                    alvo.getDate(),
                    alvo.getStartTime());
        }
        return Optional.of(apresentacao);
    }

    private List<Appointment> agendamentosAtivosDoCliente(BusinessId businessId, String telefone) {
        LocalDate hoje = relogio.hoje();
        return customerProfileService.localizarIdOficial(businessId, telefone)
                .map(appointmentApplicationService::listarAtivosPorCliente)
                .orElse(List.of()).stream()
                .filter(appointment -> appointment.pertenceAoTenant(businessId))
                .filter(appointment -> !appointment.getDate().isBefore(hoje))
                .toList();
    }

    private Agendamento paraApresentacao(BusinessId businessId, Appointment appointment) {
        String nomeServico = serviceApplicationService.buscarPorId(appointment.getServiceId())
                .map(com.troquim_bot.service.Service::getNome)
                .orElse("Servico legado");

        return new Agendamento(
                nomeServico,
                appointment.getDate(),
                appointment.getStartTime(),
                appointment.getStatus() == AppointmentStatus.CONFIRMADO);
    }

    public Optional<UpsellOffer> recomendarUpsell(String telefone,
                                                      String nomeServicoBase,
                                                      String diaInformado,
                                                      String horarioInformado) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        ServicoIdentificado base = identificarServico(nomeServicoBase);
        Optional<LocalDate> data = resolverData(diaInformado);
        Optional<LocalTime> horario = timeInputParser.parse(horarioInformado);
        if (!base.ok() || data.isEmpty() || horario.isEmpty()) {
            return Optional.empty();
        }

        // Depois do commit do booking, Appointment é a única fonte correta para descobrir
        // quem realmente atenderá. Não recalculamos profissional a partir do catálogo.
        Optional<Appointment> appointment = agendamentosAtivosDoCliente(businessId, telefone).stream()
                .filter(a -> a.getServiceId().equals(base.item().id()))
                .filter(a -> a.getDate().equals(data.get()))
                .filter(a -> a.getStartTime().equals(horario.get()))
                .findFirst();
        if (appointment.isEmpty()) {
            return Optional.empty();
        }

        return upsells.recommend(
                        businessId,
                        base.item().id(),
                        appointment.get().getProfessionalId(),
                        data.get(),
                        horario.get())
                .map(offer -> new UpsellOffer(
                        offer.serviceId().getValue().toString(),
                        offer.serviceName(),
                        offer.price(),
                        offer.professionalId().getValue().toString(),
                        offer.date(),
                        offer.startTime()));
    }

    public ReminderActionResult confirmarUpsell(String telefone,
                                                 String serviceId,
                                                 String professionalId,
                                                 LocalDate date,
                                                 LocalTime time) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        try {
            ServiceId service = ServiceId.from(java.util.UUID.fromString(serviceId));
            ProfessionalId professional =
                    ProfessionalId.from(java.util.UUID.fromString(professionalId));
            String nome = customerProfileService.nomePreferido(telefone).orElse("Cliente");
            String raw = telefone + "|" + serviceId + "|" + professionalId + "|" + date + "|" + time;
            String idem = "upsell-" + java.util.UUID.nameUUIDFromBytes(
                    raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            BookingCommandKey key = BookingCommandKey.deChaveExclusiva(
                    businessId, idem, telefone, service, professional, date, time);

            var result = confirmarAgendamento.confirmar(
                    new ConfirmarAgendamentoDoCatalogo.Pedido(
                            businessId, service, professional, telefone, nome, date, time, key));

            BookingResult booking = result.agendamento().orElse(null);
            if (result.foiRecusado() || booking == null) {
                return new ReminderActionResult(
                        ReminderActionStatus.HORARIO_INDISPONIVEL,
                        "Esse adicional não está mais disponível nesse horário.");
            }
            if (booking.isConfirmado()) {
                return new ReminderActionResult(
                        ReminderActionStatus.OK,
                        "Adicional incluído no seu atendimento.");
            }
            if (booking.isConflito()) {
                return new ReminderActionResult(
                        ReminderActionStatus.HORARIO_INDISPONIVEL,
                        "Esse adicional não cabe mais nesse horário.");
            }
            return new ReminderActionResult(
                    ReminderActionStatus.FALHA_TECNICA,
                    "Não consegui incluir o adicional agora.");
        } catch (RuntimeException failure) {
            return new ReminderActionResult(
                    ReminderActionStatus.FALHA_TECNICA,
                    "Não consegui incluir o adicional agora.");
        }
    }

    public ReminderActionResult confirmarLembrete(String telefone, java.util.UUID appointmentId) {
        Appointment appointment = reminderAppointment(telefone, appointmentId).orElse(null);
        if (appointment == null) {
            return new ReminderActionResult(ReminderActionStatus.NAO_ENCONTRADO,
                    "Esse agendamento não está mais ativo.");
        }
        if (appointment.getStatus() == AppointmentStatus.PENDENTE) {
            appointmentApplicationService.confirmarAgendamento(appointment.getId());
        }
        return new ReminderActionResult(ReminderActionStatus.OK,
                "Presença confirmada. Te esperamos no horário combinado.");
    }

    public ReminderActionResult cancelarPorLembrete(String telefone, java.util.UUID appointmentId) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        Appointment appointment = reminderAppointment(telefone, appointmentId).orElse(null);
        if (appointment == null) {
            return new ReminderActionResult(ReminderActionStatus.NAO_ENCONTRADO,
                    "Esse agendamento não está mais ativo.");
        }

        var policy = automationPolicies.get(businessId);
        java.time.LocalDateTime agora = java.time.LocalDateTime.of(relogio.hoje(), relogio.agora());
        java.time.LocalDateTime inicio = java.time.LocalDateTime.of(
                appointment.getDate(), appointment.getStartTime());
        if (!policy.podeCancelar(inicio, agora)) {
            return new ReminderActionResult(ReminderActionStatus.CANCELAMENTO_BLOQUEADO,
                    "Esse agendamento já está dentro do prazo mínimo de cancelamento do negócio. "
                            + "Fale com o estabelecimento para alterar.");
        }

        String serviceName = serviceApplicationService.buscarPorId(appointment.getServiceId())
                .map(com.troquim_bot.service.Service::getNome)
                .orElse("Serviço");
        appointmentApplicationService.cancelarAgendamento(appointment.getId());
        if (waitlistApplicationService != null) {
            waitlistApplicationService.slotReleased(
                    businessId,
                    appointment.getServiceId(),
                    appointment.getProfessionalId(),
                    serviceName,
                    appointment.getDate(),
                    appointment.getStartTime());
        }
        return new ReminderActionResult(ReminderActionStatus.OK,
                "Agendamento cancelado. O horário foi liberado.");
    }

    public ReminderRescheduleOptions opcoesReagendamento(String telefone,
                                                          java.util.UUID appointmentId) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        Appointment appointment = reminderAppointment(telefone, appointmentId).orElse(null);
        if (appointment == null) {
            return new ReminderRescheduleOptions(
                    ReminderActionStatus.NAO_ENCONTRADO, "", List.of());
        }

        var item = consultarCatalogo.porServico(businessId, appointment.getServiceId()).orElse(null);
        if (item == null || item.profissionais().stream()
                .noneMatch(p -> p.id().equals(appointment.getProfessionalId()))) {
            return new ReminderRescheduleOptions(
                    ReminderActionStatus.HORARIO_INDISPONIVEL, "", List.of());
        }

        java.util.ArrayList<com.troquim_bot.availability.SlotRecommendationPolicy.Candidate> candidates =
                new java.util.ArrayList<>();
        LocalDate start = relogio.hoje();
        for (int day = 0; day < 7; day++) {
            LocalDate date = start.plusDays(day);
            for (LocalTime time : availabilityApplicationService.horariosLivres(
                    businessId, appointment.getServiceId(), appointment.getProfessionalId(), date)) {
                candidates.add(new com.troquim_bot.availability.SlotRecommendationPolicy.Candidate(
                        date, time, gapAdjacenteMinutos(
                                businessId, appointment.getProfessionalId(), date, time, item.duracao())));
            }
        }

        var ranked = new com.troquim_bot.availability.SlotRecommendationPolicy()
                .rank(candidates, appointment.getStartTime(), 3)
                .stream()
                .map(candidate -> new SlotSugerido(
                        item.nome(), candidate.date(), candidate.time()))
                .toList();

        return new ReminderRescheduleOptions(
                ranked.isEmpty() ? ReminderActionStatus.HORARIO_INDISPONIVEL : ReminderActionStatus.OK,
                item.nome(),
                ranked);
    }

    public ReminderActionResult reagendarPorLembrete(String telefone,
                                                      java.util.UUID appointmentId,
                                                      LocalDate date,
                                                      LocalTime time) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        Appointment appointment = reminderAppointment(telefone, appointmentId).orElse(null);
        if (appointment == null) {
            return new ReminderActionResult(ReminderActionStatus.NAO_ENCONTRADO,
                    "Esse agendamento não está mais ativo.");
        }
        try {
            String key = "reminder-reschedule-" + appointmentId + "-" + date + "-" + time;
            rescheduler.reschedule(businessId, appointment.getId(), date, time, key);
            return new ReminderActionResult(ReminderActionStatus.OK,
                    "Agendamento reagendado com sucesso.");
        } catch (AppointmentRescheduleApplicationService.RescheduleRejectedException rejected) {
            return new ReminderActionResult(ReminderActionStatus.HORARIO_INDISPONIVEL,
                    rejected.getMessage());
        } catch (RuntimeException failure) {
            return new ReminderActionResult(ReminderActionStatus.FALHA_TECNICA,
                    "Não consegui reagendar agora. Seu horário original foi preservado.");
        }
    }

    private Optional<Appointment> reminderAppointment(String telefone, java.util.UUID appointmentId) {
        if (appointmentId == null) return Optional.empty();
        BusinessId businessId = tenantProvider.currentBusinessId();
        return agendamentosAtivosDoCliente(businessId, telefone).stream()
                .filter(a -> a.getId().getValue().equals(appointmentId))
                .findFirst();
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

    private ServicoIdentificado identificarServico(String nomeInterpretado) {
        BusinessId businessId = tenantProvider.currentBusinessId();
        ConsultarCatalogo.Catalogo catalogo = consultarCatalogo.consultar(businessId);
        if (catalogo.naoConfigurado()) {
            return new ServicoIdentificado(Status.CATALOGO_NAO_CONFIGURADO, null);
        }

        Optional<ServiceId> aprendido = buscarAprendido(businessId, nomeInterpretado);
        if (aprendido.isPresent()) {
            Optional<ConsultarCatalogo.ItemDeCatalogo> itemAprendido = catalogo.itens().stream()
                    .filter(item -> item.id().equals(aprendido.get()))
                    .findFirst();
            if (itemAprendido.isPresent()) {
                return new ServicoIdentificado(Status.OK, itemAprendido.get());
            }
        }

        List<ConsultarCatalogo.ItemDeCatalogo> candidatos = catalogo.itens().stream()
                .filter(item -> mesmoServico(item.nome(), nomeInterpretado))
                .toList();
        if (candidatos.size() != 1) {
            return new ServicoIdentificado(Status.SERVICO_INDISPONIVEL, null);
        }

        return new ServicoIdentificado(Status.OK, candidatos.get(0));
    }

    /**
     * Adapter para casos que, por contrato, exigem um profissional já determinado.
     * Booking conversacional premium não usa este método para escolher silenciosamente.
     */
    private ServicoResolvido resolverServico(String nomeInterpretado) {
        ServicoIdentificado identificado = identificarServico(nomeInterpretado);
        if (!identificado.ok()) {
            return new ServicoResolvido(identificado.status(), identificado.item(), null);
        }
        if (identificado.item().profissionais().size() != 1) {
            return new ServicoResolvido(Status.PROFISSIONAL_AMBIGUO, identificado.item(), null);
        }
        return new ServicoResolvido(
                Status.OK, identificado.item(), identificado.item().profissionais().get(0).id());
    }

    private Optional<ProfessionalId> profissionalParaSlot(
            BusinessId businessId,
            ConsultarCatalogo.ItemDeCatalogo item,
            LocalDate data,
            LocalTime horario) {

        return item.profissionais().stream()
                .map(ConsultarCatalogo.ProfissionalDoCatalogo::id)
                .filter(profissional -> availabilityApplicationService.estaLivre(
                        businessId, item.id(), profissional, data, horario))
                .sorted(java.util.Comparator
                        .comparingInt((ProfessionalId profissional) -> gapAdjacenteMinutos(
                                businessId, profissional, data, horario, item.duracao()))
                        .thenComparing(profissional -> profissional.getValue().toString()))
                .findFirst();
    }

    private List<com.troquim_bot.availability.SlotRecommendationPolicy.Candidate> candidatosValidos(
            BusinessId businessId,
            ConsultarCatalogo.ItemDeCatalogo item,
            List<LocalDate> datas,
            BookingIntent intent) {

        java.util.ArrayList<com.troquim_bot.availability.SlotRecommendationPolicy.Candidate> candidatos =
                new java.util.ArrayList<>();

        for (var profissionalCatalogo : item.profissionais()) {
            ProfessionalId profissional = profissionalCatalogo.id();
            for (LocalDate data : datas) {
                for (LocalTime horario : availabilityApplicationService.horariosLivres(
                        businessId, item.id(), profissional, data)) {
                    if (intent != null && !intent.accepts(horario)) {
                        continue;
                    }
                    candidatos.add(new com.troquim_bot.availability.SlotRecommendationPolicy.Candidate(
                            data,
                            horario,
                            gapAdjacenteMinutos(
                                    businessId, profissional, data, horario, item.duracao()),
                            profissional.getValue().toString()));
                }
            }
        }
        return candidatos;
    }

    private List<SlotSugerido> rankearSlotsUnicos(
            ConsultarCatalogo.ItemDeCatalogo item,
            List<com.troquim_bot.availability.SlotRecommendationPolicy.Candidate> candidatos,
            LocalTime alvo,
            int limite) {

        if (candidatos.isEmpty() || limite <= 0) {
            return List.of();
        }

        List<com.troquim_bot.availability.SlotRecommendationPolicy.Candidate> ordenados =
                new com.troquim_bot.availability.SlotRecommendationPolicy()
                        .rank(candidatos, alvo, candidatos.size());

        java.util.LinkedHashMap<String, SlotSugerido> unicos = new java.util.LinkedHashMap<>();
        for (var candidato : ordenados) {
            String key = candidato.date() + "|" + candidato.time();
            unicos.putIfAbsent(key, new SlotSugerido(
                    item.nome(), candidato.date(), candidato.time()));
            if (unicos.size() >= limite) {
                break;
            }
        }
        return List.copyOf(unicos.values());
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

    private Optional<ServiceId> buscarAprendido(BusinessId businessId, String entrada) {
        String normalizada = normalizar(entrada);
        java.util.LinkedHashSet<ServiceId> encontrados = new java.util.LinkedHashSet<>();

        interpretationLearningStore.buscar(businessId, normalizada).ifPresent(encontrados::add);
        for (String token : normalizada.split("\\s+")) {
            interpretationLearningStore.buscar(businessId, token).ifPresent(encontrados::add);
        }

        return encontrados.size() == 1
                ? Optional.of(encontrados.iterator().next())
                : Optional.empty();
    }

    private static String chaveDeCorrecao(String nomeCanonico, String entradaNormalizada) {
        String alvo = singularSimples(normalizar(nomeCanonico));
        String melhor = entradaNormalizada;
        int melhorDistancia = distanciaLevenshtein(alvo, singularSimples(entradaNormalizada));

        for (String token : entradaNormalizada.split("\\s+")) {
            int distancia = distanciaLevenshtein(alvo, singularSimples(token));
            if (distancia < melhorDistancia) {
                melhorDistancia = distancia;
                melhor = token;
            }
        }
        return melhor;
    }

    private static boolean mesmoServico(String catalogo, String interpretado) {
        String a = singularSimples(normalizar(catalogo));
        String b = singularSimples(normalizar(interpretado));
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }

        // Linguagem natural simples: "quero fazer manicure" continua sendo interpretacao
        // da conversa. A identidade aceita continua vindo do item real do catalogo.
        return (" " + b + " ").contains(" " + a + " ");
    }

    private static int distanciaParaEntrada(String nomeCatalogo, String entradaNormalizada) {
        String alvo = singularSimples(normalizar(nomeCatalogo));
        int melhor = distanciaLevenshtein(alvo, singularSimples(entradaNormalizada));
        for (String token : entradaNormalizada.split("\\s+")) {
            melhor = Math.min(melhor, distanciaLevenshtein(alvo, singularSimples(token)));
        }
        return melhor;
    }

    private static int limiteDeCorrecao(String nomeCatalogo) {
        int tamanho = singularSimples(normalizar(nomeCatalogo)).length();
        if (tamanho <= 4) {
            return 1;
        }
        return 2;
    }

    private static int distanciaLevenshtein(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        int[] anterior = new int[b.length() + 1];
        int[] atual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            atual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int custo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                atual[j] = Math.min(
                        Math.min(atual[j - 1] + 1, anterior[j] + 1),
                        anterior[j - 1] + custo);
            }
            int[] troca = anterior;
            anterior = atual;
            atual = troca;
        }
        return anterior[b.length()];
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
