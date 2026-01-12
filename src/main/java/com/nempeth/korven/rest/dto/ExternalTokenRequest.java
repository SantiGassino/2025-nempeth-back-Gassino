package com.nempeth.korven.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalTokenRequest {
    
    @NotBlank(message = "El nombre del cliente es requerido")
    private String clientName;
}
