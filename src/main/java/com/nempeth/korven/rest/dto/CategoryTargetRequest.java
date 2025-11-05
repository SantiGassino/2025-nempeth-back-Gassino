package com.nempeth.korven.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryTargetRequest(
        @NotNull(message = "El ID de la categoría es requerido")
        UUID categoryId,
        
        @NotNull(message = "El objetivo de ingresos es requerido")
        @DecimalMin(value = "0.0", inclusive = false, message = "El objetivo debe ser mayor a 0")
        BigDecimal revenueTarget
) {
}
