package com.nempeth.korven.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ActiveGoalSummaryResponse(
        UUID id,
        String name,
        LocalDate periodStart,
        LocalDate periodEnd,
        String daysRemaining,
        Integer categoriesCompleted,
        Integer categoriesTotal,
        String completionStatus,
        BigDecimal totalTarget,
        BigDecimal totalActual
) {
}
