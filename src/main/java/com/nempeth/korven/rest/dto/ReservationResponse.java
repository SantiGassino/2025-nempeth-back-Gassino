package com.nempeth.korven.rest.dto;

import com.nempeth.korven.constants.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
    UUID id,
    List<TableSimpleResponse> tables,
    String customerName,
    String customerContact,
    String customerDocument,
    OffsetDateTime startDateTime,
    OffsetDateTime endDateTime,
    Integer partySize,
    ReservationStatus status,
    Boolean forced,
    String createdBy,
    OffsetDateTime createdAt,
    String notes
) {}
