package com.nempeth.korven.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for business ranking data
 * Contains only the composite score and essential identification data
 */
public record BusinessRankingResponse(
        UUID businessId,
        String businessName,
        @JsonProperty("score")
        BigDecimal compositeScore,
        Integer position,
        Boolean isOwnBusiness
) {
}
