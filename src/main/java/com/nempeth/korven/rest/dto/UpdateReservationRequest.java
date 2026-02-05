package com.nempeth.korven.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UpdateReservationRequest(
    String customerName,
    String customerContact,
    Integer partySize,
    @Size(min = 1, message = "Debe seleccionar al menos una mesa")
    List<UUID> tableIds,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    String notes
) {}
