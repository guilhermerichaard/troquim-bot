package com.troquim_bot.application.conversation;

import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.business.TenantProvider;
import com.troquim_bot.repository.BusinessCalendarRepository;
import com.troquim_bot.repository.BusinessPublicProfileRepository;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Projecao somente-leitura dos fatos que a conversa pode afirmar sobre o negocio.
 *
 * Esta classe nao interpreta intencao e nao decide agenda. Ela apenas le as fontes
 * canonicas do dominio/application para impedir respostas duplicadas ou hardcoded no canal.
 */
@Component
public class BusinessConversationFacts {

    public record ServiceFact(String name,
                              String description,
                              BigDecimal price,
                              String currency,
                              long durationMinutes) {

        public boolean hasPrice() {
            return price != null && currency != null && !currency.isBlank();
        }

        public boolean hasDescription() {
            return description != null && !description.isBlank();
        }
    }

    private final TenantProvider tenantProvider;
    private final ConsultarCatalogo consultarCatalogo;
    private final BusinessCalendarRepository businessCalendarRepository;
    private final BusinessPublicProfileRepository businessPublicProfileRepository;

    public BusinessConversationFacts(TenantProvider tenantProvider,
                                     ConsultarCatalogo consultarCatalogo,
                                     BusinessCalendarRepository businessCalendarRepository,
                                     BusinessPublicProfileRepository businessPublicProfileRepository) {
        this.tenantProvider = tenantProvider;
        this.consultarCatalogo = consultarCatalogo;
        this.businessCalendarRepository = businessCalendarRepository;
        this.businessPublicProfileRepository = businessPublicProfileRepository;
    }

    public List<ServiceFact> services() {
        var businessId = tenantProvider.currentBusinessId();
        return consultarCatalogo.consultar(businessId).itens().stream()
                .map(item -> new ServiceFact(
                        item.nome(),
                        item.descricao(),
                        item.preco().map(money -> money.getAmount()).orElse(null),
                        item.preco().map(money -> money.getCurrency()).orElse(null),
                        item.duracao().toMinutes()))
                .toList();
    }

    public Optional<String> address() {
        var businessId = tenantProvider.currentBusinessId();
        return businessPublicProfileRepository.buscarPorBusinessId(businessId)
                .map(profile -> profile.getEnderecoPublico())
                .filter(value -> value != null && !value.isBlank());
    }

    public Optional<String> publicName() {
        var businessId = tenantProvider.currentBusinessId();
        return businessPublicProfileRepository.buscarPorBusinessId(businessId)
                .map(profile -> profile.getNomePublico())
                .filter(value -> value != null && !value.isBlank());
    }

    public Optional<String> openingHoursSummary() {
        var businessId = tenantProvider.currentBusinessId();
        if (!businessCalendarRepository.configurado(businessId)) {
            return Optional.empty();
        }

        BusinessHours hours = businessCalendarRepository.buscar(businessId).getExpediente();
        if (hours == null || hours.naoTemExpediente()) {
            return Optional.empty();
        }

        List<String> parts = new ArrayList<>();
        for (DiaSemana day : DiaSemana.values()) {
            List<IntervaloDeHorario> periods = hours.periodosDe(day);
            if (periods.isEmpty()) {
                continue;
            }

            String periodText = periods.stream()
                    .map(period -> formatTime(period.inicio()) + "-" + formatTime(period.fim()))
                    .reduce((a, b) -> a + " e " + b)
                    .orElse("");
            parts.add(dayLabel(day) + " " + periodText);
        }

        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", parts));
    }

    private static String dayLabel(DiaSemana day) {
        return switch (day) {
            case SEGUNDA -> "seg";
            case TERCA -> "ter";
            case QUARTA -> "qua";
            case QUINTA -> "qui";
            case SEXTA -> "sex";
            case SABADO -> "sáb";
            case DOMINGO -> "dom";
        };
    }

    private static String formatTime(LocalTime time) {
        if (time.getMinute() == 0) {
            return time.getHour() + "h";
        }
        return String.format(Locale.of("pt", "BR"), "%d:%02d", time.getHour(), time.getMinute());
    }
}
