package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.BusinessCalendar;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.repository.BusinessCalendarRepository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter JPA do calendário do negócio — o repositório de PRODUÇÃO.
 *
 * Só converte e persiste. A regra de sobreposição entre períodos do mesmo dia é do Value
 * Object {@link BusinessHours}: reconstituir aqui passa pelo construtor dele, então um dado
 * inconsistente que tenha entrado por fora da aplicação falha ao ser LIDO, em vez de virar
 * horário duplicado na tela da cliente.
 *
 * Persiste fisicamente em {@code business_hours} — mesma tabela de sempre, uma linha por
 * período do calendário.
 */
@Repository
@Primary
public class JpaBusinessCalendarRepository implements BusinessCalendarRepository {

    private final SpringDataBusinessHoursRepository springDataRepository;

    public JpaBusinessCalendarRepository(SpringDataBusinessHoursRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    /**
     * Substituição atômica: apaga o calendário atual e grava o novo na MESMA transação.
     * Sem isso, uma falha no meio deixaria o negócio com metade da semana cadastrada — e
     * metade de um expediente é pior do que nenhum, porque parece configurado.
     */
    @Override
    @Transactional
    public void salvar(BusinessCalendar calendario) {
        if (calendario == null) {
            throw new IllegalArgumentException("Calendário é obrigatório");
        }
        BusinessId businessId = calendario.getBusinessId();

        springDataRepository.deleteByBusinessId(businessId.getValue());

        LocalDateTime agora = LocalDateTime.now();
        List<BusinessHoursJpaEntity> linhas = new ArrayList<>();
        calendario.getExpediente().porDiaDaSemana().forEach((dia, periodos) ->
                periodos.forEach(periodo ->
                        linhas.add(BusinessHoursJpaEntity.de(businessId, dia, periodo, agora))));
        springDataRepository.saveAll(linhas);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessCalendar buscar(BusinessId businessId) {
        if (businessId == null) {
            return null;
        }
        Map<DiaSemana, List<IntervaloDeHorario>> porDia = new EnumMap<>(DiaSemana.class);
        for (BusinessHoursJpaEntity linha : springDataRepository.findByBusinessId(businessId.getValue())) {
            porDia.computeIfAbsent(linha.getDiaSemana(), d -> new ArrayList<>())
                    .add(linha.paraPeriodo());
        }
        BusinessHours expediente = porDia.isEmpty()
                ? BusinessHours.naoConfigurado()
                : BusinessHours.deSemana(porDia);
        return new BusinessCalendar(businessId, expediente);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean configurado(BusinessId businessId) {
        return businessId != null && springDataRepository.existsByBusinessId(businessId.getValue());
    }
}
