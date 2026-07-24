package com.troquim_bot.whatsapp.flow.application.catalog;

import com.troquim_bot.professional.ProfessionalId;

/**
 * Profissional oferecido na tela PROFISSIONAL.
 *
 * @param id            chave estável exposta ao cliente (nunca o UUID interno cru,
 *                      para não vazar identificadores de persistência no contrato)
 * @param titulo        rótulo exibido
 * @param professionalId identidade de domínio correspondente
 */
public record FlowProfessionalOption(String id, String titulo, ProfessionalId professionalId) {
}
