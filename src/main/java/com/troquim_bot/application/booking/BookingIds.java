package com.troquim_bot.application.booking;

import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/**
 * Derivação determinística dos identificadores do MVP.
 *
 * Existe para que a chave de idempotência e a confirmação falem exatamente do MESMO
 * serviço e do MESMO profissional. Antes, a fórmula vivia privada dentro do caso de uso;
 * qualquer outro ponto que precisasse do id teria de reimplementá-la — e uma divergência
 * de normalização produziria chaves que não colidem, quebrando a idempotência sem erro
 * visível.
 *
 * Some quando existir catálogo persistido de Service/Professional: os ids passam a vir
 * do repositório, e só esta classe muda.
 */
public final class BookingIds {

    /** Profissional único do salão-piloto. */
    public static final ProfessionalId PROFISSIONAL_PADRAO =
            ProfessionalId.from(uuidDeterministico("professional:troquim-mvp-default"));

    private BookingIds() {
    }

    /** ServiceId estável a partir da chave canônica do serviço (ex.: {@code "unha"}). */
    public static ServiceId serviceId(String servico) {
        return ServiceId.from(uuidDeterministico("service:" + normalizar(servico)));
    }

    /**
     * Normalização usada nas chaves: sem acentos, minúscula, sem espaços nas bordas.
     * Precisa ser idêntica em todos os pontos, senão "Unhas" e "unhas" viram comandos
     * diferentes.
     */
    public static String normalizar(String texto) {
        String base = texto == null ? "" : texto;
        String semAcentos = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).trim();
    }

    public static UUID uuidDeterministico(String chave) {
        return UUID.nameUUIDFromBytes(chave.getBytes(StandardCharsets.UTF_8));
    }
}
