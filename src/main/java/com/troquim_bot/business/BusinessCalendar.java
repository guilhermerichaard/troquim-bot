package com.troquim_bot.business;

/**
 * Aggregate Root que é a ÚNICA autoridade sobre o expediente de um negócio.
 *
 * Identidade: {@link BusinessId} — um calendário por negócio, nunca dois. O expediente em
 * si é o Value Object {@link BusinessHours}; este agregado só dá a ele identidade e ciclo
 * de vida próprios, separados de {@link Business}. Não duplica NENHUMA regra de
 * {@link com.troquim_bot.availability.IntervaloDeHorario} ou de {@link BusinessHours}: só
 * encapsula QUAL negócio é dono de QUAL expediente.
 *
 * "Não configurado" e "dia fechado" continuam distintos aqui, exatamente como em
 * {@link BusinessHours}: {@link #naoConfigurado()} devolve um calendário cujo expediente
 * não tem nenhum dia, e é isso — não um booleano à parte — que os distingue.
 */
public class BusinessCalendar {

    private final BusinessId businessId;
    private final BusinessHours expediente;

    public BusinessCalendar(BusinessId businessId, BusinessHours expediente) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para o calendário");
        }
        this.businessId = businessId;
        this.expediente = expediente == null ? BusinessHours.naoConfigurado() : expediente;
    }

    /** Calendário de um negócio que ainda não publicou expediente. Estado observável, não erro. */
    public static BusinessCalendar naoConfigurado(BusinessId businessId) {
        return new BusinessCalendar(businessId, BusinessHours.naoConfigurado());
    }

    public BusinessId getBusinessId() {
        return businessId;
    }

    public BusinessHours getExpediente() {
        return expediente;
    }

    /** O negócio nunca publicou nenhum período — distinto de "publicou e está fechado hoje". */
    public boolean naoConfigurado() {
        return expediente.naoTemExpediente();
    }
}
