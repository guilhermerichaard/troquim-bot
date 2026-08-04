package com.troquim_bot.application.availability;

import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.availability.PoliticaDeInicioDeSlot;
import com.troquim_bot.availability.RelogioDoNegocio;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.BusinessHoursRepository;
import com.troquim_bot.service.ServiceId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Caso de uso NEUTRO de disponibilidade: a única autoridade sobre "que horários existem".
 *
 * A conta é sempre a mesma, e é toda ela feita de dados persistidos do tenant:
 *
 * <pre>
 *   períodos de funcionamento do NEGÓCIO
 *   ∩ períodos de disponibilidade do PROFISSIONAL
 *   − appointments ativos que se sobreponham
 *   , com a duração REAL do SERVIÇO cabendo inteira
 * </pre>
 *
 * O que isto substitui: uma agenda global em memória, igual para todos os negócios, com
 * horários de hora em hora chumbados no código e uma duração fixa de uma hora. Aquilo não
 * conseguia representar almoço, sábado diferente nem serviço de 45 minutos — e, por ser
 * global, o horário ocupado num salão sumia da agenda de outro.
 *
 * FRONTEIRA: nada aqui sabe o que é WhatsApp, Flow, Meta, tela ou JSON. As regras de
 * período, sobreposição e encaixe vivem no Domain ({@link IntervaloDeHorario},
 * {@link PoliticaDeInicioDeSlot}); a Infrastructure só persiste. Esta classe orquestra.
 *
 * ESTADOS EXPLÍCITOS: "não tem horário" nunca é uma lista vazia sem explicação. Dia fechado,
 * expediente não configurado, profissional sem agenda e agenda cheia são coisas diferentes,
 * e quem consome precisa poder dizer isso ao cliente.
 */
@Component
public class ConsultarDisponibilidade {

    private final ConsultarCatalogo consultarCatalogo;
    private final BusinessHoursRepository businessHoursRepository;
    private final AvailabilityRepository availabilityRepository;
    private final AppointmentRepository appointmentRepository;
    private final RelogioDoNegocio relogio;
    private final PoliticaDeInicioDeSlot politica;

    /**
     * {@code @Autowired} explícito: há duas assinaturas, e sem a marcação o Spring não teria
     * como escolher. É a política do MVP que deve ser injetada, não a de teste.
     */
    @Autowired
    public ConsultarDisponibilidade(ConsultarCatalogo consultarCatalogo,
                                    BusinessHoursRepository businessHoursRepository,
                                    AvailabilityRepository availabilityRepository,
                                    AppointmentRepository appointmentRepository,
                                    RelogioDoNegocio relogio) {
        this(consultarCatalogo, businessHoursRepository, availabilityRepository,
                appointmentRepository, relogio, PoliticaDeInicioDeSlot.padrao());
    }

    /** Sobrecarga com política explícita — existe para testar a própria regra de passo. */
    public ConsultarDisponibilidade(ConsultarCatalogo consultarCatalogo,
                                    BusinessHoursRepository businessHoursRepository,
                                    AvailabilityRepository availabilityRepository,
                                    AppointmentRepository appointmentRepository,
                                    RelogioDoNegocio relogio,
                                    PoliticaDeInicioDeSlot politica) {
        this.consultarCatalogo = consultarCatalogo;
        this.businessHoursRepository = businessHoursRepository;
        this.availabilityRepository = availabilityRepository;
        this.appointmentRepository = appointmentRepository;
        this.relogio = relogio;
        this.politica = politica;
    }

    /**
     * Por que um dia não tem horários. Cada valor é uma frase diferente para o cliente e uma
     * ação diferente para o dono do salão — colapsá-los numa lista vazia esconderia
     * "expediente nunca cadastrado" atrás de "hoje está cheio".
     */
    public enum Condicao {
        /** Há ao menos um horário livre. */
        DISPONIVEL,
        /** O negócio nunca publicou expediente. */
        EXPEDIENTE_NAO_CONFIGURADO,
        /** O negócio não abre neste dia da semana. */
        DIA_FECHADO,
        /** O profissional não tem período cadastrado (ou ativo) neste dia. */
        PROFISSIONAL_SEM_DISPONIBILIDADE,
        /** Serviço inexistente, de outro negócio, inativo ou sem ninguém habilitado. */
        SERVICO_INDISPONIVEL,
        /** Profissional inexistente, de outro negócio, inativo ou que não atende o serviço. */
        PROFISSIONAL_INDISPONIVEL,
        /** Data anterior a hoje — não se agenda no passado. */
        DATA_PASSADA,
        /** Tudo configurado, mas nada sobrou depois dos agendamentos (ou já passou da hora). */
        AGENDA_CHEIA
    }

