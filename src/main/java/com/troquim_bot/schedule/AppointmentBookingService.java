package com.troquim_bot.schedule;

import org.springframework.stereotype.Service;

@Service
public class AppointmentBookingService {

    private final ScheduleService scheduleService;
    private final AppointmentService appointmentService;

    public AppointmentBookingService(ScheduleService scheduleService, AppointmentService appointmentService) {
        this.scheduleService = scheduleService;
        this.appointmentService = appointmentService;
    }

    public boolean isAvailable(String day, String time) {
        return scheduleService.isHorarioDisponivel(day, time);
    }

    public String bookIfAvailable(String customerNumber, String customerName, String service, String day, String time) {
        String horarioNormalizado = normalizarHorario(time);
        
        if (!scheduleService.isHorarioDisponivel(day, horarioNormalizado)) {
            return "Desculpe, o horário " + day + " às " + time + " não está mais disponível. Gostaria de escolher outro horário?";
        }

        boolean reservado = scheduleService.reservarHorario(day, horarioNormalizado, customerNumber);
        if (!reservado) {
            return "Houve um problema ao reservar o horário. Por favor, tente novamente.";
        }

        appointmentService.criarAgendamento(customerNumber, customerName, service, day, time);

        return "Perfeito! Vou reservar " + service + " na " + day + " às " + time + " para você.";
    }
    
    private String normalizarHorario(String horario) {
        if (horario == null || horario.isBlank()) {
            return horario;
        }
        
        String normalizado = horario.toLowerCase().trim();
        
        // Remove 'hs' ou 'h' do final (verificar 'hs' primeiro pois é mais específico)
        if (normalizado.endsWith("hs")) {
            normalizado = normalizado.substring(0, normalizado.length() - 2);
        } else if (normalizado.endsWith("h")) {
            normalizado = normalizado.substring(0, normalizado.length() - 1);
        }
        
        // Se não tem minutos, adiciona :00
        if (!normalizado.contains(":")) {
            normalizado = normalizado + ":00";
        }
        
        // Garante formato HH:MM (com zero à esquerda)
        if (normalizado.contains(":")) {
            String[] partes = normalizado.split(":");
            if (partes.length == 2) {
                try {
                    int hora = Integer.parseInt(partes[0]);
                    normalizado = String.format("%02d:%s", hora, partes[1]);
                } catch (NumberFormatException e) {
                    // Se não conseguir parsear, retorna o original
                    return horario;
                }
            }
        }
        
        return normalizado;
    }
}