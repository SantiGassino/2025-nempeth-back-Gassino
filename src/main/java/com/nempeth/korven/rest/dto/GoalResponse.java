package com.nempeth.korven.rest.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        UUID businessId,
        String name,
        LocalDate periodStart,
        LocalDate periodEnd,
        Boolean isLocked,
        Boolean isPeriodFinished,
        List<GoalCategoryTargetResponse> categoryTargets
) {
}
