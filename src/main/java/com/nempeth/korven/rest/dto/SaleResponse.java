package com.nempeth.korven.rest.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record SaleResponse(
        UUID id,
        String code,
        OffsetDateTime occurredAt,
        BigDecimal totalAmount,
        String createdByUserName,
        SaleTableInfo table,  // null si es orden manual, poblado si es de mesa
        List<SaleItemResponse> items
) {
}