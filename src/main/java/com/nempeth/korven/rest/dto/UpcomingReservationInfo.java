package com.nempeth.korven.rest.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpcomingReservationInfo(
    UUID reservationId,
    String customerName,
    OffsetDateTime startsAt,
    long minutesUntilStart
) {}
