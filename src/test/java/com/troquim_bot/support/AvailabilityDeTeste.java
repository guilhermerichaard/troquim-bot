package com.troquim_bot.support;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.schedule.ScheduleService;

/**
 * Monta o {@code AvailabilityApplicationService} para os testes do caminho LEGADO da
 * conversa textual.
 *
 * Esses testes exercitam o menu por NOME de dia, que não tem ServiceId e por isso não passa
 * pelo caso de uso canônico. O {@code ConsultarDisponibilidade} é deixado NULO de propósito:
 * se algum desses testes começar a consultar horários pelo caminho novo, o NPE aparece na
 * hora, em vez de o teste passar exercitando silenciosamente a rota errada.
 */
public final class AvailabilityDeTeste {

    private AvailabilityDeTeste() {
    }

    public static AvailabilityApplicationService legado(ScheduleService scheduleService) {
        return new AvailabilityApplicationService(
                new InMemoryAvailabilityRepository(), null, scheduleService);
    }

    public static AvailabilityApplicationService legado() {
        return legado(new ScheduleService());
    }
}
