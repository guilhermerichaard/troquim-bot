package com.troquim_bot.application.booking;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.service.ServiceId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Caso de uso de confirmaÃ§Ã£o de agendamento a partir do rascunho da conversa.
 *
 * Orquestra os Application Services jÃ¡ existentes (Customer, Reservation,
 * Appointment) para transformar os dados coletados no menu STRICT_MVP
 * (serviÃ§o, dia, horÃ¡rio, nome) em dados reais persistidos. Toda a regra
 * de negÃ³cio de confirmaÃ§Ã£o vive aqui â€” a camada de conversa apenas invoca
 * este serviÃ§o e traduz o {@link BookingResult} em mensagem.
 *
 * MVP: o salÃ£o tem um Ãºnico profissional. Um {@link ProfessionalId} estÃ¡vel
 * evita duplicar um catÃ¡logo de profissionais e faz o conflito de horÃ¡rio
 * (mesmo profissional + mesma data + horÃ¡rio sobreposto) funcionar de fato.
 */
@Service
public class BookingApplicationService {

    private static final Duration DURACAO_PADRAO = Duration.ofHours(1);

    private static final ProfessionalId PROFISSIONAL_PADRAO =
            ProfessionalId.from(uuidDeterministico("professional:troquim-mvp-default"));

    private final ReservationApplicationService reservationApplicationService;
    private final AppointmentApplicationService appointmentApplicationService;
    private final CustomerProfileService customerProfileService;

    @Autowired
    public BookingApplicationService(ReservationApplicationService reservationApplicationService,
                                     AppointmentApplicationService appointmentApplicationService,
                                     CustomerProfileService customerProfileService) {
        this.reservationApplicationService = reservationApplicationService;
        this.appointmentApplicationService = appointmentApplicationService;
        this.customerProfileService = customerProfileService;
    }

