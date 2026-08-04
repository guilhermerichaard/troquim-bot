package com.troquim_bot.support;

import com.troquim_bot.application.availability.ConsultarDisponibilidade;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.availability.RelogioDoNegocio;
import com.troquim_bot.repository.InMemoryAvailabilityRepository;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryBusinessCalendarRepository;
import com.troquim_bot.repository.InMemoryProfessionalRepository;
import com.troquim_bot.repository.InMemoryServiceRepository;

/**
 * Fábrica de fixtures do caminho de booking, para testes que constroem
 * {@link com.troquim_bot.application.booking.BookingApplicationService} manualmente.
 *
 * A maioria desses testes exercita apenas o caminho LEGADO
 * ({@link com.troquim_bot.application.booking.BookingApplicationService#confirmar}), que
 * nunca chama {@link ConsultarDisponibilidade} nem a seção crítica de slot — mas o
 * construtor exige as duas dependências. Este helper existe para não duplicar, em doze
 * arquivos, o mesmo cabeamento inerte de repositórios vazios.
 */
public final class TestBookingSupport {

    private TestBookingSupport() {
    }

    /** ConsultarDisponibilidade com repositórios em memória vazios — nunca exercitada pelo caminho legado. */
    public static ConsultarDisponibilidade consultarDisponibilidadeInerte() {
        return new ConsultarDisponibilidade(
                new ConsultarCatalogo(new InMemoryServiceRepository(), new InMemoryProfessionalRepository()),
                new InMemoryBusinessCalendarRepository(),
                new InMemoryAvailabilityRepository(),
                new InMemoryAppointmentRepository(),
                new RelogioDoNegocio());
    }
}
