package com.troquim_bot.application.booking;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.professional.ProfessionalId;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Porta de serialização de operações concorrentes sobre o MESMO slot de agenda.
 *
 * FECHA A LACUNA que {@link BookingIdempotencyStore} não cobre: a idempotência protege o
 * MESMO comando repetido, mas dois comandos DIFERENTES disputando o mesmo
 * (negócio, profissional, dia) ainda fariam "consultar disponibilidade → gravar" como duas
 * operações separadas — uma corrida clássica de leitura-depois-escrita. Esta porta serializa
 * o par inteiro (consulta + escrita) para o mesmo slot, sem tocar em regra de negócio.
 *
 * NÃO CONTÉM regra de disponibilidade nem cria agendamento: só garante que, para a MESMA
 * chave (BusinessId, ProfessionalId, LocalDate), no máximo uma execução do {@code action}
 * roda por vez — em qualquer instância da aplicação.
 */
public interface BookingSlotCriticalSection {

    /**
     * Executa {@code action} dentro da seção crítica do slot. A implementação decide COMO
     * serializar (lock de banco, lock local, etc.); quem chama só recebe a garantia de
     * exclusão mútua para a chave informada.
     */
    <T> T executar(BusinessId businessId, ProfessionalId professionalId, LocalDate date, Supplier<T> action);
}
