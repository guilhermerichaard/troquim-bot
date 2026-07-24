package com.troquim_bot.application.booking;

import com.troquim_bot.application.appointment.AppointmentApplicationService;
import com.troquim_bot.application.reservation.ReservationApplicationService;
import com.troquim_bot.appointment.Appointment;
import com.troquim_bot.availability.AvailabilityId;
import com.troquim_bot.availability.HorarioIndisponivelException;
import com.troquim_bot.customer.Customer;
import com.troquim_bot.customer.CustomerId;
import com.troquim_bot.customer.CustomerProfileService;
import com.troquim_bot.professional.ProfessionalId;
import com.troquim_bot.reservation.Reservation;
import com.troquim_bot.service.ServiceId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(BookingApplicationService.class);

    private static final Duration DURACAO_PADRAO = Duration.ofHours(1);

    private static final ProfessionalId PROFISSIONAL_PADRAO =
            ProfessionalId.from(uuidDeterministico("professional:troquim-mvp-default"));

    private final ReservationApplicationService reservationApplicationService;
    private final AppointmentApplicationService appointmentApplicationService;
    private final CustomerProfileService customerProfileService;
    private final BookingIdempotencyStore idempotencyStore;

    @Autowired
    public BookingApplicationService(ReservationApplicationService reservationApplicationService,
                                     AppointmentApplicationService appointmentApplicationService,
                                     CustomerProfileService customerProfileService,
                                     BookingIdempotencyStore idempotencyStore) {
        this.reservationApplicationService = reservationApplicationService;
        this.appointmentApplicationService = appointmentApplicationService;
        this.customerProfileService = customerProfileService;
        this.idempotencyStore = idempotencyStore;
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

        // NAO e' idempotencia: e' um atalho historico deste caminho legado (qualquer
        // agendamento ativo do cliente encerra a confirmacao). A idempotencia real deste
        // canal vem do InboundReceiptProcessor, uma camada acima.
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

        // Sem BookingCommandKey: a idempotencia deste caminho vive uma camada acima, no
        // InboundReceiptProcessor (UNIQUE por provider + external_message_id). Ver secao
        // "Conversation" em docs/features/whatsapp-flow-agendamento.md.
        return reservarEAgendar(customer, PROFISSIONAL_PADRAO, serviceId, availabilityId,
                data, inicio, fim, expiraEm, servico, dia, horario, nomeCliente, null);
    }

    /**
     * Confirma o agendamento para uma DATA de calendario explicita.
     *
     * Existe porque o WhatsApp Flow coleta a data num CalendarPicker (data absoluta),
     * enquanto {@link #confirmar} recebe nome de dia da semana e resolve a proxima
     * ocorrencia. As duas portas de entrada compartilham a MESMA orquestracao e a
     * mesma deteccao de conflito: o Flow nao reimplementa confirmacao.
     *
     * A idempotencia aqui e por SLOT (mesmo cliente, mesma data, mesmo horario) —
     * reconfirmar o mesmo agendamento devolve sucesso sem duplicar, mas um horario
     * diferente segue sendo um agendamento novo.
     *
     * O AvailabilityId e derivado da data ISO (nao do nome do dia), evitando a colisao
     * entre semanas que o caminho por dia da semana tem.
     */
    @Transactional
    public BookingResult confirmarEm(String telefone, String nomeCliente, String servico,
                                     ProfessionalId profissional, LocalDate data, LocalTime inicio,
                                     Duration duracao, BookingCommandKey chave) {
        if (chave == null) {
            throw new IllegalArgumentException(
                    "BookingCommandKey e obrigatoria: sem ela nao ha idempotencia de comando");
        }
        if (data == null || inicio == null || profissional == null) {
            return BookingResult.invalido("Data ou horario ausente.");
        }
        if (data.isBefore(LocalDate.now())) {
            return BookingResult.invalido("Essa data ja passou.");
        }

        // PRIMEIRA escrita da transacao: reivindicar o comando. Antes de qualquer coisa de
        // negocio, para que duas execucoes simultaneas do MESMO comando se serializem aqui
        // e nao no meio da criacao do agendamento.
        BookingIdempotencyStore.Claim claim = idempotencyStore.reivindicar(chave);
        if (!claim.reivindicada()) {
            if (claim.baseJaConfirmada().isPresent()) {
                // Regra do MVP: este Flow ja concluiu um agendamento e agora chegou uma
                // escolha DIFERENTE. Nao e' retry (dados nao batem) nem conflito de agenda
                // (o horario pode estar livre) — a sessao e' que se esgotou. Nada e'
                // escrito e o cliente e' orientado a abrir a agenda de novo.
                log.info("Confirmacao recusada: a base do comando ja concluiu um agendamento.");
                return BookingResult.sessaoJaConfirmada();
            }
            if (claim.existente().isPresent()) {
                // Retry de um comando ja concluido: devolve o desfecho gravado, sem
                // reexecutar negocio e sem procurar agendamentos do cliente.
                return claim.existente().get().comoResultado();
            }
            // Chave tomada, sem desfecho visivel. Nao ha como decidir com seguranca:
            // tratar como falha tecnica deixa o cliente repetir, que e' o caminho seguro.
            log.warn("Comando de booking reivindicado por outra execucao e ainda sem desfecho.");
            return BookingResult.falhaTecnica();
        }

        LocalTime fim = inicio.plus(duracao == null ? DURACAO_PADRAO : duracao);

        Customer customer = customerProfileService.resolverOuConstruir(telefone, nomeCliente);
        ServiceId serviceId = BookingIds.serviceId(servico);
        AvailabilityId availabilityId = AvailabilityId.from(uuidDeterministico(
                "availability:" + profissional + ":" + data + ":" + inicio));
        LocalDateTime expiraEm = LocalDateTime.of(data, inicio);

        // INVARIANTE DE DOMINIO (nao e' idempotencia): um profissional nao pode ter dois
        // agendamentos sobrepostos. Continua valendo para comandos DIFERENTES que disputem
        // o mesmo slot; o retry do MESMO comando ja foi resolvido pela chave acima.
        for (Appointment existente : appointmentApplicationService.listarAtivos()) {
            if (profissional.equals(existente.getProfessionalId())
                    && data.equals(existente.getDate())
                    && inicio.isBefore(existente.getEndTime())
                    && existente.getStartTime().isBefore(fim)) {
                BookingResult conflito = BookingResult.indisponivel("Esse horario ja esta ocupado.");
                // Desfecho negativo tambem e' gravado: repetir o mesmo comando deve dar a
                // mesma resposta, sem varrer a agenda de novo.
                idempotencyStore.concluir(chave, null, conflito.status(),
                        servico, data.toString(), inicio.toString(), nomeCliente);
                return conflito;
            }
        }

        return reservarEAgendar(customer, profissional, serviceId, availabilityId,
                data, inicio, fim, expiraEm, servico, data.toString(), inicio.toString(),
                nomeCliente, chave);
    }

    /**
     * Orquestracao compartilhada Reservation -> Appointment -> Customer, com compensacao.
     * Unico lugar do sistema que materializa um agendamento confirmado.
     */
    private BookingResult reservarEAgendar(Customer customer, ProfessionalId profissional,
                                           ServiceId serviceId, AvailabilityId availabilityId,
                                           LocalDate data, LocalTime inicio, LocalTime fim,
                                           LocalDateTime expiraEm, String servico,
                                           String rotuloDia, String rotuloHorario,
                                           String nomeCliente, BookingCommandKey chave) {
        Reservation reservation;
        try {
            reservation = reservationApplicationService.criarReserva(
                    customer.getId(), profissional, serviceId, availabilityId,
                    data, inicio, fim, expiraEm);
        } catch (HorarioIndisponivelException conflito) {
            // Conflito REAL de agenda. Nada foi escrito: sem compensacao a fazer.
            return concluirConflito(chave, servico, rotuloDia, rotuloHorario, nomeCliente);
        } catch (RuntimeException falha) {
            throw falhaTecnica("criar reserva", falha);
        }

        Appointment appointment;
        try {
            appointment = appointmentApplicationService.criarAgendamentoDeReserva(reservation.getId());
        } catch (HorarioIndisponivelException conflito) {
            compensar(reservation);
            return concluirConflito(chave, servico, rotuloDia, rotuloHorario, nomeCliente);
        } catch (RuntimeException falha) {
            compensar(reservation);
            throw falhaTecnica("criar agendamento", falha);
        }

        // Reserva e agendamento concluidos: persiste o Customer oficial uma unica vez.
        // O mesmo customerId ja foi gravado em Reservation e Appointment.
        //
        // DELIBERADAMENTE SEM try/catch: uma falha aqui precisa PROPAGAR para que o
        // rollback da transacao desfaca Reservation, Appointment E a reivindicacao junto.
        customerProfileService.persistir(customer);

        // Recibo do comando, na MESMA transacao. Commit: agendamento e recibo aparecem
        // juntos. Rollback: somem juntos, e o retry reivindica do zero.
        if (chave != null) {
            idempotencyStore.concluir(chave, appointment.getId(), BookingResult.Status.CONFIRMADO,
                    servico, rotuloDia, rotuloHorario, nomeCliente);
        }

        return BookingResult.confirmado(servico, rotuloDia, rotuloHorario, nomeCliente);
    }

    /** Grava o desfecho negativo para que o retry do MESMO comando responda igual. */
    private BookingResult concluirConflito(BookingCommandKey chave, String servico,
                                           String rotuloDia, String rotuloHorario,
                                           String nomeCliente) {
        BookingResult conflito = BookingResult.indisponivel("Esse horario ja esta ocupado.");
        if (chave != null) {
            idempotencyStore.concluir(chave, null, conflito.status(),
                    servico, rotuloDia, rotuloHorario, nomeCliente);
        }
        return conflito;
    }

    /**
     * Falha tecnica SEMPRE lanca — nunca vira retorno.
     *
     * O motivo e' a idempotencia: a reivindicacao do comando ja foi inserida nesta
     * transacao. Retornar normalmente comitaria uma chave reivindicada e sem desfecho, e
     * todo retry futuro dela ficaria preso em "em andamento". Lancando, o rollback apaga
     * a reivindicacao junto com Reservation/Appointment, e o cliente pode repetir de fato.
     *
     * Quem chama (Flow e conversa) ja traduz RuntimeException em falha tecnica.
     */
    private RuntimeException falhaTecnica(String etapa, RuntimeException causa) {
        log.error("Falha tecnica ao {}: {}", etapa, causa.getClass().getSimpleName());
        return new BookingPersistenceException(
                "Falha de persistencia ao " + etapa + " (" + causa.getClass().getSimpleName() + ")",
                causa);
    }

    /**
     * Compensacao best-effort da reserva orfa.
     *
     * Para repositorios JPA o rollback da transacao ja e' a rede de seguranca real; esta
     * compensacao existe para os repositorios em memoria, onde nao ha rollback. Falhar
     * aqui nao pode mascarar o erro original, entao a excecao e' registrada e engolida.
     */
    private void compensar(Reservation reservation) {
        try {
            reservationApplicationService.cancelarReserva(reservation.getId());
        } catch (RuntimeException e) {
            log.warn("Compensacao da reserva nao pode ser aplicada: {}", e.getClass().getSimpleName());
        }
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
