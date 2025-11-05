package com.nempeth.korven.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateGoalRequest(
        @NotBlank(message = "El nombre es requerido")
        String name,
        
        @NotNull(message = "La fecha de inicio es requerida")
        LocalDate periodStart,
        
        @NotNull(message = "La fecha de fin es requerida")
        LocalDate periodEnd,
        
        BigDecimal totalRevenueGoal,
        
        @NotEmpty(message = "Debe especificar al menos un objetivo de categoría")
        @Valid
        List<CategoryTargetRequest> categoryTargets
) {
}
