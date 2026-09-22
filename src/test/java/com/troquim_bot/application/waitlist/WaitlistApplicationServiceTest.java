package com.troquim_bot.application.waitlist;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.WaitlistRepository;
import com.troquim_bot.service.ServiceId;
import com.troquim_bot.waitlist.WaitlistEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitlistApplicationServiceTest {

    @Test
    void avisaSomenteAPrimeiraEsperaCompativelEMarcaComoNotificada() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway notifications = new RecordingGateway(true);
        WaitlistApplicationService service =
                new WaitlistApplicationService(repository, List.of(notifications));

        BusinessId business = BusinessId.generate();
        ServiceId servico = ServiceId.generate();
        ProfessionalId profissional = ProfessionalId.generate();
        LocalDate sexta = LocalDate.of(2026, 9, 25);

        WaitlistEntry primeiro = service.join(
                business, "5511999990001", servico, profissional,
                sexta, LocalTime.of(16, 0), null);
        service.join(
                business, "5511999990002", servico, profissional,
                sexta, LocalTime.of(16, 0), null);

        assertTrue(service.slotReleased(
                business, servico, profissional, "Manicure", sexta, LocalTime.of(17, 0)));

        assertEquals("+5511999990001", notifications.lastPhone);
        assertEquals(com.troquim_bot.waitlist.WaitlistStatus.NOTIFIED, primeiro.getStatus());
        assertEquals(1, notifications.calls);
    }

    @Test
    void falhaDeTransporteNaoConsomeLugarNaFila() {
        FakeRepository repository = new FakeRepository();
        WaitlistApplicationService service =
                new WaitlistApplicationService(repository, List.of(new RecordingGateway(false)));

        BusinessId business = BusinessId.generate();
        ServiceId servico = ServiceId.generate();
        ProfessionalId profissional = ProfessionalId.generate();
        LocalDate sexta = LocalDate.of(2026, 9, 25);

        WaitlistEntry entry = service.join(
                business, "5511999990001", servico, profissional,
                sexta, LocalTime.of(16, 0), LocalTime.of(18, 0));

        assertFalse(service.slotReleased(
                business, servico, profissional, "Manicure", sexta, LocalTime.of(17, 0)));
        assertEquals(com.troquim_bot.waitlist.WaitlistStatus.ACTIVE, entry.getStatus());
    }

    @Test
    void mesmaPreferenciaEhIdempotenteMasOutraJanelaPodeCoexistir() {
        FakeRepository repository = new FakeRepository();
        WaitlistApplicationService service =
                new WaitlistApplicationService(repository, List.of());

        BusinessId business = BusinessId.generate();
        ServiceId servico = ServiceId.generate();
        ProfessionalId profissional = ProfessionalId.generate();
        LocalDate sexta = LocalDate.of(2026, 9, 25);

        WaitlistEntry a = service.join(
                business, "5511999990001", servico, profissional,
                sexta, LocalTime.of(16, 0), null);
        WaitlistEntry b = service.join(
                business, "5511999990001", servico, profissional,
                sexta, LocalTime.of(16, 0), null);
        WaitlistEntry c = service.join(
                business, "5511999990001", servico, profissional,
                sexta, LocalTime.of(18, 0), null);

        assertEquals(a.getId(), b.getId());
        assertTrue(!a.getId().equals(c.getId()));
        assertEquals(2, repository.entries.size());
    }

    private static final class RecordingGateway implements WaitlistNotificationGateway {
        private final boolean result;
        private int calls;
        private String lastPhone;

        private RecordingGateway(boolean result) {
            this.result = result;
        }

        @Override
        public boolean notifySlotAvailable(UUID waitlistId,
                                           String phoneE164,
                                           String serviceName,
                                           LocalDate date,
                                           LocalTime time) {
            calls++;
            lastPhone = phoneE164;
            return result;
        }
    }

    private static final class FakeRepository implements WaitlistRepository {
        private final List<WaitlistEntry> entries = new ArrayList<>();

        @Override
        public WaitlistEntry save(WaitlistEntry entry) {
            entries.removeIf(existing -> existing.getId().equals(entry.getId()));
            entries.add(entry);
            return entry;
        }

        @Override
        public Optional<WaitlistEntry> findById(UUID id) {
            return entries.stream().filter(entry -> entry.getId().equals(id)).findFirst();
        }

        @Override
        public List<WaitlistEntry> findActiveByBusiness(BusinessId businessId) {
            return entries.stream()
                    .filter(entry -> entry.getBusinessId().equals(businessId))
                    .filter(entry -> entry.getStatus()
                            == com.troquim_bot.waitlist.WaitlistStatus.ACTIVE)
                    .toList();
        }

        @Override
        public Optional<WaitlistEntry> findActiveRequest(BusinessId businessId,
                                                         String phoneE164,
                                                         ServiceId serviceId,
                                                         ProfessionalId professionalId,
                                                         LocalDate requestedDate,
                                                         LocalTime earliestTime,
                                                         LocalTime latestTime) {
            return entries.stream()
                    .filter(entry -> entry.getBusinessId().equals(businessId))
                    .filter(entry -> entry.getPhoneE164().equals(phoneE164))
                    .filter(entry -> entry.getServiceId().equals(serviceId))
                    .filter(entry -> entry.getProfessionalId().equals(professionalId))
                    .filter(entry -> java.util.Objects.equals(entry.getRequestedDate(), requestedDate))
                    .filter(entry -> java.util.Objects.equals(entry.getEarliestTime(), earliestTime))
                    .filter(entry -> java.util.Objects.equals(entry.getLatestTime(), latestTime))
                    .filter(entry -> entry.getStatus()
                            == com.troquim_bot.waitlist.WaitlistStatus.ACTIVE)
                    .findFirst();
        }
    }
}
