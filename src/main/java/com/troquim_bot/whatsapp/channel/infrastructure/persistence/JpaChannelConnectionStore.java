package com.troquim_bot.whatsapp.channel.infrastructure.persistence;

import com.troquim_bot.whatsapp.channel.application.ChannelConnection;
import com.troquim_bot.whatsapp.channel.application.ChannelConnectionStore;
import com.troquim_bot.whatsapp.channel.application.EncryptedCredential;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA da porta {@link ChannelConnectionStore}. */
@Repository
public class JpaChannelConnectionStore implements ChannelConnectionStore {

    private final SpringDataWhatsAppChannelConnectionRepository repository;

    public JpaChannelConnectionStore(SpringDataWhatsAppChannelConnectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public ChannelConnection salvar(ChannelConnection conexao) {
        WhatsAppChannelConnectionJpaEntity entidade = repository.findById(conexao.id())
                .orElseGet(() -> new WhatsAppChannelConnectionJpaEntity(
                        conexao.id(), conexao.businessId()));

        entidade.setOwnerUserId(conexao.ownerUserId().orElse(null));
        entidade.setStatus(conexao.status());
        entidade.setStateToken(conexao.stateToken().orElse(null));
        entidade.setStateExpiraEm(conexao.stateExpiraEm().orElse(null));
        entidade.setWabaId(conexao.wabaId().orElse(null));
        entidade.setPhoneNumberId(conexao.phoneNumberId().orElse(null));
        entidade.setFalhaMotivo(conexao.falhaMotivo().orElse(null));

        // A credencial anterior é sobrescrita por inteiro (inclusive apagada quando
        // ausente): não faz sentido manter cifra órfã de uma conexão que falhou.
        entidade.setCredencialCifrada(conexao.credencial()
                .map(EncryptedCredential::cipherTextBase64).orElse(null));
        entidade.setCredencialIv(conexao.credencial()
                .map(EncryptedCredential::ivBase64).orElse(null));
        entidade.setKeyVersion(conexao.credencial()
                .map(EncryptedCredential::keyVersion).orElse(null));

        entidade.setCriadoEm(conexao.criadoEm());
        entidade.setAtualizadoEm(conexao.atualizadoEm());

        return paraConexao(repository.save(entidade));
    }

    @Override
    public Optional<ChannelConnection> buscarPorTenant(UUID businessId) {
        if (businessId == null) {
            return Optional.empty();
        }
        return repository.findByBusinessId(businessId).map(JpaChannelConnectionStore::paraConexao);
    }

    @Override
    public Optional<ChannelConnection> buscarPorState(String stateToken) {
        if (stateToken == null || stateToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByStateToken(stateToken).map(JpaChannelConnectionStore::paraConexao);
    }

    @Override
    public void remover(UUID businessId) {
        if (businessId == null) {
            return;
        }
        repository.findByBusinessId(businessId).ifPresent(repository::delete);
    }

    private static ChannelConnection paraConexao(WhatsAppChannelConnectionJpaEntity e) {
        Optional<EncryptedCredential> credencial =
                e.getCredencialCifrada() == null || e.getCredencialIv() == null
                        || e.getKeyVersion() == null
                        ? Optional.empty()
                        : Optional.of(new EncryptedCredential(
                                e.getCredencialCifrada(), e.getCredencialIv(), e.getKeyVersion()));

        return new ChannelConnection(
                e.getId(),
                e.getBusinessId(),
                Optional.ofNullable(e.getOwnerUserId()),
                e.getStatus(),
                Optional.ofNullable(e.getStateToken()),
                Optional.ofNullable(e.getStateExpiraEm()),
                Optional.ofNullable(e.getWabaId()),
                Optional.ofNullable(e.getPhoneNumberId()),
                credencial,
                Optional.ofNullable(e.getFalhaMotivo()),
                e.getCriadoEm(),
                e.getAtualizadoEm());
    }
}
