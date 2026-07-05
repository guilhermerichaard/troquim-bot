package com.troquim_bot.schedule;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.repository.InMemoryAppointmentRepository;
import com.troquim_bot.repository.InMemoryReservationRepository;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.service.ServiceId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AppointmentBookingService {

    private static final ProfessionalId DEFAULT_PROFESSIONAL_ID = ProfessionalId.from(
            UUID.fromString("00000000-0000-0000-0000-000000000001")
    );

    private final ScheduleService scheduleService;
    private final AppointmentService appointmentService;
    private final ReservationApplicationService reservationApplicationService;
    private final AppointmentApplicationService appointmentApplicationService;

    @Autowired
    public AppointmentBookingService(ScheduleService scheduleService,
                                     AppointmentService appointmentService,
                                     ReservationApplicationService reservationApplicationService,
                                     AppointmentApplicationService appointmentApplicationService) {
        this.scheduleService = scheduleService;
        this.appointmentService = appointmentService;
        this.reservationApplicationService = reservationApplicationService;
        this.appointmentApplicationService = appointmentApplicationService;
    }

    public AppointmentBookingService(ScheduleService scheduleService, AppointmentService appointmentService) {
        this(scheduleService, appointmentService, new InMemoryReservationRepository(), new InMemoryAppointmentRepository());
    }

    private AppointmentBookingService(ScheduleService scheduleService,
                                      AppointmentService appointmentService,
                                      InMemoryReservationRepository reservationRepository,
                                      InMemoryAppointmentRepository appointmentRepository) {
        this(
                scheduleService,
                appointmentService,
                new ReservationApplicationService(reservationRepository),
                new AppointmentApplicationService(appointmentRepository, reservationRepository)
        );
    }

    public boolean isAvailable(String day, String time) {
        return scheduleService.isHorarioDisponivel(day, time);
    }

    public List<String> listarHorariosDisponiveis(String day) {
        return scheduleService.listarHorariosDisponiveis(day).stream()
                .map(ScheduleSlot::getHorario)
                .toList();
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

        try {
            criarAgendamentoViaReserva(customerNumber, service, day, horarioNormalizado);
        } catch (IllegalArgumentException e) {
            scheduleService.cancelarReserva(day, horarioNormalizado);
            return "Houve um problema ao reservar o horário. Por favor, tente novamente.";
        }

        return "Perfeito! Vou reservar " + service + " na " + day + " às " + time + " para você.";
    }

    private void criarAgendamentoViaReserva(String customerNumber, String service, String day, String normalizedTime) {
        LocalDate date = resolverData(day);
        LocalTime startTime = LocalTime.parse(normalizedTime);
        LocalTime endTime = startTime.plusHours(1);

        Reservation reservation = reservationApplicationService.criarReserva(
                CustomerId.fromPhone(customerNumber),
                DEFAULT_PROFESSIONAL_ID,
                ServiceId.from(stableUuid("service:" + valorSeguro(service))),
                AvailabilityId.from(stableUuid("availability:" + normalizarTexto(day) + ":" + normalizedTime)),
                date,
                startTime,
                endTime,
                LocalDateTime.of(date, endTime).plusMinutes(15)
        );

        appointmentApplicationService.criarAgendamentoDeReserva(reservation.getId());
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

    private LocalDate resolverData(String day) {
        String normalizado = normalizarTexto(day);

        if ("hoje".equals(normalizado)) {
            return LocalDate.now();
        }

        if ("amanha".equals(normalizado)) {
            return LocalDate.now().plusDays(1);
        }

        DayOfWeek dayOfWeek = switch (normalizado) {
            case "segunda" -> DayOfWeek.MONDAY;
            case "terca" -> DayOfWeek.TUESDAY;
            case "quarta" -> DayOfWeek.WEDNESDAY;
            case "quinta" -> DayOfWeek.THURSDAY;
            case "sexta" -> DayOfWeek.FRIDAY;
            case "sabado" -> DayOfWeek.SATURDAY;
            case "domingo" -> DayOfWeek.SUNDAY;
            default -> null;
        };

        if (dayOfWeek != null) {
            return LocalDate.now().with(TemporalAdjusters.nextOrSame(dayOfWeek));
        }

        return LocalDate.parse(day);
    }

    private UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String valorSeguro(String value) {
        return value == null ? "" : value;
    }

    private String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }

        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return semAcentos.toLowerCase(Locale.ROOT).trim();
    }
}
