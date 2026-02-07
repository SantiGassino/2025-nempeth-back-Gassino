package com.nempeth.korven.rest.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * Información simplificada de la mesa asociada a una venta.
 * Solo se incluye cuando la venta está asociada a una mesa.
 */
@Builder
public record SaleTableInfo(
        UUID id,
        String tableCode
) {
}
