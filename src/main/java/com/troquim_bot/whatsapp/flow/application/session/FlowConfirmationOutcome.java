package com.troquim_bot.whatsapp.flow.application.session;

/**
 * Desfecho persistido de um CONFIRM, usado como ESTADO DE APRESENTAÇÃO da sessão.
 *
 * NÃO é mecanismo de idempotência. Já foi — e estava errado: o {@code flow_token}
 * identifica a sessão, não o comando. A idempotência real é por command key, em
 * {@code booking_idempotency}, na mesma transação do agendamento.
 *
 * Guarda apenas o suficiente para remontar a tela de sucesso numa reentrega — sem
 * dado pessoal além do que o próprio cliente escolheu na tela. Não substitui o
 * Appointment: é o recibo da interação, não a fonte da verdade do agendamento.
 *
 * @param servicoNome rótulo do serviço confirmado
 * @param dataIso     data no formato ISO {@code yyyy-MM-dd}
 * @param horario     horário no formato {@code HH:mm}
 */
public record FlowConfirmationOutcome(String servicoNome, String dataIso, String horario) {
}
