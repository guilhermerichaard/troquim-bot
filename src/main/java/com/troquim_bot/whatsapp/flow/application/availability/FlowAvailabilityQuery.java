package com.troquim_bot.whatsapp.flow.application.availability;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Ponte do módulo Flow para a fronteira oficial de disponibilidade.
 *
 * NÃO calcula slots, NÃO tem horários fixos, NÃO conhece duração e NÃO mantém calendário
 * paralelo: cada método é uma delegação direta a {@link AvailabilityApplicationService},
 * que por sua vez delega ao caso de uso canônico. A mesma fronteira que a conversa e o /app
 * usam. Se esta classe voltar a conter regra, a duplicação voltou.
 *
 * O {@link ServiceId} entrou na assinatura porque a duração do atendimento é do SERVIÇO: sem
 * ele não há como saber se um horário comporta o atendimento inteiro. Ele é o id REAL do
 * catálogo, e o {@link BusinessId} continua vindo exclusivamente da sessão validada — nunca
 * do payload da tela.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowAvailabilityQuery {

    private final AvailabilityApplicationService availabilityApplicationService;

    public FlowAvailabilityQuery(AvailabilityApplicationService availabilityApplicationService) {
        this.availabilityApplicationService = availabilityApplicationService;
    }

    public List<LocalTime> horariosLivres(BusinessId businessId, ServiceId servico, LocalDate data,
                                          ProfessionalId profissional) {
        return availabilityApplicationService.horariosLivres(businessId, servico, profissional, data);
    }

    public List<LocalDate> datasDisponiveis(BusinessId businessId, ServiceId servico,
                                            LocalDate de, LocalDate ate,
                                            ProfessionalId profissional) {
        return availabilityApplicationService.datasComVaga(businessId, servico, profissional, de, ate);
    }

    public boolean estaLivre(BusinessId businessId, ServiceId servico, LocalDate data, LocalTime horario,
                             ProfessionalId profissional) {
        return availabilityApplicationService.estaLivre(businessId, servico, profissional, data, horario);
    }
}
