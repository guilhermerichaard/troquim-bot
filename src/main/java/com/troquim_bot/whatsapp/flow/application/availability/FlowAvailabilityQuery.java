package com.troquim_bot.whatsapp.flow.application.availability;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Ponte do módulo Flow para a fronteira oficial de disponibilidade.
 *
 * NÃO calcula slots, NÃO tem horários fixos e NÃO mantém calendário paralelo: cada método
 * é uma delegação direta a {@link AvailabilityApplicationService}, a mesma fronteira que a
 * conversa usa. Antes, este arquivo cruzava gabarito e Appointments por conta própria —
 * era a segunda fonte de disponibilidade do sistema.
 *
 * A classe permanece (em vez de os handlers chamarem o Application Service direto) apenas
 * como ponto de tradução do vocabulário do Flow. Se ela voltar a conter regra, a duplicação
 * voltou.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowAvailabilityQuery {

    private final AvailabilityApplicationService availabilityApplicationService;

    public FlowAvailabilityQuery(AvailabilityApplicationService availabilityApplicationService) {
        this.availabilityApplicationService = availabilityApplicationService;
    }

    public List<LocalTime> horariosLivres(LocalDate data, ProfessionalId profissional) {
        return availabilityApplicationService.horariosLivres(data, profissional);
    }

    public List<LocalDate> datasDisponiveis(LocalDate de, LocalDate ate, ProfessionalId profissional) {
        return availabilityApplicationService.datasComVaga(de, ate, profissional);
    }

    public boolean estaLivre(LocalDate data, LocalTime horario, ProfessionalId profissional) {
        return availabilityApplicationService.estaLivre(data, horario, profissional);
    }
}
