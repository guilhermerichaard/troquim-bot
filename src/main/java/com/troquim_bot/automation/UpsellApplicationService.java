package com.troquim_bot.automation;

import com.troquim_bot.application.availability.AvailabilityApplicationService;
import com.troquim_bot.application.catalog.ConsultarCatalogo;
import com.troquim_bot.application.service.ServiceApplicationService;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class UpsellApplicationService {
    private final UpsellRuleRepository rules;
    private final BookingAutomationPolicyRepository policies;
    private final ServiceApplicationService services;
    private final ConsultarCatalogo catalog;
    private final AvailabilityApplicationService availability;

    public UpsellApplicationService(UpsellRuleRepository rules,
                                    BookingAutomationPolicyRepository policies,
                                    ServiceApplicationService services,
                                    ConsultarCatalogo catalog,
                                    AvailabilityApplicationService availability) {
        this.rules=rules;this.policies=policies;this.services=services;
        this.catalog=catalog;this.availability=availability;
    }

    @Transactional
    public RuleView configure(BusinessId businessId,ServiceId base,ServiceId addon,boolean active){
        if(businessId==null||base==null||addon==null)throw new IllegalArgumentException("Serviços são obrigatórios");
        if(base.equals(addon))throw new IllegalArgumentException("Upsell precisa ser um serviço diferente");
        var baseService=services.buscarPorId(businessId,base)
                .orElseThrow(()->new IllegalArgumentException("Serviço base não encontrado"));
        var addonService=services.buscarPorId(businessId,addon)
                .orElseThrow(()->new IllegalArgumentException("Serviço adicional não encontrado"));
        var saved=rules.save(new UpsellRuleRepository.Rule(businessId,base,addon,active));
        return new RuleView(saved.baseServiceId(),baseService.getNome(),
                saved.addonServiceId(),addonService.getNome(),saved.active());
    }

    @Transactional
    public void remove(BusinessId businessId,ServiceId base){
        rules.delete(businessId,base);
    }

    @Transactional(readOnly=true)
    public List<RuleView> list(BusinessId businessId){
        return rules.findByBusiness(businessId).stream().map(rule->{
            String baseName=services.buscarPorId(businessId,rule.baseServiceId())
                    .map(com.troquim_bot.service.Service::getNome).orElse("Serviço");
            String addonName=services.buscarPorId(businessId,rule.addonServiceId())
                    .map(com.troquim_bot.service.Service::getNome).orElse("Adicional");
            return new RuleView(rule.baseServiceId(),baseName,
                    rule.addonServiceId(),addonName,rule.active());
        }).toList();
    }

    @Transactional(readOnly=true)
    public Optional<Offer> recommend(BusinessId businessId,
                                     ServiceId baseServiceId,
                                     ProfessionalId professionalId,
                                     LocalDate date,
                                     LocalTime baseStart) {
        if(!policies.get(businessId).upsellEnabled())return Optional.empty();
        var rule=rules.find(businessId,baseServiceId).filter(UpsellRuleRepository.Rule::active).orElse(null);
        if(rule==null)return Optional.empty();

        var base=catalog.porServico(businessId,baseServiceId).orElse(null);
        var addon=catalog.porServico(businessId,rule.addonServiceId()).orElse(null);
        if(base==null||addon==null)return Optional.empty();
        if(addon.profissionais().stream().noneMatch(p->p.id().equals(professionalId)))return Optional.empty();

        LocalTime addonStart=baseStart.plus(base.duracao());
        if(!availability.estaLivre(businessId,addon.id(),professionalId,date,addonStart))return Optional.empty();

        return Optional.of(new Offer(
                addon.id(),addon.nome(),
                addon.preco().map(p->p.getAmount().doubleValue()).orElse(null),
                professionalId,date,addonStart,addon.duracao().toMinutes()));
    }

    public record RuleView(ServiceId baseServiceId,String baseServiceName,
                           ServiceId addonServiceId,String addonServiceName,boolean active){}
    public record Offer(ServiceId serviceId,String serviceName,Double price,
                        ProfessionalId professionalId,LocalDate date,LocalTime startTime,
                        long durationMinutes){}
}
