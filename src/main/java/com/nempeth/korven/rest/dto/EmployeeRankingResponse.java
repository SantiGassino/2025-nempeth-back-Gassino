package com.nempeth.korven.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Response DTO for employee ranking data
 */
public record EmployeeRankingResponse(
        String name,
        String lastName,
        @JsonProperty("sales")
        Long salesCount,
        BigDecimal revenue,
        Integer position,
        Boolean currentUser
) {
}
