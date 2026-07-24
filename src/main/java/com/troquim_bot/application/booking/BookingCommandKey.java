package com.troquim_bot.application.booking;

import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.service.ServiceId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Identidade de um COMANDO de confirmação de agendamento.
 *
 * Quatro partes, com papéis distintos:
 * <ul>
 *   <li>{@code businessId} — o tenant dono do comando. Guardado à parte porque a regra do
 *       MVP ("uma base conclui no máximo um agendamento") precisa ser ESCOPADA por
 *       negócio: dois salões distintos com a mesma base (cenário adversário) têm de poder
 *       concluir cada um o seu. O businessId também entra no fingerprint, mas de lá não é
 *       recuperável (hash) — por isso viaja explícito;</li>
 *   <li>{@code base} — a origem do comando (o {@code flow_token}). A regra do MVP incide
 *       sobre o par ({@code businessId}, {@code base}), nunca sobre a base sozinha;</li>
 *   <li>{@code valor} — a chave única do comando: {@code <base>:<fingerprint>}. É ela que
 *       vai para a coluna {@code command_key} (PK) e sustenta a idempotência de comando;</li>
 *   <li>{@code fingerprint} — SHA-256 do payload canônico (que já inclui o businessId),
 *       guardado separadamente para que um acerto de chave possa ser CONFERIDO.</li>
 * </ul>
 *
 * A base sozinha (ex.: o {@code flow_token}) NÃO serve como chave de comando: o token
 * identifica a sessão inteira, e uma sessão pode legitimamente tentar confirmar escolhas
 * diferentes. Incluir o fingerprint faz "mesmo token, dados diferentes" ser um comando
 * diferente — que é exatamente o defeito apontado na auditoria.
 *
 * O payload canônico usa apenas IDs estáveis e valores normalizados, em ORDEM FIXA. Nada
 * de nome visível (muda sem mudar o comando) e nada de serialização de JSON (a ordem dos
 * campos não é garantida pelo cliente).
 */
public record BookingCommandKey(UUID businessId, String base, String valor, String fingerprint) {

    /** Cabe folgado em {@code VARCHAR(160)}: base limitada + ':' + 64 hex. */
    public static final int TAMANHO_MAX = 160;

    private static final String SEPARADOR_CAMPO = "|";
    private static final int BASE_MAX = 80;

    public BookingCommandKey {
        if (businessId == null) {
            throw new IllegalArgumentException("businessId da command key é obrigatório");
        }
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("base da command key não pode ser vazia");
        }
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("command key não pode ser vazia");
        }
        if (valor.length() > TAMANHO_MAX) {
            throw new IllegalArgumentException("command key excede " + TAMANHO_MAX + " caracteres");
        }
        if (fingerprint == null || fingerprint.length() != 64) {
            throw new IllegalArgumentException("fingerprint deve ser SHA-256 em hex");
        }
    }

    /**
     * Monta a chave a partir do contexto CONFIÁVEL do servidor.
     *
     * @param base       origem estável do comando — o {@code flow_token} da sessão, ou o
     *                   id da mensagem inbound. Nunca vem do corpo enviado pelo cliente.
     * @param businessId tenant, do {@link com.troquim_bot.business.TenantProvider}
     * @param telefone   identidade do cliente, resolvida pelo servidor (não digitada)
     */
    public static BookingCommandKey de(String base, UUID businessId, String telefone,
                                       ServiceId serviceId, ProfessionalId professionalId,
                                       LocalDate data, LocalTime horario) {
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("base da command key é obrigatória");
        }

        // Ordem fixa e explícita. Alterar esta sequência invalida as chaves já gravadas:
        // comandos antigos deixariam de ser reconhecidos como repetição.
        String canonico = String.join(SEPARADOR_CAMPO,
                "v1",
                texto(businessId),
                BookingIds.normalizar(telefone),
                texto(serviceId == null ? null : serviceId.getValue()),
                texto(professionalId == null ? null : professionalId.getValue()),
                texto(data),
                horario == null ? "" : horario.toString());

        if (businessId == null) {
            throw new IllegalArgumentException("businessId é obrigatório");
        }

        String fingerprint = sha256(canonico);
        String baseLimitada = base.length() > BASE_MAX ? sha256(base).substring(0, BASE_MAX) : base;
        return new BookingCommandKey(businessId, baseLimitada, baseLimitada + ":" + fingerprint, fingerprint);
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
