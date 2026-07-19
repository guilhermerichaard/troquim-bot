package com.troquim_bot.application.booking;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
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
 * Caso de uso de confirmação de agendamento a partir do rascunho da conversa.
 *
 * Orquestra os Application Services já existentes (Customer, Reservation,
 * Appointment) para transformar os dados coletados no menu STRICT_MVP
 * (serviço, dia, horário, nome) em dados reais persistidos. Toda a regra
 * de negócio de confirmação vive aqui — a camada de conversa apenas invoca
 * este serviço e traduz o {@link BookingResult} em mensagem.
 *
 * MVP: o salão tem um único profissional. Um {@link ProfessionalId} estável
 * evita duplicar um catálogo de profissionais e faz o conflito de horário
 * (mesmo profissional + mesma data + horário sobreposto) funcionar de fato.
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
     * Em caso de conflito, nenhum dado parcial permanece: a reserva só é
     * criada após passar na verificação, o Customer só é persistido após o
     * agendamento concluir, e uma falha ao criar o Appointment cancela a
     * reserva recém-criada (compensação).
     *
     * Fronteira transacional da Application (ARCHITECTURE_V2_1 §C10): Reservation,
     * Appointment e Customer são persistidos numa ÚNICA transação Spring/JPA. Se a
     * persistência do Customer (ou qualquer escrita) lançar RuntimeException não
     * tratada, a transação sofre rollback e nenhum Reservation/Appointment órfão
     * permanece. A ordem funcional já validada é preservada; o caminho de conflito
     * retorna sem exceção (commit sem Customer persistido).
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
            return BookingResult.invalido("Não consegui interpretar a data ou o horário informado.");
        }

        // Autoridade única de identidade: resolve/cria o Customer UMA vez e usa o
        // CustomerId oficial surrogate. O cliente é persistido só no sucesso (persistir),
        // para não deixar Customer órfão quando o horário estiver ocupado.
        Customer customer = customerProfileService.resolverOuConstruir(telefone, nomeCliente);
        CustomerId customerId = customer.getId();
        ServiceId serviceId = ServiceId.from(uuidDeterministico("service:" + normalizar(servico)));
        AvailabilityId availabilityId = AvailabilityId.from(uuidDeterministico(
                "availability:" + PROFISSIONAL_PADRAO + ":" + normalizar(dia) + ":" + inicio));
        LocalDateTime expiraEm = LocalDateTime.of(data, inicio);

        Reservation reservation;
        try {
            reservation = reservationApplicationService.criarReserva(
                    customerId, PROFISSIONAL_PADRAO, serviceId, availabilityId,
                    data, inicio, fim, expiraEm);
        } catch (IllegalArgumentException e) {
            return BookingResult.indisponivel("Esse horário já está ocupado.");
        }

        try {
            appointmentApplicationService.criarAgendamento(
                    customerId, PROFISSIONAL_PADRAO, serviceId, availabilityId,
                    data, inicio, fim);
        } catch (RuntimeException e) {
            // Compensação: não deixa uma reserva ativa sem o agendamento correspondente.
            reservationApplicationService.cancelarReserva(reservation.getId());
            return BookingResult.indisponivel("Esse horário já está ocupado.");
        }

        // Reserva e agendamento concluídos: persiste o Customer oficial uma única vez.
        // O mesmo customerId já foi gravado em Reservation e Appointment.
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
            default -> throw new IllegalArgumentException("Dia não reconhecido: " + dia);
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
