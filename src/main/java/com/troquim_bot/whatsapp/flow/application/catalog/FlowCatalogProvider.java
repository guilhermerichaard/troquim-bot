package com.troquim_bot.whatsapp.flow.application.catalog;

import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Fronteira de catálogo do Flow: a ÚNICA fonte de serviços e profissionais que as telas
 * conhecem.
 *
 * PENDÊNCIA CONHECIDA — o projeto ainda não tem catálogo persistido: não há seed de
 * Service nem de Professional, e o menu de conversa (StrictMvpMenuService) trabalha com
 * as mesmas cinco chaves fixas. Este provider concentra essa limitação num ponto só, com
 * as MESMAS chaves canônicas usadas pelo booking, para que trocar por um catálogo real
 * (repositório + seed por tenant) seja mudança local. Nenhum handler conhece a lista.
 *
 * Duração padrão de 1h porque é a duração que o domínio efetivamente aplica hoje ao criar
 * Reservation/Appointment. Preço não é exposto porque não existe no domínio.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowCatalogProvider {

    /** Mesmo id determinístico do profissional único do MVP usado pelo booking. */
    public static final String PROFISSIONAL_QUALQUER_ID = "qualquer";

    private static final Duration DURACAO_PADRAO = Duration.ofHours(1);

    private static final ProfessionalId PROFISSIONAL_PADRAO = ProfessionalId.from(
            UUID.nameUUIDFromBytes("professional:troquim-mvp-default".getBytes(StandardCharsets.UTF_8)));

    private static final List<FlowServiceOption> SERVICOS = List.of(
            new FlowServiceOption("unha", "Unhas", DURACAO_PADRAO),
            new FlowServiceOption("cabelo", "Cabelo", DURACAO_PADRAO),
            new FlowServiceOption("sobrancelha", "Sobrancelha", DURACAO_PADRAO),
            new FlowServiceOption("cilios", "Cílios", DURACAO_PADRAO),
            new FlowServiceOption("pe e mao", "Pé e mão", DURACAO_PADRAO));

    public List<FlowServiceOption> servicos() {
        return SERVICOS;
    }

    /** Resolve um id vindo do cliente. Vazio = id desconhecido, tratado pelo handler. */
    public Optional<FlowServiceOption> servicoPorId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalizado = id.trim().toLowerCase(Locale.ROOT);
        return SERVICOS.stream().filter(s -> s.id().equals(normalizado)).findFirst();
    }

    /**
     * Profissionais habilitados para um serviço. Hoje o salão-piloto tem um único
     * profissional, então a lista independe do serviço — mas a assinatura já recebe o
     * serviço para que a regra real de habilitação entre aqui sem mudar os handlers.
     */
    public List<FlowProfessionalOption> profissionaisPara(FlowServiceOption servico) {
        return List.of(new FlowProfessionalOption(
                PROFISSIONAL_QUALQUER_ID, "Qualquer profissional", PROFISSIONAL_PADRAO));
    }

    /**
     * Resolve um profissional para um serviço. Vazio quando o id é desconhecido OU
     * quando o profissional não atende aquele serviço — o cliente pode ter reenviado
     * uma combinação inválida, e a tela não é autoridade sobre compatibilidade.
     */
    public Optional<FlowProfessionalOption> profissionalPara(FlowServiceOption servico, String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalizado = id.trim().toLowerCase(Locale.ROOT);
        return profissionaisPara(servico).stream()
                .filter(p -> p.id().equals(normalizado))
                .findFirst();
    }

    public ProfessionalId profissionalPadrao() {
        return PROFISSIONAL_PADRAO;
    }
}
