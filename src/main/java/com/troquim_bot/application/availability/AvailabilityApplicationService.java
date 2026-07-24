package com.troquim_bot.application.availability;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.availability.Availability;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.AvailabilityStatus;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.AvailabilityRepository;
import com.troquim_bot.repository.AppointmentRepository;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.schedule.ScheduleService;
import com.troquim_bot.schedule.ScheduleSlot;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Application Service para gerenciar Availabilities.
 * 
 * Responsabilidades:
 * - Criar disponibilidades
 * - Listar disponibilidades
 * - Buscar disponibilidades
 * - Atualizar disponibilidades
 * - Inativar disponibilidades
 * - Prevenir horários sobrepostos
 */
@org.springframework.stereotype.Service
public class AvailabilityApplicationService {

    private static final Duration DURACAO_PADRAO = Duration.ofHours(1);

    /**
     * Profissional unico do salao-piloto. Mesma chave deterministica usada pelo caso de
     * uso de booking, para que a consulta e a confirmacao falem do MESMO profissional.
     * Sai daqui quando existir catalogo persistido de Professional.
     */
    private static final ProfessionalId PROFISSIONAL_PADRAO = ProfessionalId.from(
            java.util.UUID.nameUUIDFromBytes(
                    "professional:troquim-mvp-default".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    private final AvailabilityRepository availabilityRepository;
    private final ScheduleService scheduleService;
    private final AppointmentRepository appointmentRepository;

    /**
     * Construtor para MVP com repositório em memória.
     */
    public AvailabilityApplicationService() {
        this(new InMemoryAvailabilityRepository(), new ScheduleService(),
                new InMemoryAppointmentRepository());
    }

    /**
     * Construtor com injeção de dependência (para testes ou futura implementação JPA).
     */
    public AvailabilityApplicationService(AvailabilityRepository availabilityRepository) {
        this(availabilityRepository, new ScheduleService(), new InMemoryAppointmentRepository());
    }

    public AvailabilityApplicationService(AvailabilityRepository availabilityRepository,
                                          ScheduleService scheduleService) {
        this(availabilityRepository, scheduleService, new InMemoryAppointmentRepository());
    }

    /**
     * Construtor completo, usado pelo Spring.
     *
     * A anotação {@code @Autowired} é necessária: com vários construtores e nenhum
     * marcado, o Spring escolhia o VAZIO — e a aplicação rodava com um
     * {@link ScheduleService} instanciado aqui dentro, diferente do bean usado pelo
     * resto do sistema. Eram duas agendas distintas em memória.
     */
    @Autowired
    public AvailabilityApplicationService(AvailabilityRepository availabilityRepository,
                                          ScheduleService scheduleService,
                                          AppointmentRepository appointmentRepository) {
        this.availabilityRepository = availabilityRepository;
        this.scheduleService = scheduleService;
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Cria uma nova disponibilidade.
     * 
     * @param professionalId ID do profissional
     * @param dayOfWeek Dia da semana
     * @param startTime Horário de início
     * @param endTime Horário de fim
     * @return Availability criada com status ATIVO
     * @throws IllegalArgumentException se houver conflito de horário
     */
    public Availability criarDisponibilidade(ProfessionalId professionalId, DiaSemana dayOfWeek,
                                              LocalTime startTime, LocalTime endTime) {
        if (professionalId == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório");
        }
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Dia da semana é obrigatório");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Horário de início é obrigatório");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("Horário de fim é obrigatório");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Horário de início deve ser menor que horário de fim");
        }

        AvailabilityId id = AvailabilityId.generate();

        Availability newAvailability = new Availability(id, professionalId, dayOfWeek, startTime, endTime);

        // Verifica conflito com disponibilidades existentes do mesmo profissional
        List<Availability> existentes = availabilityRepository.findByProfessionalIdAndDayOfWeek(professionalId, dayOfWeek);
        for (Availability existing : existentes) {
            if (existing.isAtivo() && newAvailability.conflitaCom(existing)) {
                throw new IllegalArgumentException("Já existe uma disponibilidade neste horário para este profissional");
            }
        }

        return availabilityRepository.save(newAvailability);
    }

    /**
     * Busca disponibilidade por ID.
     * 
     * @param id ID da disponibilidade
     * @return Optional com o Availability se encontrado
     */
    public Optional<Availability> buscarPorId(AvailabilityId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(availabilityRepository.findById(id));
    }

    /**
     * Lista todas as disponibilidades.
     * 
     * @return Lista de todos os availabilities
     */
    public List<Availability> listarTodos() {
        return availabilityRepository.findAll();
    }

    /**
     * Lista disponibilidades de um profissional.
     * 
     * @param professionalId ID do profissional
     * @return Lista de availabilities do profissional
     */
    public List<Availability> listarPorProfissional(ProfessionalId professionalId) {
        if (professionalId == null) {
            return List.of();
        }
        return availabilityRepository.findByProfessionalId(professionalId);
    }

    /**
     * Lista apenas disponibilidades ativas.
     * 
     * @return Lista de availabilities ativos
     */
    public List<Availability> listarAtivos() {
        return availabilityRepository.findAll().stream()
            .filter(Availability::isAtivo)
            .toList();
    }

    /**
     * Atualiza o dia da semana.
     * 
     * @param id ID da disponibilidade
     * @param novoDayOfWeek Novo dia da semana
     * @return Availability atualizado
     */
    public Availability atualizarDayOfWeek(AvailabilityId id, DiaSemana novoDayOfWeek) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.atualizarDayOfWeek(novoDayOfWeek);
        return availabilityRepository.save(availability);
    }

