package com.troquim_bot.schedule;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppointmentService {

    private final ConcurrentHashMap<String, List<Appointment>> appointmentsByPhone = new ConcurrentHashMap<>();

    public Appointment criarAgendamento(String numeroCliente,
                                        String nomeCliente,
                                        String servico,
                                        String dia,
                                        String horario) {
        LocalDateTime agora = LocalDateTime.now();
        Appointment appointment = new Appointment(
                UUID.randomUUID(),
                numeroCliente,
                nomeCliente,
                servico,
                dia,
                horario,
                AppointmentStatus.PENDENTE,
                agora,
                agora
        );

        List<Appointment> appointments = appointmentsByPhone.computeIfAbsent(
                chave(numeroCliente),
                ignored -> Collections.synchronizedList(new ArrayList<>())
        );
        appointments.add(appointment);

        return appointment;
    }

    public Optional<Appointment> buscarUltimoAgendamentoPorTelefone(String numeroCliente) {
        List<Appointment> appointments = appointmentsByPhone.get(chave(numeroCliente));
        if (appointments == null) {
            return Optional.empty();
        }

        synchronized (appointments) {
            if (appointments.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(appointments.get(appointments.size() - 1));
        }
    }

    public List<Appointment> listarAgendamentosDoCliente(String numeroCliente) {
        List<Appointment> appointments = appointmentsByPhone.get(chave(numeroCliente));
        if (appointments == null) {
            return List.of();
        }

        synchronized (appointments) {
            return List.copyOf(appointments);
        }
    }

    public Optional<Appointment> atualizarStatus(UUID id, AppointmentStatus status) {
        if (id == null || status == null) {
            return Optional.empty();
        }

        for (List<Appointment> appointments : appointmentsByPhone.values()) {
            synchronized (appointments) {
                for (Appointment appointment : appointments) {
                    if (id.equals(appointment.getId())) {
                        appointment.atualizarStatus(status);
                        return Optional.of(appointment);
                    }
                }
            }
        }

        return Optional.empty();
    }

    public Optional<Appointment> cancelarAgendamento(UUID id) {
        return atualizarStatus(id, AppointmentStatus.CANCELADO);
    }

    private String chave(String numeroCliente) {
        return numeroCliente == null ? "" : numeroCliente;
    }
}
