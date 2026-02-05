package com.nempeth.korven.rest.dto;

import com.nempeth.korven.constants.TableStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTableStatusRequest(
    @NotNull(message = "El estado es obligatorio")
    TableStatus status
) {}
