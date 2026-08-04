package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.availability.IntervaloDeHorario;
import com.troquim_bot.business.BusinessHours;
import com.troquim_bot.business.BusinessId;
import com.troquim_bot.business.DiaSemana;
import com.troquim_bot.repository.BusinessHoursRepository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter JPA do expediente — o repositório de PRODUÇÃO.
 *
 * Só converte e persiste. A regra de sobreposição entre períodos do mesmo dia é do agregado
 * {@link BusinessHours}: reconstituir aqui passa pelo construtor dele, então um dado
 * inconsistente que tenha entrado por fora da aplicação falha ao ser LIDO, em vez de virar
 * horário duplicado na tela da cliente.
 */
@Repository
@Primary
public class JpaBusinessHoursRepository implements BusinessHoursRepository {

    private final SpringDataBusinessHoursRepository springDataRepository;

    public JpaBusinessHoursRepository(SpringDataBusinessHoursRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    /**
     * Substituição atômica: apaga o expediente atual e grava o novo na MESMA transação.
     * Sem isso, uma falha no meio deixaria o negócio com metade da semana cadastrada — e
     * metade de um expediente é pior do que nenhum, porque parece configurado.
     */
    @Override
    @Transactional
    public void salvar(BusinessId businessId, BusinessHours expediente) {
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para salvar expediente");
        }
        if (expediente == null) {
            throw new IllegalArgumentException("Expediente é obrigatório");
        }

        springDataRepository.deleteByBusinessId(businessId.getValue());

        LocalDateTime agora = LocalDateTime.now();
        List<BusinessHoursJpaEntity> linhas = new ArrayList<>();
        expediente.porDiaDaSemana().forEach((dia, periodos) ->
                periodos.forEach(periodo ->
                        linhas.add(BusinessHoursJpaEntity.de(businessId, dia, periodo, agora))));
        springDataRepository.saveAll(linhas);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessHours buscar(BusinessId businessId) {
        if (businessId == null) {
            return BusinessHours.naoConfigurado();
        }
        Map<DiaSemana, List<IntervaloDeHorario>> porDia = new EnumMap<>(DiaSemana.class);
        for (BusinessHoursJpaEntity linha : springDataRepository.findByBusinessId(businessId.getValue())) {
            porDia.computeIfAbsent(linha.getDiaSemana(), d -> new ArrayList<>())
                    .add(linha.paraPeriodo());
        }
        return porDia.isEmpty() ? BusinessHours.naoConfigurado() : BusinessHours.deSemana(porDia);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean configurado(BusinessId businessId) {
        return businessId != null && springDataRepository.existsByBusinessId(businessId.getValue());
    }
}