    /**
     * Atualiza o horário de início.
     * 
     * @param id ID da disponibilidade
     * @param novoStartTime Novo horário de início
     * @return Availability atualizado
     */
    public Availability atualizarStartTime(AvailabilityId id, LocalTime novoStartTime) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.atualizarStartTime(novoStartTime);
        return availabilityRepository.save(availability);
    }

    /**
     * Atualiza o horário de fim.
     * 
     * @param id ID da disponibilidade
     * @param novoEndTime Novo horário de fim
     * @return Availability atualizado
     */
    public Availability atualizarEndTime(AvailabilityId id, LocalTime novoEndTime) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.atualizarEndTime(novoEndTime);
        return availabilityRepository.save(availability);
    }

    /**
     * Atualiza dia e horários completos.
     * 
     * @param id ID da disponibilidade
     * @param dayOfWeek Novo dia da semana
     * @param startTime Novo horário de início
     * @param endTime Novo horário de fim
     * @return Availability atualizado
     */
    public Availability atualizarHorario(AvailabilityId id, DiaSemana dayOfWeek,
                                          LocalTime startTime, LocalTime endTime) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.atualizarHorario(dayOfWeek, startTime, endTime);
        return availabilityRepository.save(availability);
    }

    /**
     * Inativa uma disponibilidade.
     * 
     * @param id ID da disponibilidade
     * @return Availability inativado
     */
    public Availability inativarDisponibilidade(AvailabilityId id) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.inativar();
        return availabilityRepository.save(availability);
    }

    /**
     * Ativa uma disponibilidade.
     * 
     * @param id ID da disponibilidade
     * @return Availability ativado
     */
    public Availability ativarDisponibilidade(AvailabilityId id) {
        Availability availability = getAvailabilityOrThrow(id);
        availability.ativar();
        return availabilityRepository.save(availability);
    }

    /**
     * Verifica se uma disponibilidade existe.
     * 
     * @param id ID da disponibilidade
     * @return true se existe
     */
    public boolean existe(AvailabilityId id) {
        if (id == null) {
            return false;
        }
        return availabilityRepository.exists(id);
    }

    /**
     * Consulta horários disponíveis para um determinado dia.
     * Delega para o serviço de agendamento que gerencia a agenda real.
     * 
     * @param dia Nome do dia (segunda, terça, etc.) ou "hoje", "amanhã"
     * @return Lista de horários disponíveis formatados como string
     */
    public List<String> consultarDisponibilidade(String dia) {
        LocalDate data = proximaDataPara(dia);
        if (data == null) {
            return List.of();
        }
        return horariosLivres(data, PROFISSIONAL_PADRAO).stream()
                .map(LocalTime::toString)
                .toList();
    }

    // ==================== FONTE ÚNICA DE DISPONIBILIDADE ====================
    //
    // Antes existiam dois caminhos: a conversa lia o gabarito de ScheduleService cru
    // (mostrando como livre um horário JÁ agendado) e o módulo do WhatsApp Flow tinha
    // seu próprio cruzamento com Appointments. Agora só existe este: o gabarito é o
    // conjunto de partida e os Appointments ativos são o filtro autoritativo.
    // Conversation e Flow chamam exatamente estes métodos.

    /**
     * Horários livres numa data para um profissional, em ordem crescente.
     * Vazio significa dia fechado, data no passado ou agenda cheia.
     */
    public List<LocalTime> horariosLivres(LocalDate data, ProfessionalId profissional) {
        if (data == null || profissional == null || data.isBefore(LocalDate.now())) {
            return List.of();
        }

        List<ScheduleSlot> gabarito = scheduleService.listarHorariosDisponiveis(chaveDoDia(data));
        if (gabarito.isEmpty()) {
            return List.of();
        }

        List<Appointment> ocupados = appointmentRepository
                .findByProfessionalIdAndDate(profissional, data).stream()
                .filter(Appointment::isAtivo)
                .toList();

        boolean hoje = data.isEqual(LocalDate.now());
        LocalTime agora = LocalTime.now();

        List<LocalTime> livres = new ArrayList<>();
        for (ScheduleSlot slot : gabarito) {
            LocalTime inicio = LocalTime.parse(slot.getHorario());
            // O gabarito não conhece a hora atual: horário que já passou hoje não é opção.
            if (hoje && !inicio.isAfter(agora)) {
                continue;
            }
            if (!conflita(inicio, ocupados)) {
                livres.add(inicio);
            }
        }
        livres.sort(LocalTime::compareTo);
        return List.copyOf(livres);
    }

    /** Datas SEM nenhum horário livre na janela, para desabilitar no calendário. */
    public List<LocalDate> datasSemVaga(LocalDate de, LocalDate ate, ProfessionalId profissional) {
        if (de == null || ate == null || profissional == null) {
            return List.of();
        }
        List<LocalDate> indisponiveis = new ArrayList<>();
        for (LocalDate dia = de; !dia.isAfter(ate); dia = dia.plusDays(1)) {
            if (horariosLivres(dia, profissional).isEmpty()) {
                indisponiveis.add(dia);
            }
        }
        return List.copyOf(indisponiveis);
    }

    /** Datas COM ao menos um horário livre na janela — para o dropdown de datas do Flow. */
    public List<LocalDate> datasComVaga(LocalDate de, LocalDate ate, ProfessionalId profissional) {
        if (de == null || ate == null || profissional == null) {
            return List.of();
        }
        List<LocalDate> disponiveis = new ArrayList<>();
        for (LocalDate dia = de; !dia.isAfter(ate); dia = dia.plusDays(1)) {
            if (!horariosLivres(dia, profissional).isEmpty()) {
                disponiveis.add(dia);
            }
        }
        return List.copyOf(disponiveis);
    }

    /**
     * Um horário específico continua livre? Consulta de APRESENTAÇÃO — não reserva nada,
     * e o resultado envelhece. A decisão final é sempre revalidada na confirmação.
     */
    public boolean estaLivre(LocalDate data, LocalTime horario, ProfessionalId profissional) {
        return horario != null && horariosLivres(data, profissional).contains(horario);
    }

    private static boolean conflita(LocalTime inicio, List<Appointment> ocupados) {
        LocalTime fim = inicio.plus(DURACAO_PADRAO);
        for (Appointment ocupado : ocupados) {
            if (inicio.isBefore(ocupado.getEndTime()) && ocupado.getStartTime().isBefore(fim)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Chave usada pelo {@link ScheduleService}: nome do dia em português COM acento.
     */
    private static String chaveDoDia(LocalDate data) {
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

    // ==================== MÉTODOS PRIVADOS ====================

    private Availability getAvailabilityOrThrow(AvailabilityId id) {
        Availability availability = availabilityRepository.findById(id);
        if (availability == null) {
            throw new IllegalArgumentException("Disponibilidade não encontrada");
        }
        return availability;
    }
}