    /**
     * Confirma o agendamento: localiza/cria o Customer, cria a Reservation
     * (que valida disponibilidade via conflito) e cria o Appointment.
     *
     * Em caso de conflito, nenhum dado parcial permanece: a reserva sÃ³ Ã©
     * criada apÃ³s passar na verificaÃ§Ã£o, o Customer sÃ³ Ã© persistido apÃ³s o
     * agendamento concluir, e uma falha ao criar o Appointment cancela a
     * reserva recÃ©m-criada (compensaÃ§Ã£o).
     *
     * Fronteira transacional da Application (ARCHITECTURE_V2_1 Â§C10): Reservation,
     * Appointment e Customer sÃ£o persistidos numa ÃšNICA transaÃ§Ã£o Spring/JPA. Se a
     * persistÃªncia do Customer (ou qualquer escrita) lanÃ§ar RuntimeException nÃ£o
     * tratada, a transaÃ§Ã£o sofre rollback e nenhum Reservation/Appointment Ã³rfÃ£o
     * permanece. A ordem funcional jÃ¡ validada Ã© preservada; o caminho de conflito
     * retorna sem exceÃ§Ã£o (commit sem Customer persistido).
     */
    @Transactional
    public BookingResult confirmar(String telefone, String nomeCliente,
                                   String servico, String dia, String horario) {
        final LocalDate data;
        final LocalTime inicio;
        final LocalTime fim;
        try {
            data = proximaDataPara(dia);
            inicio = parseHorario(horario);
            fim = inicio.plus(DURACAO_PADRAO);
        } catch (RuntimeException e) {
            return BookingResult.invalido("NÃ£o consegui interpretar a data ou o horÃ¡rio informado.");
        }

        // Autoridade Ãºnica de identidade: resolve/cria o Customer UMA vez e usa o
        // CustomerId oficial surrogate. O cliente Ã© persistido sÃ³ no sucesso (persistir),
        // para nÃ£o deixar Customer Ã³rfÃ£o quando o horÃ¡rio estiver ocupado.
        Customer customer = customerProfileService.resolverOuConstruir(telefone, nomeCliente);
        CustomerId customerId = customer.getId();
        ServiceId serviceId = ServiceId.from(uuidDeterministico("service:" + normalizar(servico)));
        AvailabilityId availabilityId = AvailabilityId.from(uuidDeterministico(
                "availability:" + PROFISSIONAL_PADRAO + ":" + normalizar(dia) + ":" + inicio));
        LocalDateTime expiraEm = LocalDateTime.of(data, inicio);

        Reservation reservation;
        // Idempotência Application/Domain: se já existe Appointment confirmado para este
        // cliente neste horário, retorna sucesso sem duplicar entidades.
        if (!appointmentApplicationService.listarAtivosPorCliente(customerId).isEmpty()) {
            return BookingResult.confirmado(servico, dia, horario, nomeCliente);
        }

        // Verifica conflito com Appointments já confirmados (a Reservation cancelada
        // após criar o Appointment não protege mais o slot — o Appointment protege).
        for (Appointment existente : appointmentApplicationService.listarAtivos()) {
            if (PROFISSIONAL_PADRAO.equals(existente.getProfessionalId())
                    && data.equals(existente.getDate())
                    && inicio.isBefore(existente.getEndTime())
                    && existente.getStartTime().isBefore(fim)) {
                return BookingResult.indisponivel("Esse horário já está ocupado.");
            }
        }

        try {
            reservation = reservationApplicationService.criarReserva(
                    customerId, PROFISSIONAL_PADRAO, serviceId, availabilityId,
                    data, inicio, fim, expiraEm);
        } catch (IllegalArgumentException e) {
            return BookingResult.indisponivel("Esse horÃ¡rio jÃ¡ estÃ¡ ocupado.");
        }

        try {
            appointmentApplicationService.criarAgendamentoDeReserva(reservation.getId());
        } catch (RuntimeException e) {
            // CompensaÃ§Ã£o: nÃ£o deixa uma reserva ativa sem o agendamento correspondente.
            reservationApplicationService.cancelarReserva(reservation.getId());
            return BookingResult.indisponivel("Esse horÃ¡rio jÃ¡ estÃ¡ ocupado.");
        }

        // Reserva e agendamento concluÃ­dos: persiste o Customer oficial uma Ãºnica vez.
        // O mesmo customerId jÃ¡ foi gravado em Reservation e Appointment.
        customerProfileService.persistir(customer);

        return BookingResult.confirmado(servico, dia, horario, nomeCliente);
    }

    private LocalDate proximaDataPara(String dia) {
        DayOfWeek alvo = diaDaSemana(dia);
        LocalDate data = LocalDate.now();
        while (data.getDayOfWeek() != alvo) {
            data = data.plusDays(1);
        }
        return data;
    }

    private DayOfWeek diaDaSemana(String dia) {
        return switch (normalizar(dia)) {
            case "segunda" -> DayOfWeek.MONDAY;
            case "terca" -> DayOfWeek.TUESDAY;
            case "quarta" -> DayOfWeek.WEDNESDAY;
            case "quinta" -> DayOfWeek.THURSDAY;
            case "sexta" -> DayOfWeek.FRIDAY;
            case "sabado" -> DayOfWeek.SATURDAY;
            case "domingo" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Dia nÃ£o reconhecido: " + dia);
        };
    }

    private LocalTime parseHorario(String horario) {
        String texto = horario == null ? "" : horario.trim().toLowerCase(Locale.ROOT);
        if (texto.contains(":")) {
            String[] partes = texto.split(":");
            return LocalTime.of(Integer.parseInt(partes[0].trim()), Integer.parseInt(partes[1].trim()));
        }
        if (texto.endsWith("h")) {
            texto = texto.substring(0, texto.length() - 1).trim();
        }
        return LocalTime.of(Integer.parseInt(texto), 0);
    }

    private static String normalizar(String texto) {
        String base = texto == null ? "" : texto;
        String semAcentos = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.toLowerCase(Locale.ROOT).trim();
    }

    private static UUID uuidDeterministico(String chave) {
        return UUID.nameUUIDFromBytes(chave.getBytes(StandardCharsets.UTF_8));
    }
}
