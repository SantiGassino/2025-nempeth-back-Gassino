package com.nempeth.korven.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoalReportResponse(
        UUID id,
        String name,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalRevenueGoal,
        BigDecimal totalActualRevenue,
        BigDecimal totalAchievement,
        List<GoalCategoryTargetResponse> categoryTargets
) {
}
