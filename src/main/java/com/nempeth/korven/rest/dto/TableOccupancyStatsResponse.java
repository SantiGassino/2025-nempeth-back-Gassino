package com.nempeth.korven.rest.dto;

public record TableOccupancyStatsResponse(
    Integer totalTables,
    Integer freeTables,
    Integer reservedTables,
    Integer occupiedTables,
    Double occupancyRate
) {}
