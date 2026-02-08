package com.nempeth.korven.rest.dto;

import java.util.List;
import java.util.UUID;

/**
 * Información de una mesa y todas sus reservas para un rango de fechas.
 * Diseñado para alimentar un diagrama de Gantt.
 */
public record TableGanttResponse(
    UUID tableId,
    String tableCode,
    Integer capacity,
    List<ReservationGanttSlot> reservations
) {}
