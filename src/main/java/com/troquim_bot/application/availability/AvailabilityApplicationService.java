package com.troquim_bot.application.availability;

import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.schedule.ScheduleService;
import com.troquim_bot.schedule.ScheduleSlot;
import com.troquim_bot.service.ServiceId;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Application Service de disponibilidade.
 *
 * DUAS RESPONSABILIDADES, DELIBERADAMENTE SEPARADAS:
 *
 * <ol>
 *   <li><b>CRUD tenant-scoped</b> da disponibilidade dos profissionais — toda operação exige
 *       {@link BusinessId} explícito. Não existe mais busca por id sem tenant, nem
 *       {@code listarTodos()} global;</li>
 *   <li><b>Delegação</b> da consulta de horários para {@link ConsultarDisponibilidade}, o
 *       caso de uso canônico. Este serviço NÃO calcula mais nada.</li>
 * </ol>
 *
 * O QUE SAIU DAQUI, e por quê:
 * <ul>
 *   <li>{@code ScheduleService} como gabarito — era uma agenda global em memória, igual para
 *       todos os negócios. Continua injetável apenas para o caminho LEGADO da conversa
 *       textual, marcado como tal;</li>
 *   <li>{@code PROFISSIONAL_PADRAO} — pressupunha um profissional único por sistema;</li>
 *   <li>{@code DURACAO_PADRAO} de uma hora — fazia serviço de 45min ocupar 60 e serviço de
 *       1h40 caber onde não cabia. A duração agora vem do catálogo, por serviço;</li>
 *   <li>os construtores que instanciavam {@code InMemoryAvailabilityRepository} e
 *       {@code InMemoryAppointmentRepository} — cada um criava uma agenda paralela invisível,
 *       e o Spring podia escolher justamente o construtor errado.</li>
 * </ul>
 */
@org.springframework.stereotype.Service
public class AvailabilityApplicationService {

    private final AvailabilityRepository availabilityRepository;
    private final ConsultarDisponibilidade consultarDisponibilidade;

    /**
     * Gabarito LEGADO da conversa textual. Ver {@link #consultarDisponibilidade(String)}.
     * Nenhum caminho novo o utiliza, e ele não participa de nenhuma decisão deste serviço.
     */
    private final ScheduleService scheduleServiceLegado;

    public AvailabilityApplicationService(AvailabilityRepository availabilityRepository,
                                          ConsultarDisponibilidade consultarDisponibilidade,
                                          ScheduleService scheduleServiceLegado) {
        this.availabilityRepository = availabilityRepository;
        this.consultarDisponibilidade = consultarDisponibilidade;
        this.scheduleServiceLegado = scheduleServiceLegado;
    }

    // ==================== CRUD TENANT-SCOPED ====================

