package com.troquim_bot.infrastructure.persistence;

import com.troquim_bot.conversation.state.AppointmentDraft;
import com.troquim_bot.conversation.state.ConversationState;
import com.troquim_bot.conversation.state.ConversationStep;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Snapshot serializável do ConversationState para persistência JSON.
 */
public class ConversationStateSnapshot {

    private String step;
    private List<DraftSnapshot> drafts;
    private String ultimaPergunta;
    private String nome;
    private String nomePerfil;

    /**
     * Construtor padrão para Jackson.
     */
    public ConversationStateSnapshot() {}

    public ConversationStateSnapshot(String step, List<DraftSnapshot> drafts,
                                     String ultimaPergunta, String nome, String nomePerfil) {
        this.step = step;
        this.drafts = drafts;
        this.ultimaPergunta = ultimaPergunta;
        this.nome = nome;
        this.nomePerfil = nomePerfil;
    }

    public static ConversationStateSnapshot fromDomain(ConversationState state) {
        List<DraftSnapshot> draftSnapshots = state.getDrafts().stream()
                .map(DraftSnapshot::fromDomain)
                .collect(Collectors.toList());

        return new ConversationStateSnapshot(
                state.getStep().name(),
                draftSnapshots,
                state.getUltimaPergunta(),
                state.getNome(),
                state.getNomePerfil()
        );
    }

    public ConversationState toDomain(String numero) {
        ConversationState state = new ConversationState(numero);

        if (step != null) {
            state.setStep(ConversationStep.valueOf(step));
        }

        if (drafts != null) {
            List<AppointmentDraft> domainDrafts = drafts.stream()
                    .map(DraftSnapshot::toDomain)
                    .collect(Collectors.toList());
            state.setDrafts(domainDrafts);
        } else {
            state.setDrafts(new ArrayList<>());
        }

        state.setUltimaPergunta(ultimaPergunta);
        state.setNome(nome);
        state.setNomePerfil(nomePerfil);

        return state;
    }

    // ==================== GETTERS E SETTERS (para Jackson) ====================

    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }

    public List<DraftSnapshot> getDrafts() { return drafts; }
    public void setDrafts(List<DraftSnapshot> drafts) { this.drafts = drafts; }

    public String getUltimaPergunta() { return ultimaPergunta; }
    public void setUltimaPergunta(String ultimaPergunta) { this.ultimaPergunta = ultimaPergunta; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getNomePerfil() { return nomePerfil; }
    public void setNomePerfil(String nomePerfil) { this.nomePerfil = nomePerfil; }

    /**
     * Snapshot serializável do AppointmentDraft.
     */
    public static class DraftSnapshot {
        private String commandBase;
        private String servico;
        private String servicoSugerido;
        private String entradaServicoSugerida;
        private String dia;
        private String horario;
        private String nome;
        private boolean confirmado;

        public DraftSnapshot() {}

        public DraftSnapshot(String commandBase, String servico, String servicoSugerido,
                             String entradaServicoSugerida, String dia, String horario,
                             String nome, boolean confirmado) {
            this.commandBase = commandBase;
            this.servico = servico;
            this.servicoSugerido = servicoSugerido;
            this.entradaServicoSugerida = entradaServicoSugerida;
            this.dia = dia;
            this.horario = horario;
            this.nome = nome;
            this.confirmado = confirmado;
        }

        public static DraftSnapshot fromDomain(AppointmentDraft draft) {
            return new DraftSnapshot(
                    draft.getCommandBase(),
                    draft.getServico(),
                    draft.getServicoSugerido(),
                    draft.getEntradaServicoSugerida(),
                    draft.getDia(),
                    draft.getHorario(),
                    draft.getNome(),
                    draft.isConfirmado()
            );
        }

        public AppointmentDraft toDomain() {
            AppointmentDraft draft = new AppointmentDraft();
            draft.setCommandBase(commandBase);
            draft.setServico(servico);
            draft.setServicoSugerido(servicoSugerido);
            draft.setEntradaServicoSugerida(entradaServicoSugerida);
            draft.setDia(dia);
            draft.setHorario(horario);
            draft.setNome(nome);
            draft.setConfirmado(confirmado);
            return draft;
        }

        // ==================== GETTERS E SETTERS (para Jackson) ====================

        public String getCommandBase() { return commandBase; }
        public void setCommandBase(String commandBase) { this.commandBase = commandBase; }

        public String getServico() { return servico; }
        public void setServico(String servico) { this.servico = servico; }

        public String getServicoSugerido() { return servicoSugerido; }
        public void setServicoSugerido(String servicoSugerido) { this.servicoSugerido = servicoSugerido; }

        public String getEntradaServicoSugerida() { return entradaServicoSugerida; }
        public void setEntradaServicoSugerida(String entradaServicoSugerida) { this.entradaServicoSugerida = entradaServicoSugerida; }

        public String getDia() { return dia; }
        public void setDia(String dia) { this.dia = dia; }

        public String getHorario() { return horario; }
        public void setHorario(String horario) { this.horario = horario; }

        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }

        public boolean isConfirmado() { return confirmado; }
        public void setConfirmado(boolean confirmado) { this.confirmado = confirmado; }
    }
}