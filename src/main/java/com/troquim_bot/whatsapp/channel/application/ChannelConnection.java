package com.troquim_bot.whatsapp.channel.application;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Vínculo entre um tenant e a conta WhatsApp Business que ele conectou pela Meta.
 *
 * A credencial vive AQUI já cifrada ({@link EncryptedCredential}) — este objeto nunca
 * carrega o access token em claro, então nem um log acidental nem uma serialização
 * distraída conseguem vazá-lo. Quem decifra é exclusivamente o gateway de saída, no
 * momento de usar, através de {@link ChannelCredentialCipher}.
 *
 * {@code stateToken} é o nonce do Embedded Signup: existe só entre o início e a
 * finalização, é de uso único e é anulado ao ser consumido.
 */
public record ChannelConnection(UUID id,
                                UUID businessId,
                                ChannelConnectionStatus status,
                                Optional<String> stateToken,
                                Optional<LocalDateTime> stateExpiraEm,
                                Optional<String> wabaId,
                                Optional<String> phoneNumberId,
                                Optional<EncryptedCredential> credencial,
                                Optional<String> falhaMotivo,
                                LocalDateTime criadoEm,
                                LocalDateTime atualizadoEm) {

    /** Início do Embedded Signup: reserva o tenant e emite o nonce. */
    public static ChannelConnection pendente(UUID id, UUID businessId, String stateToken,
                                             LocalDateTime stateExpiraEm, LocalDateTime agora) {
        return new ChannelConnection(id, businessId, ChannelConnectionStatus.PENDENTE,
                Optional.of(stateToken), Optional.of(stateExpiraEm),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                agora, agora);
    }

    /**
     * Conclusão bem-sucedida. O nonce é descartado no mesmo movimento em que a
     * credencial entra: o mesmo code nunca pode ser trocado duas vezes.
     */
    public ChannelConnection conectada(String wabaId, String phoneNumberId,
                                       EncryptedCredential credencial, LocalDateTime agora) {
        return new ChannelConnection(id, businessId, ChannelConnectionStatus.CONECTADO,
                Optional.empty(), Optional.empty(),
                Optional.ofNullable(wabaId), Optional.ofNullable(phoneNumberId),
                Optional.of(credencial), Optional.empty(), criadoEm, agora);
    }

    /**
     * Falha na finalização. O nonce também é descartado — recomeçar exige um novo
     * início, senão um code recusado poderia ser repetido indefinidamente.
     */
    public ChannelConnection falhou(String motivo, LocalDateTime agora) {
        return new ChannelConnection(id, businessId, ChannelConnectionStatus.FALHOU,
                Optional.empty(), Optional.empty(), wabaId, phoneNumberId, credencial,
                Optional.ofNullable(motivo), criadoEm, agora);
    }

    /** O nonce ainda vale? Vencido é tratado igual a inexistente. */
    public boolean stateUtilizavel(LocalDateTime agora) {
        return status == ChannelConnectionStatus.PENDENTE
                && stateToken.isPresent()
                && stateExpiraEm.map(agora::isBefore).orElse(false);
    }

    public boolean pertenceAoTenant(UUID businessId) {
        return businessId != null && businessId.equals(this.businessId);
    }

    /**
     * Blindagem contra vazamento por log/depuração: a representação textual jamais
     * inclui credencial nem nonce. Um {@code log.info("{}", conexao)} distraído
     * imprime só identificadores e estado.
     */
    @Override
    public String toString() {
        return "ChannelConnection[id=" + id
                + ", businessId=" + businessId
                + ", status=" + status
                + ", wabaId=" + wabaId.orElse("-")
                + ", phoneNumberId=" + phoneNumberId.orElse("-")
                + ", credencial=" + (credencial.isPresent() ? "<cifrada>" : "-")
                + ", stateToken=" + (stateToken.isPresent() ? "<oculto>" : "-")
                + "]";
    }
}
