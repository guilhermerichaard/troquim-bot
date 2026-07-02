package com.troquim_bot.conversation.state;

import java.util.ArrayList;
import java.util.List;

public class ConversationState {

    private final String numero;
    private ConversationStep step;
    private List<AppointmentDraft> drafts;
    private String ultimaPergunta;
    private String nome;

    public ConversationState(String numero) {
        this.numero = numero;
        this.step = ConversationStep.INICIO;
        this.drafts = new ArrayList<>();
    }

    public String getNumero() {
        return numero;
    }

    public ConversationStep getStep() {
        return step;
    }

    public void setStep(ConversationStep step) {
        this.step = step;
    }

    public List<AppointmentDraft> getDrafts() {
        return drafts;
    }

    public void setDrafts(List<AppointmentDraft> drafts) {
        this.drafts = drafts;
    }

    public AppointmentDraft getDraftAtual() {
        if (drafts.isEmpty()) {
            return null;
        }
        return drafts.get(drafts.size() - 1);
    }

    public AppointmentDraft criarNovoDraft() {
        AppointmentDraft novoDraft = new AppointmentDraft();
        drafts.add(novoDraft);
        return novoDraft;
    }

    public List<AppointmentDraft> getDraftsPendentes() {
        List<AppointmentDraft> pendentes = new ArrayList<>();
        for (AppointmentDraft draft : drafts) {
            if (draft.isCompleto() && !draft.isConfirmado()) {
                pendentes.add(draft);
            }
        }
        return pendentes;
    }

    public String getUltimaPergunta() {
        return ultimaPergunta;
    }

    public void setUltimaPergunta(String ultimaPergunta) {
        this.ultimaPergunta = ultimaPergunta;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}
