package com.troquim_bot.whatsapp.flow.application.catalog;

import com.troquim_bot.professional.ProfessionalId;

/**
 * Profissional oferecido na tela PROFISSIONAL.
 *
 * Como no serviço, a identidade é a REAL do catálogo persistido: o id trafegado é o UUID
 * do {@link ProfessionalId}, e é ele que volta do Flow para ser revalidado contra a
 * habilitação por serviço. Não existe mais o id sintético "qualquer" — ele pressupunha um
 * profissional único por negócio, premissa que o catálogo por tenant não tem.
 *
 * @param professionalId identidade de domínio
 * @param titulo         rótulo exibido (nome do profissional)
 */
public record FlowProfessionalOption(ProfessionalId professionalId, String titulo) {

    public FlowProfessionalOption {
        if (professionalId == null) {
            throw new IllegalArgumentException("ProfessionalId é obrigatório numa opção do Flow");
        }
    }

    public String id() {
        return professionalId.getValue().toString();
    }
}
