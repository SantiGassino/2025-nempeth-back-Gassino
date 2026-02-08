package com.nempeth.korven.constants;

public enum ReservationStatus {
    PENDING,      // Pendiente (reserva activa)
    IN_PROGRESS,  // En curso (cliente sentado)
    COMPLETED,    // Finalizada
    CANCELLED,    // Cancelada
    NO_SHOW       // No se presentó
}
