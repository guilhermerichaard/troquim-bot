package com.troquim_bot.application.waitlist;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Porta para aviso proativo. O canal decide como respeitar suas próprias políticas
 * (no WhatsApp, fora da janela de atendimento, isso significa template aprovado).
 */
public interface WaitlistNotificationGateway {

    boolean notifySlotAvailable(java.util.UUID waitlistId,
                                String phoneE164,
                                String serviceName,
                                LocalDate date,
                                LocalTime time);
}
