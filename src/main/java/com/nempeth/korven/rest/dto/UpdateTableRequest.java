package com.nempeth.korven.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateTableRequest(
    String tableCode,
    @Min(value = 1, message = "La capacidad debe ser al menos 1")
    @Max(value = 100, message = "La capacidad no puede superar 100 personas")
    Integer capacity,
    String sector
) {}
