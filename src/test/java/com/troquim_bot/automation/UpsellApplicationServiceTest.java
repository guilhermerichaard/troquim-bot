package com.troquim_bot.automation;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.service.ServiceApplicationService;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.common.valueobject.Money;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.Service;
import com.troquim_bot.service.ServiceDuration;
import com.troquim_bot.service.ServiceId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpsellApplicationServiceTest {

    @Test
    void recommendsOnlyExplicitAddonThatFitsSameProfessionalAgenda() {
        BusinessId business = BusinessId.from(java.util.UUID.randomUUID());
        ServiceId baseId = ServiceId.generate();
        ServiceId addonId = ServiceId.generate();
        ProfessionalId professionalId = ProfessionalId.generate();
        LocalDate date = LocalDate.of(2026, 9, 25);
        LocalTime baseStart = LocalTime.of(14, 0);

        UpsellRuleRepository rules = mock(UpsellRuleRepository.class);
        BookingAutomationPolicyRepository policies = mock(BookingAutomationPolicyRepository.class);
        ServiceApplicationService services = mock(ServiceApplicationService.class);
        ConsultarCatalogo catalog = mock(ConsultarCatalogo.class);
        AvailabilityApplicationService availability = mock(AvailabilityApplicationService.class);

        when(policies.get(business)).thenReturn(new BookingAutomationPolicy(true, 24, 0, true));
        when(rules.find(business, baseId)).thenReturn(Optional.of(
                new UpsellRuleRepository.Rule(business, baseId, addonId, true)));

        var baseItem = new ConsultarCatalogo.ItemDeCatalogo(
                baseId, "Corte", "Corte", Duration.ofMinutes(45), Optional.of(Money.of(50, "BRL")),
                List.of(new ConsultarCatalogo.ProfissionalDoCatalogo(professionalId, "Gui")));
        var addonItem = new ConsultarCatalogo.ItemDeCatalogo(
                addonId, "Barba", "Barba", Duration.ofMinutes(30), Optional.of(Money.of(25, "BRL")),
                List.of(new ConsultarCatalogo.ProfissionalDoCatalogo(professionalId, "Gui")));

        when(catalog.porServico(business, baseId)).thenReturn(Optional.of(baseItem));
        when(catalog.porServico(business, addonId)).thenReturn(Optional.of(addonItem));
        when(availability.estaLivre(business, addonId, professionalId, date, LocalTime.of(14,45)))
                .thenReturn(true);

        var service = new UpsellApplicationService(rules, policies, services, catalog, availability);
        var offer = service.recommend(business, baseId, professionalId, date, baseStart);

        assertTrue(offer.isPresent());
        assertEquals(addonId, offer.get().serviceId());
        assertEquals(LocalTime.of(14,45), offer.get().startTime());
        assertEquals(25.0, offer.get().price());
    }

    @Test
    void doesNotRecommendWhenAutomationIsDisabled() {
        BusinessId business = BusinessId.from(java.util.UUID.randomUUID());
        ServiceId baseId = ServiceId.generate();
        ProfessionalId professionalId = ProfessionalId.generate();

        UpsellRuleRepository rules = mock(UpsellRuleRepository.class);
        BookingAutomationPolicyRepository policies = mock(BookingAutomationPolicyRepository.class);
        when(policies.get(business)).thenReturn(new BookingAutomationPolicy(true, 24, 0, false));

        var service = new UpsellApplicationService(
                rules, policies, mock(ServiceApplicationService.class),
                mock(ConsultarCatalogo.class), mock(AvailabilityApplicationService.class));

        assertTrue(service.recommend(
                business, baseId, professionalId,
                LocalDate.of(2026,9,25), LocalTime.of(14,0)).isEmpty());
        verifyNoInteractions(rules);
    }
}
