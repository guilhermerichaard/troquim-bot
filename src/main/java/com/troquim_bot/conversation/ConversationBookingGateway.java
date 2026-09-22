package com.troquim_bot.conversation;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
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
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.customer.CustomerProfileService;
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
                                      WaitlistApplicationService waitlistApplicationService) {
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
        return resolverServico(nomeInterpretado).status();
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
        ServicoResolvido servico = intent.sameAsUsual()
                ? resolverServicoDoHistorico(businessId, telefone)
                : resolverServico(intent.serviceQuery());

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
            datas = availabilityApplicationService.datasComVaga(
                    businessId, servico.item().id(), servico.profissional(), hoje, hoje.plusDays(6));
        }

        com.troquim_bot.availability.SlotRecommendationPolicy policy =
                new com.troquim_bot.availability.SlotRecommendationPolicy();

        List<com.troquim_bot.availability.SlotRecommendationPolicy.Candidate> candidatos =
                new java.util.ArrayList<>();
        for (LocalDate data : datas) {
            for (LocalTime horario : availabilityApplicationService.horariosLivres(
                    businessId, servico.item().id(), servico.profissional(), data)) {
                if (!intent.accepts(horario)) {
                    continue;
                }
                candidatos.add(new com.troquim_bot.availability.SlotRecommendationPolicy.Candidate(
                        data, horario, gapAdjacenteMinutos(
                                businessId, servico.profissional(), data, horario, servico.item().duracao())));
            }
        }

        List<SlotSugerido> slots = policy.rank(candidatos, intent.targetTime(), 3).stream()
                .map(candidato -> new SlotSugerido(
                        servico.item().nome(), candidato.date(), candidato.time()))
                .toList();

        return new Recomendacao(Status.OK, servico.item().nome(), slots, intent.sameAsUsual());
    }

    private ServicoResolvido resolverServicoDoHistorico(BusinessId businessId, String telefone) {
        return customerProfileService.localizarIdOficial(businessId, telefone)
                .flatMap(customerId -> appointmentApplicationService
                        .listarHistoricoPorCliente(customerId).stream()
                        .filter(appointment -> appointment.pertenceAoTenant(businessId))
                        .findFirst())
                .flatMap(appointment -> serviceApplicationService.buscarPorId(appointment.getServiceId()))
                .map(servico -> resolverServico(servico.getNome()))
                .orElseGet(() -> new ServicoResolvido(Status.SERVICO_INDISPONIVEL, null, null));
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

        Optional<ServiceId> aprendido = buscarAprendido(businessId, nomeInterpretado);
        if (aprendido.isPresent()) {
            Optional<ConsultarCatalogo.ItemDeCatalogo> itemAprendido = catalogo.itens().stream()
                    .filter(item -> item.id().equals(aprendido.get()))
                    .findFirst();
            if (itemAprendido.isPresent()) {
                return resolverProfissional(itemAprendido.get());
            }
        }

        List<ConsultarCatalogo.ItemDeCatalogo> candidatos = catalogo.itens().stream()
                .filter(item -> mesmoServico(item.nome(), nomeInterpretado))
                .toList();
        if (candidatos.size() != 1) {
            return new ServicoResolvido(Status.SERVICO_INDISPONIVEL, null, null);
        }

        return resolverProfissional(candidatos.get(0));
    }

    private ServicoResolvido resolverProfissional(ConsultarCatalogo.ItemDeCatalogo item) {
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
