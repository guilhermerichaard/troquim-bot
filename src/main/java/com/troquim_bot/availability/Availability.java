package com.troquim_bot.availability;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.professional.ProfessionalId;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Aggregate Root: os períodos em que um profissional está disponível num dia da semana.
 *
 * Assim como o expediente do negócio, a disponibilidade é COMPOSIÇÃO POR PERÍODOS — cada
 * linha é um {@link IntervaloDeHorario}, e um profissional que para para almoçar tem duas
 * linhas naquele dia. Dia sem linha ativa é dia em que ele não atende.
 *
 * O {@link BusinessId} é OBRIGATÓRIO e faz parte da identidade. Sem ele, dois negócios que
 * compartilhassem o mesmo UUID de profissional (cenário adversário, e também o resultado de
 * um bug de tenant em qualquer camada acima) misturariam agendas — e o sintoma apareceria
 * como cliente marcada num horário que não existe.
 *
 * A disponibilidade do profissional NÃO amplia o expediente do negócio: quem manda é a
 * interseção dos dois, calculada pelo caso de uso de consulta.
 */
public class Availability {

    private final AvailabilityId id;
    private final BusinessId businessId;
    private final ProfessionalId professionalId;
    private DiaSemana dayOfWeek;
    private IntervaloDeHorario periodo;
    private AvailabilityStatus status;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    /** Criação de nova disponibilidade. Inicia ATIVA. */
    public Availability(AvailabilityId id, BusinessId businessId, ProfessionalId professionalId,
                        DiaSemana dayOfWeek, IntervaloDeHorario periodo) {
        this(id, businessId, professionalId, dayOfWeek, periodo, AvailabilityStatus.ATIVO,
                LocalDateTime.now(), LocalDateTime.now());
    }

    /** Atalho de criação a partir de início e fim soltos. */
    public Availability(AvailabilityId id, BusinessId businessId, ProfessionalId professionalId,
                        DiaSemana dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this(id, businessId, professionalId, dayOfWeek, periodoDe(startTime, endTime));
    }

    /**
     * Reconstituição a partir da persistência. Uso EXCLUSIVO do adapter.
     */
    public Availability(AvailabilityId id, BusinessId businessId, ProfessionalId professionalId,
                        DiaSemana dayOfWeek, IntervaloDeHorario periodo,
                        AvailabilityStatus status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        if (id == null) {
            throw new IllegalArgumentException("AvailabilityId é obrigatório");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório");
        }
        if (professionalId == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório");
        }
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Dia da semana é obrigatório");
        }
        if (periodo == null) {
            throw new IllegalArgumentException("Período é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status é obrigatório");
        }

        this.id = id;
        this.businessId = businessId;
        this.professionalId = professionalId;
        this.dayOfWeek = dayOfWeek;
        this.periodo = periodo;
        this.status = status;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    private static IntervaloDeHorario periodoDe(LocalTime startTime, LocalTime endTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Horário de início é obrigatório");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("Horário de fim é obrigatório");
        }
        return IntervaloDeHorario.de(startTime, endTime);
    }

    // ==================== GETTERS ====================

    public AvailabilityId getId() {
        return id;
    }

    public BusinessId getBusinessId() {
        return businessId;
    }

    public ProfessionalId getProfessionalId() {
        return professionalId;
    }

    public DiaSemana getDayOfWeek() {
        return dayOfWeek;
    }

    public IntervaloDeHorario getPeriodo() {
        return periodo;
    }

    public LocalTime getStartTime() {
        return periodo.inicio();
    }

    public LocalTime getEndTime() {
        return periodo.fim();
    }

    public AvailabilityStatus getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    // ==================== MÉTODOS DE NEGÓCIO ====================

    public boolean isAtivo() {
        return status == AvailabilityStatus.ATIVO;
    }

    /** Guarda de isolamento: usada pela Application antes de expor ou agendar. */
    public boolean pertenceAo(BusinessId outro) {
        return outro != null && businessId.equals(outro);
    }

    /**
     * Conflita com outra disponibilidade?
     *
     * Só faz sentido comparar dentro do MESMO negócio: dois salões podem ter profissionais
     * homônimos — ou, no limite, o mesmo UUID — sem que um cadastro atrapalhe o outro.
     */
    public boolean conflitaCom(Availability other) {
        if (other == null) {
            return false;
        }
        if (!this.businessId.equals(other.businessId)) {
            return false;
        }
        if (!this.professionalId.equals(other.professionalId)) {
            return false;
        }
        if (this.dayOfWeek != other.dayOfWeek) {
            return false;
        }
        return this.periodo.sobrepoe(other.periodo);
    }

    public void atualizarDayOfWeek(DiaSemana dayOfWeek) {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Dia da semana não pode ser nulo");
        }
        this.dayOfWeek = dayOfWeek;
        tocar();
    }

    public void atualizarStartTime(LocalTime startTime) {
        this.periodo = periodoDe(startTime, periodo.fim());
        tocar();
    }

    public void atualizarEndTime(LocalTime endTime) {
        this.periodo = periodoDe(periodo.inicio(), endTime);
        tocar();
    }

    public void atualizarPeriodo(DiaSemana dayOfWeek, IntervaloDeHorario novoPeriodo) {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("Dia da semana não pode ser nulo");
        }
        if (novoPeriodo == null) {
            throw new IllegalArgumentException("Período não pode ser nulo");
        }
        this.dayOfWeek = dayOfWeek;
        this.periodo = novoPeriodo;
        tocar();
    }

    public void atualizarHorario(DiaSemana dayOfWeek, LocalTime startTime, LocalTime endTime) {
        atualizarPeriodo(dayOfWeek, periodoDe(startTime, endTime));
    }

    public void inativar() {
        if (status == AvailabilityStatus.ATIVO) {
            this.status = AvailabilityStatus.INATIVO;
            tocar();
        }
    }

    public void ativar() {
        if (status == AvailabilityStatus.INATIVO) {
            this.status = AvailabilityStatus.ATIVO;
            tocar();
        }
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