    /** Horários de um dia, com o motivo quando não há nenhum. */
    public record AgendaDoDia(BusinessId businessId, ServiceId servico, ProfessionalId profissional,
                              LocalDate data, List<LocalTime> horarios, Condicao condicao) {

        public boolean temHorario() {
            return !horarios.isEmpty();
        }

        static AgendaDoDia vazia(BusinessId businessId, ServiceId servico, ProfessionalId profissional,
                                 LocalDate data, Condicao condicao) {
            return new AgendaDoDia(businessId, servico, profissional, data, List.of(), condicao);
        }
    }

    /** Dias com vaga numa janela, com o motivo quando a janela inteira está indisponível. */
    public record AgendaDaJanela(List<LocalDate> datas, Condicao condicao) {

        public boolean temData() {
            return !datas.isEmpty();
        }
    }

    /**
     * Horários livres para agendar ESTE serviço com ESTE profissional NESTE dia.
     *
     * A duração vem do catálogo persistido, nunca do chamador: é a única forma de garantir
     * que o serviço de 1h40 ocupe 1h40 e que ninguém consiga encolher o próprio atendimento
     * mandando outro número no payload.
     */
    public AgendaDoDia doDia(BusinessId businessId, ServiceId servico,
                             ProfessionalId profissional, LocalDate data) {
        exigir(businessId != null, "BusinessId é obrigatório para consultar disponibilidade");
        exigir(servico != null, "ServiceId é obrigatório para consultar disponibilidade");
        exigir(profissional != null, "ProfessionalId é obrigatório para consultar disponibilidade");
        exigir(data != null, "Data é obrigatória para consultar disponibilidade");

        LocalDate hoje = relogio.hoje();
        if (data.isBefore(hoje)) {
            return AgendaDoDia.vazia(businessId, servico, profissional, data, Condicao.DATA_PASSADA);
        }

        // Catálogo é a autoridade sobre tenant, serviço ativo e habilitação do profissional.
        // Nada disso é reimplementado aqui.
        Optional<ConsultarCatalogo.ItemDeCatalogo> item =
                consultarCatalogo.porServico(businessId, servico);
        if (item.isEmpty()) {
            return AgendaDoDia.vazia(businessId, servico, profissional, data,
                    Condicao.SERVICO_INDISPONIVEL);
        }
        boolean habilitado = item.get().profissionais().stream()
                .anyMatch(p -> p.id().equals(profissional));
        if (!habilitado) {
            return AgendaDoDia.vazia(businessId, servico, profissional, data,
                    Condicao.PROFISSIONAL_INDISPONIVEL);
        }
        Duration duracao = item.get().duracao();

        BusinessHours expediente = businessHoursRepository.buscar(businessId);
        if (expediente.naoTemExpediente()) {
            return AgendaDoDia.vazia(businessId, servico, profissional, data,
                    Condicao.EXPEDIENTE_NAO_CONFIGURADO);
        }

        DiaSemana dia = DiaSemana.de(data);
        List<IntervaloDeHorario> doNegocio = expediente.periodosDe(dia);
        if (doNegocio.isEmpty()) {
            return AgendaDoDia.vazia(businessId, servico, profissional, data, Condicao.DIA_FECHADO);
        }

        List<Availability> doProfissional = availabilityRepository
                .listarAtivasPorProfissionalEDia(businessId, profissional, dia);
        if (doProfissional.isEmpty()) {
            return AgendaDoDia.vazia(businessId, servico, profissional, data,
                    Condicao.PROFISSIONAL_SEM_DISPONIBILIDADE);
        }

        // A disponibilidade do profissional NÃO amplia o expediente: só a parte comum vale.
        List<IntervaloDeHorario> atendiveis = new ArrayList<>();
        for (IntervaloDeHorario periodoNegocio : doNegocio) {
            for (Availability disponibilidade : doProfissional) {
                periodoNegocio.intersecao(disponibilidade.getPeriodo()).ifPresent(atendiveis::add);
            }
        }

        // Escopo de tenant E de profissional na consulta de ocupação: agendamento de outro
        // negócio (ou de outro profissional) não pode bloquear este horário.
        List<Appointment> ocupados = appointmentRepository
                .findByBusinessIdAndProfessionalIdAndDate(businessId, profissional, data).stream()
                .filter(Appointment::isAtivo)
                .toList();

        boolean ehHoje = data.isEqual(hoje);
        LocalTime agora = relogio.agora();

        TreeSet<LocalTime> livres = new TreeSet<>();
        for (IntervaloDeHorario atendivel : atendiveis) {
            for (LocalTime inicio : politica.candidatos(atendivel, duracao)) {
                if (ehHoje && !inicio.isAfter(agora)) {
                    continue; // horário que já passou hoje não é opção
                }
                if (!conflita(inicio, duracao, ocupados)) {
                    livres.add(inicio);
                }
            }
        }

        if (livres.isEmpty()) {
            return AgendaDoDia.vazia(businessId, servico, profissional, data, Condicao.AGENDA_CHEIA);
        }
        return new AgendaDoDia(businessId, servico, profissional, data,
                List.copyOf(livres), Condicao.DISPONIVEL);
    }

