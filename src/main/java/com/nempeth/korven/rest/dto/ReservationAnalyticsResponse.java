package com.nempeth.korven.rest.dto;

import java.util.List;

public record ReservationAnalyticsResponse(
        ReservationSummary summary,
        List<TableUtilization> tableUtilization,
        List<ClientReliability> clientReliability,
        List<TimeSlotAnalysis> timeSlotAnalysis,
        List<CapacityWaste> capacityWaste
) {
    public record ReservationSummary(
            long totalReservations,
            long pendingReservations,
            long completedReservations,
            long cancelledReservations,
            long noShowReservations,
            long inProgressReservations,
            double completionRate,
            double noShowRate,
            double cancellationRate
    ) {}

    public record TableUtilization(
            String tableCode,
            long totalReservations,
            long totalHoursReserved,
            long completedReservations,
            long noShows,
            double utilizationRate
    ) {}

    public record ClientReliability(
            String customerName,
            String customerContact,
            long totalReservations,
            long completedReservations,
            long noShows,
            long cancellations,
            double reliabilityScore
    ) {}

    public record TimeSlotAnalysis(
            int hourOfDay,
            long totalReservations,
            long noShows,
            double noShowRate,
            double avgPartySize
    ) {}

    public record CapacityWaste(
            String tableCode,
            int tableCapacity,
            long reservationCount,
            double avgPartySize,
            double wastePercentage
    ) {}
}
