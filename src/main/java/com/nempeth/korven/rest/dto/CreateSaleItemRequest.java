package com.nempeth.korven.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record CreateSaleItemRequest(
        @NotNull(message = "El producto es obligatorio")
        UUID productId,
        
        @NotNull(message = "La cantidad es obligatoria")
        @PositiveOrZero(message = "La cantidad debe ser mayor o igual a 0")
        Integer quantity
) {
}