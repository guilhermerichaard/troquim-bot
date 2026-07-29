package com.troquim_bot.application.agenda;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.business.Business;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.application.business.BusinessApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Leitura da agenda de UM negócio — o caso de uso que a área privada do dono consome.
 *
 * Não calcula disponibilidade, não cria agendamento e não mantém calendário próprio:
 * delega a {@link AppointmentApplicationService}, a MESMA fronteira que o Flow usa para
 * confirmar. Se esta classe algum dia contiver regra de agenda, existirão duas agendas.
 *
 * Todo método exige o {@link BusinessId} explicitamente. Não há resolução ambiente de
 * tenant aqui de propósito: quem chama precisa provar de qual negócio está falando, e é
 * isso que impede a agenda de um dono aparecer para outro.
 */
@Service
public class AgendaDoNegocioService {

    /** Teto de itens devolvidos. A tela do dono mostra os próximos, não o histórico. */
    private static final int LIMITE_PADRAO = 20;

    private final AppointmentApplicationService appointmentApplicationService;
    private final BusinessApplicationService businessApplicationService;

    public AgendaDoNegocioService(AppointmentApplicationService appointmentApplicationService,
                                  BusinessApplicationService businessApplicationService) {
        this.appointmentApplicationService = appointmentApplicationService;
        this.businessApplicationService = businessApplicationService;
    }

    /**
     * Próximos agendamentos ativos do negócio, a partir de hoje.
     *
     * @param businessId negócio dono da agenda; obrigatório
     */
    @Transactional(readOnly = true)
    public List<Appointment> proximosAgendamentos(BusinessId businessId) {
        return proximosAgendamentos(businessId, LocalDate.now(), LIMITE_PADRAO);
    }

    @Transactional(readOnly = true)
    public List<Appointment> proximosAgendamentos(BusinessId businessId, LocalDate desde, int limite) {
        if (businessId == null || desde == null || limite <= 0) {
            return List.of();
        }
        return appointmentApplicationService
                .listarAtivosPorTenantDesde(businessId, desde).stream()
                .limit(limite)
                .toList();
    }

    /** Agendamentos ativos de um dia específico. */
    @Transactional(readOnly = true)
    public List<Appointment> agendaDoDia(BusinessId businessId, LocalDate dia) {
        if (businessId == null || dia == null) {
            return List.of();
        }
        return appointmentApplicationService.listarAtivosPorTenantDesde(businessId, dia).stream()
                .filter(a -> a.getDate().isEqual(dia))
                .toList();
    }

    /** Dados de identificação do negócio, para o cabeçalho da área privada. */
    @Transactional(readOnly = true)
    public Optional<Business> negocio(BusinessId businessId) {
        if (businessId == null) {
            return Optional.empty();
        }
        return businessApplicationService.buscarPorId(businessId);
    }
}
