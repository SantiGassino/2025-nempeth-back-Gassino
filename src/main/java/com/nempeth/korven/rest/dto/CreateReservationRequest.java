package com.nempeth.korven.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateReservationRequest(
    @NotBlank(message = "El nombre del cliente es obligatorio")
    String customerName,
    
    @NotBlank(message = "El contacto del cliente es obligatorio")
    String customerContact,
    
    @NotBlank(message = "El documento del cliente es obligatorio")
    String customerDocument,
    
    @NotNull(message = "La fecha y hora de inicio son obligatorias")
    OffsetDateTime startDateTime,
    
    @NotNull(message = "La fecha y hora de fin son obligatorias")
    OffsetDateTime endDateTime,
    
    @NotNull(message = "La cantidad de personas es obligatoria")
    @Min(value = 1, message = "Debe haber al menos 1 persona")
    Integer partySize,
    
    @NotNull(message = "Los IDs de las mesas son obligatorios")
    @Size(min = 1, message = "Debe seleccionar al menos una mesa")
    List<UUID> tableIds,
    
    Boolean forced,
    
    String notes
) {}
