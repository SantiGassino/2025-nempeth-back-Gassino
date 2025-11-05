package com.nempeth.korven.rest.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GoalCategoryTargetResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        BigDecimal revenueTarget,
        BigDecimal actualRevenue,
        BigDecimal achievement
) {
}
