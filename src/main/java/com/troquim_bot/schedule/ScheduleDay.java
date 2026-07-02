package com.troquim_bot.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScheduleDay {
    private final String dia;
    private final List<ScheduleSlot> slots;

    public ScheduleDay(String dia) {
        this.dia = dia;
        this.slots = new ArrayList<>();
    }

    public String getDia() {
        return dia;
    }

    public List<ScheduleSlot> getSlots() {
        return slots;
    }

    public void adicionarSlot(ScheduleSlot slot) {
        this.slots.add(slot);
    }

    public Optional<ScheduleSlot> buscarSlot(String horario) {
        return slots.stream()
                .filter(slot -> slot.getHorario().equals(horario))
                .findFirst();
    }

    public List<ScheduleSlot> listarHorariosDisponiveis() {
        return slots.stream()
                .filter(slot -> slot.getStatus() == SlotStatus.LIVRE)
                .toList();
    }
}