    /** Um horário específico continua livre? Consulta de APRESENTAÇÃO — não reserva nada. */
    public boolean estaLivre(BusinessId businessId, ServiceId servico, ProfessionalId profissional,
                             LocalDate data, LocalTime horario) {
        return horario != null && doDia(businessId, servico, profissional, data).horarios().contains(horario);
    }

    /**
     * Datas com ao menos um horário livre na janela.
     *
     * Quando NENHUM dia tem vaga, a condição devolvida é a do primeiro dia avaliado com um
     * motivo estrutural (expediente ausente, serviço/profissional indisponível): esses
     * motivos valem para a janela inteira, e repeti-los por dia só esconderia a causa.
     */
    public AgendaDaJanela naJanela(BusinessId businessId, ServiceId servico,
                                   ProfessionalId profissional, LocalDate de, LocalDate ate) {
        exigir(de != null && ate != null, "Janela de datas é obrigatória");

        List<LocalDate> comVaga = new ArrayList<>();
        Condicao estrutural = null;
        for (LocalDate dia = de; !dia.isAfter(ate); dia = dia.plusDays(1)) {
            AgendaDoDia agenda = doDia(businessId, servico, profissional, dia);
            if (agenda.temHorario()) {
                comVaga.add(dia);
                continue;
            }
            if (estrutural == null && ehEstrutural(agenda.condicao())) {
                estrutural = agenda.condicao();
            }
        }

        if (!comVaga.isEmpty()) {
            return new AgendaDaJanela(List.copyOf(comVaga), Condicao.DISPONIVEL);
        }
        return new AgendaDaJanela(List.of(), estrutural == null ? Condicao.AGENDA_CHEIA : estrutural);
    }

    /** Motivos que não mudam de um dia para o outro dentro da mesma janela. */
    private static boolean ehEstrutural(Condicao condicao) {
        return condicao == Condicao.EXPEDIENTE_NAO_CONFIGURADO
                || condicao == Condicao.SERVICO_INDISPONIVEL
                || condicao == Condicao.PROFISSIONAL_INDISPONIVEL;
    }

    /**
     * Conflito por SOBREPOSIÇÃO, nunca por igualdade de início.
     *
     * {@code inicioCandidato < fimOcupado && inicioOcupado < fimCandidato}. Comparar apenas
     * os horários iniciais deixaria passar o caso mais comum de erro real: um atendimento de
     * 1h30 começando às 10:00 não impede um candidato às 10:15 se a regra só olhar o início.
     * Encostar não conflita: quem termina às 10:00 libera o candidato das 10:00.
     */
    private static boolean conflita(LocalTime inicio, Duration duracao, List<Appointment> ocupados) {
        IntervaloDeHorario candidato = IntervaloDeHorario.comDuracao(inicio, duracao);
        for (Appointment ocupado : ocupados) {
            IntervaloDeHorario janelaOcupada =
                    IntervaloDeHorario.de(ocupado.getStartTime(), ocupado.getEndTime());
            if (candidato.sobrepoe(janelaOcupada)) {
                return true;
            }
        }
        return false;
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new IllegalArgumentException(mensagem);
        }
    }
}
