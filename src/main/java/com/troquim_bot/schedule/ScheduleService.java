package com.troquim_bot.schedule;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private final Map<String, ScheduleDay> agenda = new HashMap<>();

    public ScheduleService() {
        inicializarAgendaPadrao();
    }

    private void inicializarAgendaPadrao() {
        String[] dias = {"segunda", "terça", "quarta", "quinta", "sexta", "sábado"};
        LocalTime inicio = LocalTime.of(9, 0);

        for (String dia : dias) {
            ScheduleDay scheduleDay = new ScheduleDay(dia);
            LocalTime horarioAtual = inicio;
            LocalTime fim = horarioFim(dia);

            while (horarioAtual.isBefore(fim)) {
                String horarioFormatado = horarioAtual.toString().substring(0, 5);
                ScheduleSlot slot = new ScheduleSlot(dia, horarioFormatado, SlotStatus.LIVRE);
                scheduleDay.adicionarSlot(slot);
                horarioAtual = horarioAtual.plusHours(1);
            }

            agenda.put(dia, scheduleDay);
        }
    }

    private LocalTime horarioFim(String dia) {
        if ("sabado".equals(normalizar(dia))) {
            return LocalTime.of(13, 0);
        }

        return LocalTime.of(18, 0);
    }

    public List<ScheduleSlot> listarHorarios(String dia) {
        ScheduleDay scheduleDay = agenda.get(dia);
        if (scheduleDay == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(scheduleDay.getSlots());
    }

    public List<ScheduleSlot> listarHorariosDisponiveis(String dia) {
        ScheduleDay scheduleDay = agenda.get(dia);
        if (scheduleDay == null) {
            return Collections.emptyList();
        }
        return scheduleDay.listarHorariosDisponiveis();
    }

    public boolean isHorarioDisponivel(String dia, String horario) {
        ScheduleDay scheduleDay = agenda.get(dia);
        if (scheduleDay == null) {
            return false;
        }

        Optional<ScheduleSlot> slot = scheduleDay.buscarSlot(horario);
        return slot.isPresent() && slot.get().getStatus() == SlotStatus.LIVRE;
    }

    public boolean reservarHorario(String dia, String horario, String numeroCliente) {
        if (!isDiaUtil(dia)) {
            return false;
        }

        ScheduleDay scheduleDay = agenda.get(dia);
        if (scheduleDay == null) {
            return false;
        }

        Optional<ScheduleSlot> slotOpt = scheduleDay.buscarSlot(horario);
        if (slotOpt.isEmpty() || slotOpt.get().getStatus() != SlotStatus.LIVRE) {
            return false;
        }

        ScheduleSlot slot = slotOpt.get();
        slot.setStatus(SlotStatus.RESERVADO);
        slot.setNumeroCliente(numeroCliente);
        return true;
    }

    public boolean cancelarReserva(String dia, String horario) {
        ScheduleDay scheduleDay = agenda.get(dia);
        if (scheduleDay == null) {
            return false;
        }

        Optional<ScheduleSlot> slotOpt = scheduleDay.buscarSlot(horario);
        if (slotOpt.isEmpty() || slotOpt.get().getStatus() != SlotStatus.RESERVADO) {
            return false;
        }

        ScheduleSlot slot = slotOpt.get();
        slot.setStatus(SlotStatus.LIVRE);
        slot.setNumeroCliente(null);
        slot.setObservacao(null);
        return true;
    }

    public boolean bloquearHorario(String dia, String horario, String observacao) {
        if (!isDiaUtil(dia)) {
            return false;
        }

        ScheduleDay scheduleDay = agenda.get(dia);
        if (scheduleDay == null) {
            return false;
        }

        Optional<ScheduleSlot> slotOpt = scheduleDay.buscarSlot(horario);
        if (slotOpt.isEmpty() || slotOpt.get().getStatus() != SlotStatus.LIVRE) {
            return false;
        }

        ScheduleSlot slot = slotOpt.get();
        slot.setStatus(SlotStatus.BLOQUEADO);
        slot.setObservacao(observacao);
        return true;
    }

    public boolean liberarHorario(String dia, String horario) {
        ScheduleDay scheduleDay = agenda.get(dia);
        if (scheduleDay == null) {
            return false;
        }

        Optional<ScheduleSlot> slotOpt = scheduleDay.buscarSlot(horario);
        if (slotOpt.isEmpty() || slotOpt.get().getStatus() != SlotStatus.BLOQUEADO) {
            return false;
        }

        ScheduleSlot slot = slotOpt.get();
        slot.setStatus(SlotStatus.LIVRE);
        slot.setObservacao(null);
        return true;
    }

    private boolean isDiaUtil(String dia) {
        String diaNormalizado = normalizar(dia);
        return !diaNormalizado.equals("domingo");
    }

    private String normalizar(String texto) {
        String semAcentos = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(java.util.Locale.ROOT);
    }
}
