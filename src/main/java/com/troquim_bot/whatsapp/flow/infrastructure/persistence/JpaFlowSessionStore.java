package com.troquim_bot.whatsapp.flow.infrastructure.persistence;

import com.troquim_bot.whatsapp.flow.application.session.FlowConfirmationOutcome;
import com.troquim_bot.whatsapp.flow.application.session.FlowSession;
import com.troquim_bot.whatsapp.flow.application.session.FlowSessionStore;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JPA das sessões de Flow.
 *
 * O token é gerado com {@link SecureRandom} (256 bits, base64 url-safe): opaco,
 * imprevisível e sem relação derivável com o telefone. Adivinhar um token é a única via
 * de ataque contra a amarração cliente↔sessão, e 256 bits a fecham.
 *
 * O desfecho gravado aqui é ESTADO DE APRESENTAÇÃO da navegação — não idempotência.
 * A proteção contra confirmação duplicada vive em {@code booking_idempotency}, com chave
 * de comando e na mesma transação do agendamento.
 */
@Component
@ConditionalOnWhatsAppFlow
public class JpaFlowSessionStore implements FlowSessionStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final SpringDataWhatsAppFlowSessionRepository repository;

    public JpaFlowSessionStore(SpringDataWhatsAppFlowSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public FlowSession abrir(String telefone, UUID businessId, LocalDateTime expiraEm) {
        if (telefone == null || telefone.isBlank()) {
            throw new IllegalArgumentException("Telefone é obrigatório para abrir a sessão do Flow");
        }
        if (businessId == null) {
            throw new IllegalArgumentException("BusinessId é obrigatório para abrir a sessão do Flow");
        }

        LocalDateTime agora = LocalDateTime.now();
        WhatsAppFlowSessionJpaEntity entidade = new WhatsAppFlowSessionJpaEntity(
                gerarToken(), telefone, businessId, expiraEm, agora);
        return paraSessao(repository.saveAndFlush(entidade), agora);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowSession> buscar(String flowToken) {
        if (flowToken == null || flowToken.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime agora = LocalDateTime.now();
        return repository.findById(flowToken).map(e -> paraSessao(e, agora));
    }

    @Override
    @Transactional
    public FlowConfirmationOutcome registrarConfirmacao(String flowToken,
                                                        FlowConfirmationOutcome outcome) {
        WhatsAppFlowSessionJpaEntity entidade = repository.findById(flowToken)
                .orElseThrow(() -> new IllegalStateException("Sessão de Flow inexistente"));

        // Primeiro CONFIRM vence: uma reentrega nunca sobrescreve o desfecho original.
        if (entidade.temConfirmacao()) {
            return outcomeDe(entidade);
        }

        entidade.registrarConfirmacao(outcome.servicoNome(), outcome.dataIso(), outcome.horario(),
                LocalDateTime.now());
        repository.saveAndFlush(entidade);
        return outcome;
    }

    /**
     * Invalida numa transação PRÓPRIA: é compensação de um envio que falhou, e precisa
     * persistir mesmo que a transação do caso de uso chamador venha a ser desfeita.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void invalidar(String flowToken) {
        repository.findById(flowToken).ifPresent(entidade -> {
            entidade.invalidar(LocalDateTime.now());
            repository.saveAndFlush(entidade);
        });
    }

    private static String gerarToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static FlowSession paraSessao(WhatsAppFlowSessionJpaEntity e, LocalDateTime agora) {
        return new FlowSession(e.getFlowToken(), e.getTelefone(), e.getBusinessId(),
                e.statusEfetivo(agora), e.getCriadoEm(), e.getExpiraEm(),
                e.temConfirmacao() ? Optional.of(outcomeDe(e)) : Optional.empty());
    }

    private static FlowConfirmationOutcome outcomeDe(WhatsAppFlowSessionJpaEntity e) {
        return new FlowConfirmationOutcome(e.getConfirmadoServico(), e.getConfirmadoData(),
                e.getConfirmadoHorario());
    }
}