    /**
     * Cria uma disponibilidade para um profissional do negócio.
     *
     * Recusa períodos sobrepostos do MESMO profissional no MESMO dia e negócio: dois
     * períodos que se cruzam gerariam o mesmo horário duas vezes na tela.
     */
    public Availability criarDisponibilidade(BusinessId businessId, ProfessionalId professionalId,
                                             DiaSemana dayOfWeek, LocalTime startTime, LocalTime endTime) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (professionalId == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório");
        }
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Dia da semana é obrigatório");
        }

        // As invariantes de período (início < fim, sem meia-noite) são do Value Object.
        IntervaloDeHorario periodo = IntervaloDeHorario.de(startTime, endTime);
        Availability nova = new Availability(AvailabilityId.generate(), businessId,
                professionalId, dayOfWeek, periodo);

        for (Availability existente : availabilityRepository
                .listarAtivasPorProfissionalEDia(businessId, professionalId, dayOfWeek)) {
            if (nova.conflitaCom(existente)) {
                throw new IllegalArgumentException(
                        "Já existe uma disponibilidade neste horário para este profissional");
            }
        }

        return availabilityRepository.salvar(nova);
    }

    public Optional<Availability> buscarPorId(BusinessId businessId, AvailabilityId id) {
        return availabilityRepository.buscarPorId(businessId, id);
    }

    /** Todas as disponibilidades do NEGÓCIO. Não existe listagem global. */
    public List<Availability> listarPorNegocio(BusinessId businessId) {
        return availabilityRepository.listarPorNegocio(businessId);
    }

    public List<Availability> listarPorProfissional(BusinessId businessId, ProfessionalId professionalId) {
        return availabilityRepository.listarPorProfissional(businessId, professionalId);
    }

    public List<Availability> listarAtivos(BusinessId businessId) {
        return listarPorNegocio(businessId).stream().filter(Availability::isAtivo).toList();
    }

    public Availability atualizarDayOfWeek(BusinessId businessId, AvailabilityId id, DiaSemana novoDayOfWeek) {
        Availability availability = exigirExistente(businessId, id);
        availability.atualizarDayOfWeek(novoDayOfWeek);
        return availabilityRepository.salvar(availability);
    }

    public Availability atualizarStartTime(BusinessId businessId, AvailabilityId id, LocalTime novoStartTime) {
        Availability availability = exigirExistente(businessId, id);
        availability.atualizarStartTime(novoStartTime);
        return availabilityRepository.salvar(availability);
    }

    public Availability atualizarEndTime(BusinessId businessId, AvailabilityId id, LocalTime novoEndTime) {
        Availability availability = exigirExistente(businessId, id);
        availability.atualizarEndTime(novoEndTime);
        return availabilityRepository.salvar(availability);
    }

    public Availability atualizarHorario(BusinessId businessId, AvailabilityId id, DiaSemana dayOfWeek,
                                         LocalTime startTime, LocalTime endTime) {
        Availability availability = exigirExistente(businessId, id);
        availability.atualizarHorario(dayOfWeek, startTime, endTime);
        return availabilityRepository.salvar(availability);
    }

    public Availability inativarDisponibilidade(BusinessId businessId, AvailabilityId id) {
        Availability availability = exigirExistente(businessId, id);
        availability.inativar();
        return availabilityRepository.salvar(availability);
    }

    public Availability ativarDisponibilidade(BusinessId businessId, AvailabilityId id) {
        Availability availability = exigirExistente(businessId, id);
        availability.ativar();
        return availabilityRepository.salvar(availability);
    }

    public boolean existe(BusinessId businessId, AvailabilityId id) {
        return availabilityRepository.existe(businessId, id);
    }

    public void remover(BusinessId businessId, AvailabilityId id) {
        availabilityRepository.remover(businessId, id);
    }

    // ==================== FONTE ÚNICA DE DISPONIBILIDADE ====================
    //
    // Delegação pura. O cálculo (expediente ∩ disponibilidade − appointments, com a duração
    // real do serviço) pertence a ConsultarDisponibilidade. Estes métodos existem apenas
    // porque Conversation e Flow já falam com este serviço.

    /** Horários livres para o serviço e o profissional informados, em ordem crescente. */
    public List<LocalTime> horariosLivres(BusinessId businessId, ServiceId servico,
                                          ProfessionalId profissional, LocalDate data) {
        return consultarDisponibilidade.doDia(businessId, servico, profissional, data).horarios();
    }

    /** Datas COM ao menos um horário livre na janela. */
    public List<LocalDate> datasComVaga(BusinessId businessId, ServiceId servico,
                                        ProfessionalId profissional, LocalDate de, LocalDate ate) {
        return consultarDisponibilidade.naJanela(businessId, servico, profissional, de, ate).datas();
    }

    /**
     * Um horário específico continua livre? Consulta de APRESENTAÇÃO — não reserva nada, e o
     * resultado envelhece. A decisão final é sempre revalidada na confirmação.
     */
    public boolean estaLivre(BusinessId businessId, ServiceId servico, ProfessionalId profissional,
                             LocalDate data, LocalTime horario) {
        return consultarDisponibilidade.estaLivre(businessId, servico, profissional, data, horario);
    }

    // ==================== CAMINHO LEGADO DA CONVERSA TEXTUAL ====================

    /**
     * LEGADO — horários do menu textual antigo, por NOME de dia.
     *
     * Lê o gabarito global de {@link ScheduleService}: horários de hora em hora, iguais para
     * todos os negócios, sem serviço, sem profissional e sem cruzar agendamentos. É a agenda
     * que esta etapa substituiu — mantida SÓ para não quebrar a conversa que ainda fala por
     * texto e não tem ServiceId para informar.
     *
     * NÃO HÁ FALLBACK NOS DOIS SENTIDOS: o caminho novo nunca cai aqui, e este nunca é
     * convertido silenciosamente naquele. Some quando a conversa passar a usar o catálogo.
     *
     * @deprecated use {@link #horariosLivres(BusinessId, ServiceId, ProfessionalId, LocalDate)},
     *             que respeita expediente, disponibilidade e duração reais.
     */
    @Deprecated
    public List<String> consultarDisponibilidade(String dia) {
        LocalDate data = proximaDataPara(dia);
        if (data == null) {
            return List.of();
        }
        return scheduleServiceLegado.listarHorariosDisponiveis(chaveDoDiaLegada(data)).stream()
                .map(ScheduleSlot::getHorario)
                .toList();
    }

    /** Chave do gabarito legado: nome do dia em português COM acento. */
    private static String chaveDoDiaLegada(LocalDate data) {
        return switch (data.getDayOfWeek()) {
            case MONDAY -> "segunda";
            case TUESDAY -> "terça";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY -> "sábado";
            case SUNDAY -> "domingo";
        };
    }

    /** Próxima ocorrência do dia da semana informado por texto. Null se não reconhecido. */
    private static LocalDate proximaDataPara(String dia) {
        DayOfWeek alvo = switch (normalizar(dia)) {
            case "segunda" -> DayOfWeek.MONDAY;
            case "terca" -> DayOfWeek.TUESDAY;
            case "quarta" -> DayOfWeek.WEDNESDAY;
            case "quinta" -> DayOfWeek.THURSDAY;
            case "sexta" -> DayOfWeek.FRIDAY;
            case "sabado" -> DayOfWeek.SATURDAY;
            case "domingo" -> DayOfWeek.SUNDAY;
            default -> null;
        };
        if (alvo == null) {
            return null;
        }
        LocalDate data = LocalDate.now();
        while (data.getDayOfWeek() != alvo) {
            data = data.plusDays(1);
        }
        return data;
    }

    private static String normalizar(String texto) {
        String base = texto == null ? "" : texto;
        String semAcentos = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).trim();
    }

    private Availability exigirExistente(BusinessId businessId, AvailabilityId id) {
        return availabilityRepository.buscarPorId(businessId, id)
                .orElseThrow(() -> new IllegalArgumentException("Disponibilidade não encontrada"));
    }
}
