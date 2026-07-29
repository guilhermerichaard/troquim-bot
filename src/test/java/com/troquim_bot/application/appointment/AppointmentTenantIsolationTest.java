package com.troquim_bot.application.appointment;

import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolamento da agenda entre negócios.
 *
 * O professional_id do catálogo do Flow é sintético e compartilhado entre todos os
 * negócios. Sem escopo de tenant, a agenda do negócio A ocuparia o horário do negócio
 * B, e a leitura de um dono devolveria agendamentos de outro. Estes testes prendem as
 * duas garantias sobre o Application Service — a MESMA fronteira que o Flow usa.
 */
@DisplayName("Agenda - isolamento entre negócios")
class AppointmentTenantIsolationTest {

    // Mesmo profissional sintético para os dois negócios: é exatamente o cenário que o
    // catálogo fixo do Flow produz.
    private static final ProfessionalId PROFISSIONAL =
            ProfessionalId.from(java.util.UUID.fromString("99999999-9999-9999-9999-999999999999"));

    private AppointmentApplicationService service;

    @BeforeEach
    void setUp() {
        InMemoryAppointmentRepository repo = new InMemoryAppointmentRepository();
        service = new AppointmentApplicationService(repo, new com.troquim_bot.repository.InMemoryReservationRepository());
    }

    private Appointment agendaPara(BusinessId tenant, LocalTime inicio) {
        return service.criarAgendamento(
                tenant, CustomerId.generate(), PROFISSIONAL,
                ServiceId.generate(), AvailabilityId.generate(),
                LocalDate.now().plusDays(3), inicio, inicio.plusHours(1));
    }

    @Test
    @DisplayName("listarAtivos(A) não devolve agendamentos do negócio B")
    void listagemNaoAtravessaTenant() {
        agendaPara(TestTenants.PILOT, LocalTime.of(9, 0));
        agendaPara(TestTenants.OUTRO, LocalTime.of(10, 0));

        List<Appointment> doPiloto = service.listarAtivos(TestTenants.PILOT);
        List<Appointment> doOutro = service.listarAtivos(TestTenants.OUTRO);

        assertEquals(1, doPiloto.size());
        assertEquals(1, doOutro.size());
        assertTrue(doPiloto.get(0).pertenceAoTenant(TestTenants.PILOT));
        assertFalse(doPiloto.get(0).pertenceAoTenant(TestTenants.OUTRO),
                "O agendamento do PILOT não pode pertencer ao OUTRO");
    }

    @Test
    @DisplayName("mesmo profissional e horário em negócios diferentes NÃO é conflito")
    void mesmoHorarioEmNegociosDiferentesNaoConflita() {
        LocalTime dez = LocalTime.of(10, 0);
        agendaPara(TestTenants.PILOT, dez);

        // O mesmo slot no OUTRO negócio deve ser aceito: a agenda é por tenant, e o
        // profissional sintético compartilhado não pode acoplar os dois.
        Appointment doOutro = agendaPara(TestTenants.OUTRO, dez);

        assertTrue(doOutro.pertenceAoTenant(TestTenants.OUTRO));
        assertEquals(1, service.listarAtivos(TestTenants.PILOT).size());
        assertEquals(1, service.listarAtivos(TestTenants.OUTRO).size());
    }

    @Test
    @DisplayName("conflito REAL só ocorre dentro do mesmo negócio")
    void conflitoDentroDoMesmoTenant() {
        LocalTime dez = LocalTime.of(10, 0);
        agendaPara(TestTenants.PILOT, dez);

        // Segundo agendamento do MESMO negócio no mesmo slot: agora sim é conflito.
        try {
            agendaPara(TestTenants.PILOT, dez);
        } catch (RuntimeException esperado) {
            assertEquals(1, service.listarAtivos(TestTenants.PILOT).size(),
                    "O conflito não pode ter criado um segundo agendamento");
            return;
        }
        throw new AssertionError("Esperava conflito no mesmo negócio e horário");
    }
}
