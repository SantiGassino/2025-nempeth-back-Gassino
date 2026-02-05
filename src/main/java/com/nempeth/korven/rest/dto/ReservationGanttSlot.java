package com.nempeth.korven.rest.dto;

import com.nempeth.korven.constants.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Información simplificada de una reserva para mostrar en diagrama de Gantt
 */
public record ReservationGanttSlot(
    UUID reservationId,
    String customerName,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    Integer partySize,
    ReservationStatus status
) {}
