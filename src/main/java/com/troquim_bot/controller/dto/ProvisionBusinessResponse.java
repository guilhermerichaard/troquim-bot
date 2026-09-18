package com.troquim_bot.controller.dto;

import com.troquim_bot.application.catalog.ProvisionarNegocio;
import com.troquim_bot.business.BusinessPublicProfile;
import com.troquim_bot.professional.Professional;
import com.troquim_bot.service.Service;

import java.util.List;
import java.util.UUID;

public record ProvisionBusinessResponse(
        UUID businessId,
        List<ServiceItem> servicesCreated,
        List<ServiceItem> servicesExisting,
        ProfessionalItem professionalCreated,
        ProfessionalItem professionalExisting,
        List<String> linksAdded,
        boolean businessHoursConfigured,
        int availabilityPeriodsCreated,
        PublicProfileItem publicProfile) {

    public record ServiceItem(UUID id, String name) {
        public static ServiceItem from(Service service) {
            return new ServiceItem(service.getId().getValue(), service.getNome());
        }
    }

    public record ProfessionalItem(UUID id, String name) {
        public static ProfessionalItem from(Professional professional) {
            return new ProfessionalItem(professional.getId().getValue(), professional.getNome());
        }
    }

    public record PublicProfileItem(String slug, String publicName, boolean published) {
        public static PublicProfileItem from(BusinessPublicProfile profile) {
            if (profile == null) {
                return null;
            }
            return new PublicProfileItem(
                    profile.getSlug().getValue(),
                    profile.getNomePublico(),
                    profile.publicado());
        }
    }

    public static ProvisionBusinessResponse from(UUID businessId,
                                                 ProvisionarNegocio.Resultado result,
                                                 BusinessPublicProfile publicProfile) {
        return new ProvisionBusinessResponse(
                businessId,
                result.servicosCriados().stream().map(ServiceItem::from).toList(),
                result.servicosJaExistentes().stream().map(ServiceItem::from).toList(),
                result.profissionalCriado().map(ProfessionalItem::from).orElse(null),
                result.profissionalJaExistente().map(ProfessionalItem::from).orElse(null),
                result.habilitacoesAdicionadas(),
                result.expedienteConfigurado(),
                result.periodosDeDisponibilidadeCriados(),
                PublicProfileItem.from(publicProfile));
    }
}
