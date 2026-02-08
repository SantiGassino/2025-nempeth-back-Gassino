package com.nempeth.korven.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateTableCapacityRequest(
    @NotNull(message = "La capacidad es obligatoria")
    @Min(value = 1, message = "La capacidad debe ser al menos 1")
    @Max(value = 100, message = "La capacidad no puede superar 100 personas")
    Integer capacity
) {}
